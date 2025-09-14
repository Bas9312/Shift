package bas.app.shift.ui.terminal

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import com.google.android.material.appbar.MaterialToolbar
import bas.app.shift.R
import bas.app.shift.databinding.ActivityTerminalBinding
import bas.app.shift.helpers.LogHelper
import bas.app.shift.helpers.NoiseManager
import bas.app.shift.helpers.TerminalCommandManager
import bas.app.shift.helpers.TerminalHistoryHelper
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.helpers.WikipediaHelper
import bas.app.shift.models.TerminalCommand
import bas.app.shift.models.TerminalHistory

class TerminalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTerminalBinding

    private lateinit var adapter: ConsoleAdapter
    private var isUpgradeSessionActive = false  // Отслеживаем активную сессию UPGRADE
    private var isRebootSessionActive = false   // Отслеживаем активную сессию REBOOT
    private val levelViews by lazy {
        listOf<View>(
            findViewById(R.id.lvl1), findViewById(R.id.lvl2), findViewById(R.id.lvl3),
            findViewById(R.id.lvl4), findViewById(R.id.lvl5), findViewById(R.id.lvl6),
            findViewById(R.id.lvl7)
        )
    }

    private val colors = listOf(
        R.color.noise1, R.color.noise2, R.color.noise3,
        R.color.noise4, R.color.noise5, R.color.noise6, R.color.noise7
    )

    private var noise = 0
    private var terminalHistory = TerminalHistory()
    private lateinit var noiseManager: NoiseManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTerminalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initAutocomplete()
        // TerminalActivity уже имеет title в layout, поэтому не нужно устанавливать его программно

        adapter = ConsoleAdapter(mutableListOf())
        binding.consoleList.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.consoleList.adapter = adapter
        
        // Устанавливаем callback для плавного скроллинга во время печати
        adapter.setOnScrollCallback {
            smoothScrollToBottomIfNeeded()
        }

        binding.btnSend.setOnClickListener {
            val cmd = binding.prompt.editCommand.text.toString().trim()
            if (cmd.isNotEmpty()) {
                addLine(cmd, Line.Type.CMD)
                binding.prompt.editCommand.text.clear()
                sendToServer(cmd)
            }
        }

        updateNoise(0)          // старт
        
        // Загружаем историю терминала
        loadTerminalHistory()
        
        // Инициализируем NoiseManager
        initNoiseManager()

        binding.topBar.setNavigationOnClickListener {
            finish()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::noiseManager.isInitialized) {
            noiseManager.cleanup()
        }
    }

    private fun addLine(text: String, type: Line.Type) {
        if (type == Line.Type.RSP) {
            // Для ответов используем постепенную печать
            adapter.addTyping(text)
        } else {
            adapter.add(Line(text, type))
        }
        // плавный автоскролл вниз
        smoothScrollToBottom()
    }
    
    private fun addLineImmediate(text: String, type: Line.Type) {
        // Для истории и других случаев - показываем сразу
        adapter.add(Line(text, type))
        smoothScrollToBottom()
    }
    
    private fun smoothScrollToBottom() {
        val itemCount = adapter.itemCount
        if (itemCount > 0) {
            // Используем post с небольшой задержкой для гарантированного выполнения
            binding.consoleList.post {
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.consoleList.smoothScrollToPosition(itemCount - 1)
                }, 10)
            }
        }
    }
    
    private fun smoothScrollToBottomIfNeeded() {
        val layoutManager = binding.consoleList.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
        if (layoutManager != null) {
            val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
            val totalItemCount = adapter.itemCount
            
            // Скроллим только если последний элемент не виден полностью
            if (lastVisiblePosition < totalItemCount - 1) {
                binding.consoleList.post {
                    binding.consoleList.scrollToPosition(totalItemCount - 1)
                }
            }
        }
    }

    /** обработка команд терминала */
    private fun sendToServer(cmd: String) {
        // Сохраняем команду в историю
        saveCommandToHistory(cmd)
        
        val availableModules = getAvailableModules()
        val command = TerminalCommandManager.findCommand(cmd, availableModules)
        
        Handler(Looper.getMainLooper()).postDelayed({
            if (command != null) {
                // Обрабатываем найденную команду
                processCommand(command, cmd)
            } else {
                // Неизвестная команда
                val errorMsg = "ОШИБКА: Неизвестная команда '$cmd'"
                val helpMsg = "Введите HELP для списка доступных команд"
                
                adapter.addTyping(errorMsg)
                adapter.addTyping(helpMsg)
                
                // Сохраняем ответы в историю
                saveResponseToHistory(errorMsg)
                saveResponseToHistory(helpMsg)
                
                smoothScrollToBottom()
            }
        }, 300)
    }
    
    private fun processCommand(command: TerminalCommand, fullCommand: String) {
        when (command.name) {
            "HELP" -> {
                val helpText = TerminalCommandManager.getHelpText(getAvailableModules())
                addLine(helpText, Line.Type.RSP)
                saveResponseToHistory(helpText)
            }
            "USER.REBOOT.START" -> {
                handleRebootStartCommand()
            }
            "USER.REBOOT.END" -> {
                handleRebootEndCommand()
            }
            "USER.UPGRADE.START" -> {
                handleUpgradeStartCommand()
            }
            "USER.UPGRADE.END" -> {
                handleUpgradeEndCommand(fullCommand)
            }
            else -> {
                // Обычная команда
                val executingMsg = "Выполняю: $fullCommand"
                val processMsg = "Команда в процессе выполнения..."
                
                adapter.addTyping(executingMsg)
                adapter.addTyping(processMsg)
                
                // Сохраняем ответы в историю
                saveResponseToHistory(executingMsg)
                saveResponseToHistory(processMsg)
                
                // Отправляем команду на сервер для изменения шума
                if (command.noiseIncrease != 0) {
                    noiseManager.adjustNoise(command.noiseIncrease, "Terminal command: $fullCommand")
                }
            }
        }
        
        smoothScrollToBottom()
    }

    private fun incNoise(delta: Int) {
        noise = (noise + delta).coerceIn(0, 7)
        updateNoise(noise)
        if (noise == 5) {
            showGlitchEvent()
        }
    }

    private fun showGlitchEvent() {
        Toast.makeText(this, "Глюк-атака! Шум 5", Toast.LENGTH_SHORT).show()
    }

    fun updateNoise(n: Int) {
        levelViews.forEachIndexed { idx, v ->
            v.setBackgroundColor(
                ContextCompat.getColor(
                    this,
                    if (idx < n) colors[idx] else R.color.noiseOff
                )
            )
        }

        when (n) {
            0, 1 -> binding.noiseOverlay.visibility = View.GONE
            2, 3 -> showNoise(n)
            4 -> {
                showNoise(n); applyGlitch(n)
            }

            5 -> {
                showNoise(n); applyGlitch(n); vibrator()
            }

            6 -> showRedScrim()
            7 -> demonJumpScare()
        }
    }

    private fun initAutocomplete() {
        val cursorAnim = ValueAnimator.ofFloat(0f, 8f).apply {
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            duration = 600
            addUpdateListener {
                binding.prompt.editCommand.setPaddingRelative(
                    binding.prompt.editCommand.paddingStart,
                    binding.prompt.editCommand.paddingTop,
                    (it.animatedValue as Float).toInt(),          // курсор-паддинг
                    binding.prompt.editCommand.paddingBottom
                )
            }
        }
        cursorAnim.start()

        // Получаем доступные модули пользователя (пока заглушка)
        val availableModules = getAvailableModules()
        
        // Создаем кастомный адаптер для автодополнения
        val autoAdapter = CommandAutocompleteAdapter(this, availableModules)
        binding.prompt.editCommand.setAdapter(autoAdapter)
        
        // Обработчик выбора команды - вставляем только название без параметров
        binding.prompt.editCommand.setOnItemClickListener { _, _, position, _ ->
            val command = autoAdapter.getCommandAt(position)
            if (command != null) {
                binding.prompt.editCommand.setText(command.name)
                binding.prompt.editCommand.setSelection(command.name.length)
            }
        }

        /* красивый фон у выпадашки */
        binding.prompt.editCommand.setDropDownBackgroundResource(
            R.drawable.bg_dropdown_dark
        )


    }
    
    private fun getAvailableModules(): List<Int> {
        // Получаем реальные модули пользователя из профиля
        val userData = UserPrefsHelper.getUserData(this)
        return userData?.modules?.map { it.id } ?: emptyList()
    }
    
    private fun loadTerminalHistory() {
        terminalHistory = TerminalHistoryHelper.loadHistory(this)
        
        // Восстанавливаем историю команд и ответов сразу, без печати
        terminalHistory.commands.forEach { command ->
            addLineImmediate(command, Line.Type.CMD)
        }
        
        terminalHistory.responses.forEach { response ->
            addLineImmediate(response, Line.Type.RSP)
        }
    }
    
    private fun saveCommandToHistory(command: String) {
        TerminalHistoryHelper.addCommandToHistory(this, command)
    }
    
    private fun saveResponseToHistory(response: String) {
        TerminalHistoryHelper.addResponseToHistory(this, response)
    }
    
    private fun initNoiseManager() {
        val userId = UserPrefsHelper.getUserId(this)
        if (userId.isNotEmpty()) {
            noiseManager = NoiseManager(this)
            noiseManager.setUserId(userId)
            noiseManager.setOnNoiseUpdateListener { newNoise ->
                noise = newNoise
                updateNoise(noise)
            }
            noiseManager.setOnCommandSuccessListener {
                // Вызываем sendToMg (пока пустой)
                sendToMg()
            }
            
            // Запускаем периодическое обновление шума
            noiseManager.startPeriodicNoiseUpdate()
            
            // Получаем текущий шум сразу
            noiseManager.fetchCurrentNoise()
        } else {
            LogHelper.e("TerminalActivity: UserId is empty, cannot initialize NoiseManager")
        }
    }
    
    private fun sendToMg() {
        // TODO: Реализовать отправку MG
        LogHelper.d("TerminalActivity: sendToMg called")
    }
    
    private fun handleUpgradeStartCommand() {
        val executingMsg = "Выполняю: USER.UPGRADE.START"
        adapter.addTyping(executingMsg)
        saveResponseToHistory(executingMsg)
        
        // Проверяем кулдаун
        if (!WikipediaHelper.canUseUpgrade(this)) {
            val timeUntilNext = WikipediaHelper.getTimeUntilNextUpgrade(this)
            val hoursLeft = timeUntilNext / (60 * 60 * 1000)
            val minutesLeft = (timeUntilNext % (60 * 60 * 1000)) / (60 * 1000)
            
            val cooldownMsg = "Команда недоступна. Следующее использование через: ${hoursLeft}ч ${minutesLeft}м"
            adapter.addTyping(cooldownMsg)
            saveResponseToHistory(cooldownMsg)
            smoothScrollToBottom()
            return
        }
        
        val processMsg = "Команда в процессе выполнения..."
        adapter.addTyping(processMsg)
        saveResponseToHistory(processMsg)
        
        // Получаем случайные страницы из Wikipedia
        WikipediaHelper.getRandomPages(
            onSuccess = { startPage, finishPage ->
                val upgradeText = """
                    «Шесть кликов» — вики-серфинг для мозгов
                    
                    Вы открываете одну страницу Википедии (стартовую), и знаете статью которая должна получиться в итоге (конечная). У вас есть максимум шесть переходов по ссылкам, чтобы добраться от стартовой статьи до итоговой.
                    
                    СТАРТОВАЯ СТРАНИЦА:
                    Название: ${startPage.title}
                    Ссылка: ${startPage.fullUrl}
                    
                    ЦЕЛЕВАЯ СТРАНИЦА:
                    Название: ${finishPage.title}
                    Ссылка: ${finishPage.fullUrl}
                    
                    Время на попытку не ограничено. 
                    
                    Для завершения задачи используйте команду:
                    USER.UPGRADE.END <название_статьи_1> <название_статьи_2> ... <название_статьи_N>
                    
                    При успехе - уровень шума снижается на 2 уровня.
                """.trimIndent()
                
                adapter.addTyping(upgradeText)
                saveResponseToHistory(upgradeText)
                
                // Отмечаем использование команды
                WikipediaHelper.markUpgradeUsed(this)
                
                // Активируем сессию UPGRADE
                isUpgradeSessionActive = true
            },
            onError = { error ->
                val errorMsg = "Ошибка получения страниц Wikipedia: $error"
                adapter.addTyping(errorMsg)
                saveResponseToHistory(errorMsg)
            }
        )
        
        smoothScrollToBottom()
    }

    private fun showNoise(level: Int) {
        if (binding.noiseOverlay.visibility != View.VISIBLE) binding.noiseOverlay.visibility = View.VISIBLE
        binding.noiseOverlay.alpha = 0.08f * level
        // ImageView с @drawable/noise
        val anim = ValueAnimator.ofFloat(0f, 16f, -16f, 0f).apply {
            duration = 4000                    // 4 сек / цикл
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val shift = it.animatedValue as Float
                binding.noiseOverlay .translationX = shift
                binding.noiseOverlay .translationY = -shift / 2     // диагональный дрейф
            }
        }
        anim.start()
    }

    fun applyGlitch(level: Int) {
        // shake once
        ObjectAnimator.ofFloat(binding.root, "translationX", 0f, 8f, -8f, 0f).apply {
            duration = 200
            start()
        }

        // purple tint matrix
        val matrix = ColorMatrix().apply {
            setScale(1f, 1f - 0.1f * level, 1f, 1f)
        }
        binding.root.foreground = ColorDrawable(Color.TRANSPARENT).also {
            it.colorFilter = ColorMatrixColorFilter(matrix)
        }
    }

    private val redScrim by lazy {
        View(this).apply {
            setBackgroundColor(0x55e74c3c)
            alpha = 0f
            binding.root.addView(
                this,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    fun showRedScrim() {
        redScrim.animate().alpha(0.5f).setDuration(150).start()
    }

    fun demonJumpScare() {
        val demon = ImageView(this).apply {
            setImageResource(R.drawable.demon_silhouette)
            scaleX = 1.1f; scaleY = 1.1f
            alpha = 0f
        }
        binding.root.addView(
            demon,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        demon.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(600).withEndAction {
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.root.removeView(demon)
                }, 1500)
            }.start()
    }

    private fun vibrator() {
        val vib: Vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vib.hasVibrator()) {
            val effect = VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
            vib.vibrate(effect)
        }
    }
    
    private fun handleUpgradeEndCommand(fullCommand: String) {
        val executingMsg = "Выполняю: USER.UPGRADE.END"
        adapter.addTyping(executingMsg)
        saveResponseToHistory(executingMsg)
        
        // Проверяем активную сессию UPGRADE
        if (!isUpgradeSessionActive) {
            val errorMsg = "Ошибка: Нет активной сессии вики-серфинга. Сначала выполните USER.UPGRADE.START"
            adapter.addTyping(errorMsg)
            saveResponseToHistory(errorMsg)
            smoothScrollToBottom()
            return
        }
        
        // Парсим аргументы команды
        val parts = fullCommand.split(" ")
        if (parts.size < 2) {
            val errorMsg = "Ошибка: Необходимо указать названия статей. Формат: USER.UPGRADE.END <статья1> <статья2> ..."
            adapter.addTyping(errorMsg)
            saveResponseToHistory(errorMsg)
            smoothScrollToBottom()
            return
        }
        
        val articles = parts.drop(1) // Убираем "USER.UPGRADE.END"
        
        // Проверяем количество статей (максимум 6)
        if (articles.size > 6) {
            val errorMsg = "Ошибка: Максимум 6 статей в пути. Указано: ${articles.size}"
            adapter.addTyping(errorMsg)
            saveResponseToHistory(errorMsg)
            smoothScrollToBottom()
            return
        }
        
        val successMsg = """
            Поздравляем! Вы успешно прошли путь из ${articles.size} статей:
            ${articles.joinToString(" → ")}
            
            Уровень шума снижен на 2 уровня.
        """.trimIndent()
        
        adapter.addTyping(successMsg)
        saveResponseToHistory(successMsg)
        
        // Снижаем шум
        noiseManager.adjustNoise(-2, "USER.UPGRADE.END - Wikipedia surfing completed")
        
        // Завершаем сессию UPGRADE
        isUpgradeSessionActive = false
        
        smoothScrollToBottom()
    }
    
    private fun handleRebootStartCommand() {
        val executingMsg = "Выполняю: USER.REBOOT.START"
        adapter.addTyping(executingMsg)
        saveResponseToHistory(executingMsg)
        
        // Проверяем кулдаун (1 час)
        val prefs = getSharedPreferences("terminal_prefs", MODE_PRIVATE)
        val lastRebootTime = prefs.getLong("last_reboot_time", 0)
        val currentTime = System.currentTimeMillis()
        val oneHour = 60 * 60 * 1000L
        
        if (currentTime - lastRebootTime < oneHour) {
            val timeLeft = oneHour - (currentTime - lastRebootTime)
            val hoursLeft = timeLeft / (60 * 60 * 1000)
            val minutesLeft = (timeLeft % (60 * 60 * 1000)) / (60 * 1000)
            
            val cooldownMsg = "Команда недоступна. Следующее использование через: ${hoursLeft}ч ${minutesLeft}м"
            adapter.addTyping(cooldownMsg)
            saveResponseToHistory(cooldownMsg)
            smoothScrollToBottom()
            return
        }
        
        val rebootText = """
            === ПЕРЕЗАГРУЗКА СИСТЕМЫ ===
            
            Инициирую осознанный цифровой отдых...
            
            Система переходит в режим глубокого восстановления.
            Все активные процессы приостановлены.
            Память очищается от временных данных.
            
            Для завершения перезагрузки используйте команду:
            USER.REBOOT.END
            
            Время на восстановление должно составлять минимум 5 минут.
            При успешном завершении - уровень шума снижается на 1 уровень.
        """.trimIndent()
        
        adapter.addTyping(rebootText)
        saveResponseToHistory(rebootText)
        
        // Активируем сессию REBOOT
        isRebootSessionActive = true
        
        // Сохраняем время использования
        prefs.edit().putLong("last_reboot_time", currentTime).apply()
        
        smoothScrollToBottom()
    }
    
    private fun handleRebootEndCommand() {
        val executingMsg = "Выполняю: USER.REBOOT.END"
        adapter.addTyping(executingMsg)
        saveResponseToHistory(executingMsg)
        
        // Проверяем активную сессию REBOOT
        if (!isRebootSessionActive) {
            val errorMsg = "Ошибка: Нет активной сессии перезагрузки. Сначала выполните USER.REBOOT.START"
            adapter.addTyping(errorMsg)
            saveResponseToHistory(errorMsg)
            smoothScrollToBottom()
            return
        }
        
        val successMsg = """
            === ПЕРЕЗАГРУЗКА ЗАВЕРШЕНА ===
            
            Система успешно восстановлена.
            Все процессы возобновлены.
            Память оптимизирована.
            
            Уровень шума снижен на 1 уровень.
            Готов к работе.
        """.trimIndent()
        
        adapter.addTyping(successMsg)
        saveResponseToHistory(successMsg)
        
        // Снижаем шум
        noiseManager.adjustNoise(-1, "USER.REBOOT.END - System reboot completed")
        
        // Завершаем сессию REBOOT
        isRebootSessionActive = false
        
        smoothScrollToBottom()
    }


}
