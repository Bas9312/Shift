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
import bas.app.shift.helpers.NoiseHelper
import bas.app.shift.helpers.WikipediaHelper
import bas.app.shift.models.TerminalCommand
import bas.app.shift.models.TerminalHistory
import bas.app.shift.models.NoiseState
import bas.app.shift.api.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TerminalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTerminalBinding

    private lateinit var adapter: ConsoleAdapter
    private var isUpgradeSessionActive = false  // Отслеживаем активную сессию UPGRADE
    private var isRebootSessionActive = false   // Отслеживаем активную сессию REBOOT
    private var isDeepDiveSessionActive = false // Отслеживаем активную сессию DEEP_DIVE
    private val levelViews by lazy {
        listOf<View>(
            findViewById(R.id.lvl1), findViewById(R.id.lvl2), findViewById(R.id.lvl3),
            findViewById(R.id.lvl4), findViewById(R.id.lvl5)
        )
    }

    private val colors = listOf(
        R.color.noise1, R.color.noise2, R.color.noise3,
        R.color.noise4, R.color.noise5
    )

    private var noise = 0.0
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

        updateNoise(0.0)          // старт
        
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
            val totalItemCount = adapter.itemCount
            
            if (totalItemCount > 0) {
                val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
                val lastCompletelyVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()
                
                // Скроллим только если последний элемент не полностью виден
                if (lastCompletelyVisiblePosition < totalItemCount - 1) {
                    binding.consoleList.post {
                        // Скроллим к самому низу списка, не центрируя элемент
                        val lastView = layoutManager.findViewByPosition(totalItemCount - 1)
                        if (lastView != null) {
                            // Скроллим так, чтобы последний элемент был внизу экрана
                            val scrollY = lastView.bottom - binding.consoleList.height
                            if (scrollY > 0) {
                                binding.consoleList.scrollBy(0, scrollY)
                            }
                        } else {
                            // Если view еще не создан, используем scrollToPosition как fallback
                            binding.consoleList.scrollToPosition(totalItemCount - 1)
                        }
                    }
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
            "DEEP_DIVE.START" -> {
                handleDeepDiveStartCommand()
            }
            "DEEP_DIVE.END" -> {
                handleDeepDiveEndCommand(fullCommand)
            }
            "UTILS.GLOBAL_NOIZE" -> {
                handleGlobalNoiseCommand()
            }
            "UTILS.USER_COUNT" -> {
                handleUserCountCommand()
            }
            "SHIFT.PROXY.DEPLOY" -> {
                handleProxyDeployCommand(fullCommand)
            }
            "SHIFT.PROXY.STATUS" -> {
                handleProxyStatusCommand()
            }
            "CROSS.LINK" -> {
                handleCrossLinkCommand(fullCommand)
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
                    noiseManager.adjustNoise(command.noiseIncrease.toDouble())
                }
            }
        }
        
        smoothScrollToBottom()
    }

    private fun incNoise(delta: Int) {
        noise = (noise + delta).coerceIn(0.0, 5.0)
        updateNoise(noise)
        if (NoiseHelper.getNoiseLevel(noise) >= 3) {
            showGlitchEvent()
        }
    }

    private fun showGlitchEvent() {
        Toast.makeText(this, "Глюк-атака! Шум 3+", Toast.LENGTH_SHORT).show()
    }

    fun updateNoise(noiseValue: Double) {
        val noiseLevel = NoiseHelper.getNoiseLevel(noiseValue)
        val progress = NoiseHelper.getLevelProgress(noiseValue)
        
        levelViews.forEachIndexed { idx, v ->
            val shouldHighlight = idx < noiseLevel
            val isCurrentLevel = idx == noiseLevel
            val shouldPartialHighlight = isCurrentLevel && progress > 0.0
            
            when {
                shouldHighlight -> {
                    v.setBackgroundColor(ContextCompat.getColor(this, colors[idx]))
                }
                shouldPartialHighlight -> {
                    // Для текущего уровня с частичным заполнением - используем полупрозрачный цвет
                    val color = ContextCompat.getColor(this, colors[idx])
                    val alpha = (255 * progress).toInt().coerceIn(0, 255)
                    v.setBackgroundColor((color and 0x00FFFFFF) or (alpha shl 24))
                }
                else -> {
                    v.setBackgroundColor(ContextCompat.getColor(this, R.color.noiseOff))
                }
            }
        }

        // Обновляем отображение дробного значения
        binding.noiseValue.text = NoiseHelper.formatNoiseValue(noiseValue)

        when (noiseLevel) {
            0, 1 -> binding.noiseOverlay.visibility = View.GONE
            2 -> showNoise(noiseLevel)
            3 -> {
                showNoise(noiseLevel)
                applyGlitch(noiseLevel)
                vibrator()
            }
            4 -> showRedScrim()
            5 -> {
                showRedScrim()
                demonJumpScare()
            }
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
        noiseManager.adjustNoise(-2.0)
        
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
        noiseManager.adjustNoise(-1.0)
        
        // Завершаем сессию REBOOT
        isRebootSessionActive = false
        
        smoothScrollToBottom()
    }

    private fun handleDeepDiveStartCommand() {
        val executingMsg = "Выполняю: DEEP_DIVE.START"
        adapter.addTyping(executingMsg)
        saveResponseToHistory(executingMsg)

        val deepDiveText = """
            === ГЛУБОКОЕ ПОГРУЖЕНИЕ ===
            
            Дип! Дип! Дип!
            
            Цифровая реальность обволакивает сознание...
            Матрица кода начинает пульсировать в ритме твоего сердца.
            
            Ты чувствуешь, как границы между физическим и виртуальным 
            начинают размываться. Нули и единицы танцуют перед глазами,
            создавая причудливые узоры из света и тени.
            
            "Дип!" - шепчет система, и ты понимаешь, что это не просто
            звук, а приглашение в глубины, куда обычные пользователи
            никогда не осмелятся заглянуть.
            
            Сознание начинает растворяться в потоках данных...
            Ты становишься частью сети, частью самой системы.
            
            Для завершения погружения используйте команду:
            DEEP_DIVE.END <глубина>
            
            Где <глубина> - число от 1 до 5, полученное от мастера.
        """.trimIndent()

        adapter.addTyping(deepDiveText)
        saveResponseToHistory(deepDiveText)

        // Активируем сессию DEEP_DIVE
        isDeepDiveSessionActive = true

        smoothScrollToBottom()
    }

    private fun handleDeepDiveEndCommand(fullCommand: String) {
        val executingMsg = "Выполняю: DEEP_DIVE.END"
        adapter.addTyping(executingMsg)
        saveResponseToHistory(executingMsg)

        // Проверяем активную сессию DEEP_DIVE
        if (!isDeepDiveSessionActive) {
            val errorMsg = "Ошибка: Нет активной сессии погружения. Сначала выполните DEEP_DIVE.START"
            adapter.addTyping(errorMsg)
            saveResponseToHistory(errorMsg)
            smoothScrollToBottom()
            return
        }

        // Парсим параметр глубины
        val parts = fullCommand.split(" ")
        if (parts.size < 2) {
            val errorMsg = "Ошибка: Не указана глубина. Используйте: DEEP_DIVE.END <глубина> (1-5)"
            adapter.addTyping(errorMsg)
            saveResponseToHistory(errorMsg)
            smoothScrollToBottom()
            return
        }

        val depthStr = parts[1]
        val depth = try {
            depthStr.toInt()
        } catch (e: NumberFormatException) {
            val errorMsg = "Ошибка: Глубина должна быть числом. Используйте: DEEP_DIVE.END <глубина> (1-5)"
            adapter.addTyping(errorMsg)
            saveResponseToHistory(errorMsg)
            smoothScrollToBottom()
            return
        }

        if (depth < 1 || depth > 5) {
            val errorMsg = "Ошибка: Глубина должна быть от 1 до 5. Используйте: DEEP_DIVE.END <глубина> (1-5)"
            adapter.addTyping(errorMsg)
            saveResponseToHistory(errorMsg)
            smoothScrollToBottom()
            return
        }

        val returnText = """
            === ВОЗВРАЩЕНИЕ ИЗ ГЛУБИН ===
            
            Глубина-глубина, я не твой…
            Отпусти меня, глубина…
            
            Сознание начинает возвращаться из цифровых глубин.
            Ты чувствуешь, как виртуальная реальность постепенно
            отпускает тебя, возвращая в физический мир.
            
            "Дип!" - последний раз звучит в ушах, но теперь это
            прощальный привет, а не приглашение.
            
            Ты возвращаешься с глубины $depth, неся с собой
            частичку цифрового мира в своем сознании.
            
            Погружение завершено. Шум увеличивается.
        """.trimIndent()

        adapter.addTyping(returnText)
        saveResponseToHistory(returnText)

        // Увеличиваем шум на указанную глубину
        noiseManager.adjustNoise(depth.toDouble())

        // Завершаем сессию DEEP_DIVE
        isDeepDiveSessionActive = false

        smoothScrollToBottom()
    }

    private fun handleGlobalNoiseCommand() {
        val executingMsg = "Выполняю: UTILS.GLOBAL_NOIZE"
        val processMsg = "Получаю данные о глобальном шуме..."
        
        adapter.addTyping(executingMsg)
        adapter.addTyping(processMsg)
        saveResponseToHistory(executingMsg)
        saveResponseToHistory(processMsg)
        
        val currentUserId = UserPrefsHelper.getUserId(this) ?: return
        
        RetrofitClient.noiseApi.getUserNoise(currentUserId)
            .enqueue(object : Callback<NoiseState> {
                override fun onResponse(call: Call<NoiseState>, response: Response<NoiseState>) {
                    if (response.isSuccessful && response.body() != null) {
                        val noiseState = response.body()!!
                        val resultMsg = """
                            === ГЛОБАЛЬНЫЙ ШУМ ===
                            
                            Текущий уровень глобального шума: ${noiseState.globalLevel}
                            Значение шума: ${String.format("%.2f", noiseState.globalNoise)}
                            
                            Глобальный шум влияет на всех Шумомантов одновременно.
                            Чем выше уровень, тем сильнее воздействие на цифровую реальность.
                        """.trimIndent()
                        
                        adapter.addTyping(resultMsg)
                        saveResponseToHistory(resultMsg)
                    } else {
                        val errorMsg = "Ошибка получения данных о глобальном шуме: ${response.code()}"
                        adapter.addTyping(errorMsg)
                        saveResponseToHistory(errorMsg)
                    }
                    smoothScrollToBottom()
                }
                
                override fun onFailure(call: Call<NoiseState>, t: Throwable) {
                    val errorMsg = "Ошибка подключения: ${t.message}"
                    adapter.addTyping(errorMsg)
                    saveResponseToHistory(errorMsg)
                    smoothScrollToBottom()
                }
            })
    }

    private fun handleUserCountCommand() {
        val executingMsg = "Выполняю: UTILS.USER_COUNT"
        val processMsg = "Подсчитываю активных Шумомантов..."
        
        adapter.addTyping(executingMsg)
        adapter.addTyping(processMsg)
        saveResponseToHistory(executingMsg)
        saveResponseToHistory(processMsg)
        
        val currentUserId = UserPrefsHelper.getUserId(this) ?: return
        
        RetrofitClient.noiseApi.getUserNoise(currentUserId)
            .enqueue(object : Callback<NoiseState> {
                override fun onResponse(call: Call<NoiseState>, response: Response<NoiseState>) {
                    if (response.isSuccessful && response.body() != null) {
                        val noiseState = response.body()!!
                        val resultMsg = """
                            === АКТИВНЫЕ ШУМОМАНТЫ ===
                            
                            Количество активных Шумомантов: ${noiseState.noisemancers}
                            
                            Каждый активный Шумомант вносит свой вклад
                            в общий уровень глобального шума.
                        """.trimIndent()
                        
                        adapter.addTyping(resultMsg)
                        saveResponseToHistory(resultMsg)
                    } else {
                        val errorMsg = "Ошибка получения данных о количестве пользователей: ${response.code()}"
                        adapter.addTyping(errorMsg)
                        saveResponseToHistory(errorMsg)
                    }
                    smoothScrollToBottom()
                }
                
                override fun onFailure(call: Call<NoiseState>, t: Throwable) {
                    val errorMsg = "Ошибка подключения: ${t.message}"
                    adapter.addTyping(errorMsg)
                    saveResponseToHistory(errorMsg)
                    smoothScrollToBottom()
                }
            })
    }

    private fun handleProxyDeployCommand(fullCommand: String) {
        val executingMsg = "Выполняю: SHIFT.PROXY.DEPLOY"
        adapter.addTyping(executingMsg)
        saveResponseToHistory(executingMsg)
        
        // Проверяем, есть ли уже Proxy эффект
        val hasProxyEffect = noiseManager.hasProxyEffect()
        
        if (hasProxyEffect) {
            val errorMsg = "Ошибка: Proxy узел уже развернут. Повторная активация невозможна."
            adapter.addTyping(errorMsg)
            saveResponseToHistory(errorMsg)
            smoothScrollToBottom()
            return
        }
        
        // Парсим параметр node
        val parts = fullCommand.split(" ")
        if (parts.size < 2) {
            val errorMsg = "Ошибка: Не указан параметр <node>. Используйте: SHIFT.PROXY.DEPLOY <node>"
            adapter.addTyping(errorMsg)
            saveResponseToHistory(errorMsg)
            smoothScrollToBottom()
            return
        }
        
        val nodeName = parts[1]
        
        val processMsg = "Разворачиваю Proxy узел '$nodeName'..."
        adapter.addTyping(processMsg)
        saveResponseToHistory(processMsg)
        
        // Добавляем шум за развертывание узла (+2)
        val currentUserId = UserPrefsHelper.getUserId(this) ?: return
        noiseManager.adjustNoise(2.0)
        
        // Применяем Proxy эффект
        noiseManager.applyProxyEffect(currentUserId)
        
        // Сбрасываем шум на Proxy узле (уменьшаем на 10)
        val proxyUserId = "${currentUserId}_Proxy"
        val resetMsg = "Сбрасываю шум на Proxy узле..."
        adapter.addTyping(resetMsg)
        saveResponseToHistory(resetMsg)
        
        // Отправляем запрос на уменьшение шума на Proxy узле
        val request = bas.app.shift.models.NoiseAdjustRequest(delta = -10.0)
        RetrofitClient.noiseApi.adjustUserNoise(proxyUserId, request)
            .enqueue(object : Callback<bas.app.shift.models.NoiseAdjustResponse> {
                override fun onResponse(call: Call<bas.app.shift.models.NoiseAdjustResponse>, response: Response<bas.app.shift.models.NoiseAdjustResponse>) {
                    if (response.isSuccessful) {
                        val resetSuccessMsg = "Шум на Proxy узле сброшен."
                        adapter.addTyping(resetSuccessMsg)
                        saveResponseToHistory(resetSuccessMsg)
                    } else {
                        val resetErrorMsg = "Предупреждение: не удалось сбросить шум на Proxy узле (${response.code()})"
                        adapter.addTyping(resetErrorMsg)
                        saveResponseToHistory(resetErrorMsg)
                    }
                    
                    // Показываем сообщение об успешном развертывании
                    showProxyDeploySuccess(nodeName, currentUserId)
                }
                
                override fun onFailure(call: Call<bas.app.shift.models.NoiseAdjustResponse>, t: Throwable) {
                    val resetErrorMsg = "Предупреждение: не удалось сбросить шум на Proxy узле (${t.message})"
                    adapter.addTyping(resetErrorMsg)
                    saveResponseToHistory(resetErrorMsg)
                    
                    // Показываем сообщение об успешном развертывании
                    showProxyDeploySuccess(nodeName, currentUserId)
                }
            })
    }
    
    private fun showProxyDeploySuccess(nodeName: String, currentUserId: String) {
        val successMsg = """
            === PROXY УЗЕЛ РАЗВЕРНУТ ===
            
            Узел '$nodeName' успешно развернут и активен.
            Эффект "Узел Proxy установлен и работает" применен.
            
            Теперь при выполнении команд, генерирующих положительный шум,
            шум будет автоматически делиться пополам между вашим ID
            и ID узла (${currentUserId}_Proxy).
            
            Узел будет активен 24 часа, после чего эффект автоматически истечет.
        """.trimIndent()
        
        adapter.addTyping(successMsg)
        saveResponseToHistory(successMsg)
        
        smoothScrollToBottom()
    }
    
    private fun handleCrossLinkCommand(fullCommand: String) {
        val executingMsg = "Выполняю: CROSS.LINK"
        adapter.addTyping(executingMsg)
        saveResponseToHistory(executingMsg)
        
        // Проверяем, есть ли уже Cross-Link эффект
        val hasCrossLinkEffect = noiseManager.hasCrossLinkEffect()
        
        if (hasCrossLinkEffect) {
            val errorMsg = "Ошибка: Cross-Link связь уже установлена. Сначала разорвите существующую связь."
            adapter.addTyping(errorMsg)
            saveResponseToHistory(errorMsg)
            smoothScrollToBottom()
            return
        }
        
        // Парсим параметр partner_id
        val parts = fullCommand.split(" ")
        if (parts.size < 2) {
            val errorMsg = "Ошибка: Не указан параметр <partner_id>. Используйте: CROSS.LINK <partner_id>"
            adapter.addTyping(errorMsg)
            saveResponseToHistory(errorMsg)
            smoothScrollToBottom()
            return
        }
        
        val partnerId = parts[1]
        
        val processMsg = "Инициирую связку с партнером '$partnerId'..."
        adapter.addTyping(processMsg)
        saveResponseToHistory(processMsg)
        
        // Получаем профиль партнера
        RetrofitClient.userProfileApi.getUserProfile(partnerId)
            .enqueue(object : Callback<bas.app.shift.models.User> {
                override fun onResponse(call: Call<bas.app.shift.models.User>, response: Response<bas.app.shift.models.User>) {
                    if (response.isSuccessful && response.body() != null) {
                        val partnerProfile = response.body()!!
                        val currentUserId = UserPrefsHelper.getUserId(this@TerminalActivity) ?: return
                        val currentUserProfile = UserPrefsHelper.getUserData(this@TerminalActivity) ?: return
                        
                        // Применяем Cross-Link эффект для обоих пользователей
                        noiseManager.applyCrossLinkEffect(
                            currentUserId, 
                            partnerId, 
                            currentUserProfile.characterName, 
                            partnerProfile.characterName
                        )
                        
                        val successMsg = """
                            === CROSS-LINK СВЯЗЬ УСТАНОВЛЕНА ===
                            
                            Связь с ${partnerProfile.characterName} успешно установлена.
                            Эффект "Связь с ${partnerProfile.characterName} установлена, шум делится пополам" применен.
                            
                            Теперь при выполнении команд, генерирующих положительный шум,
                            шум будет автоматически делиться пополам между вами
                            и вашим партнером.
                            
                            Связь будет активна 24 часа, после чего эффект автоматически истечет.
                        """.trimIndent()
                        
                        adapter.addTyping(successMsg)
                        saveResponseToHistory(successMsg)
                    } else {
                        val errorMsg = "Ошибка: Партнер с ID '$partnerId' не найден (${response.code()})"
                        adapter.addTyping(errorMsg)
                        saveResponseToHistory(errorMsg)
                    }
                    smoothScrollToBottom()
                }
                
                override fun onFailure(call: Call<bas.app.shift.models.User>, t: Throwable) {
                    val errorMsg = "Ошибка: Не удалось найти партнера '$partnerId' (${t.message})"
                    adapter.addTyping(errorMsg)
                    saveResponseToHistory(errorMsg)
                    smoothScrollToBottom()
                }
            })
    }
    
    private fun handleProxyStatusCommand() {
        val executingMsg = "Выполняю: SHIFT.PROXY.STATUS"
        adapter.addTyping(executingMsg)
        saveResponseToHistory(executingMsg)
        
        // Проверяем, есть ли активный Proxy эффект
        val hasProxyEffect = noiseManager.hasProxyEffect()
        
        if (!hasProxyEffect) {
            val errorMsg = "Ошибка: Proxy узел не развернут. Используйте SHIFT.PROXY.DEPLOY для развертывания узла."
            adapter.addTyping(errorMsg)
            saveResponseToHistory(errorMsg)
            smoothScrollToBottom()
            return
        }
        
        val processMsg = "Проверяю статус Proxy узла..."
        adapter.addTyping(processMsg)
        saveResponseToHistory(processMsg)
        
        val currentUserId = UserPrefsHelper.getUserId(this) ?: return
        val proxyUserId = "${currentUserId}_Proxy"
        
        // Запрашиваем шум Proxy узла
        RetrofitClient.noiseApi.getUserNoise(proxyUserId)
            .enqueue(object : Callback<NoiseState> {
                override fun onResponse(call: Call<NoiseState>, response: Response<NoiseState>) {
                    if (response.isSuccessful && response.body() != null) {
                        val noiseState = response.body()!!
                        val localLevel = NoiseHelper.getNoiseLevel(noiseState.localNoise)
                        val resultMsg = """
                            === СТАТУС PROXY УЗЛА ===
                            
                            ID узла: $proxyUserId
                            Локальный уровень шума узла: $localLevel
                            Локальное значение шума узла: ${String.format("%.2f", noiseState.localNoise)}
                            
                            Proxy узел активен и функционирует.
                            Шум автоматически распределяется между основным
                            пользователем и узлом при выполнении команд.
                        """.trimIndent()
                        
                        adapter.addTyping(resultMsg)
                        saveResponseToHistory(resultMsg)
                    } else {
                        val errorMsg = "Ошибка получения данных Proxy узла: ${response.code()}"
                        adapter.addTyping(errorMsg)
                        saveResponseToHistory(errorMsg)
                    }
                    smoothScrollToBottom()
                }
                
                override fun onFailure(call: Call<NoiseState>, t: Throwable) {
                    val errorMsg = "Ошибка подключения к Proxy узлу: ${t.message}"
                    adapter.addTyping(errorMsg)
                    saveResponseToHistory(errorMsg)
                    smoothScrollToBottom()
                }
            })
    }


}
