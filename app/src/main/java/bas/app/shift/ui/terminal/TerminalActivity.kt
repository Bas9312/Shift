package bas.app.shift.ui.terminal

import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import bas.app.shift.R
import bas.app.shift.databinding.ActivityTerminalBinding
import bas.app.shift.helpers.LogHelper
import bas.app.shift.helpers.NetworkErrors
import bas.app.shift.helpers.NoiseManager
import bas.app.shift.helpers.TerminalCommandManager
import bas.app.shift.helpers.TerminalHistoryHelper
import bas.app.shift.helpers.TerminalVisualEffects
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

/** Как долго буферизовать строки истории терминала в памяти перед записью на диск. */
private const val HISTORY_FLUSH_DELAY_MS = 1500L

class TerminalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTerminalBinding

    private lateinit var adapter: ConsoleAdapter
    private var isUpgradeSessionActive = false  // Отслеживаем активную сессию UPGRADE
    private var isRebootSessionActive = false   // Отслеживаем активную сессию REBOOT
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
    private var globalNoise = 0.0
    private var terminalHistory = TerminalHistory()
    private var historyDirty = false
    private val historyFlushHandler = Handler(Looper.getMainLooper())
    private val historyFlushRunnable = Runnable { flushHistory() }
    private lateinit var noiseManager: NoiseManager
    private var lastExecutedCommand: String? = null
    private val visualEffects by lazy { TerminalVisualEffects(this, binding.root, binding.noiseOverlay) }

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

        // Восстанавливаем активную сессию UPGRADE из преференсов (если была начата ранее)
        val prefs = getSharedPreferences("terminal_prefs", MODE_PRIVATE)
        isUpgradeSessionActive = prefs.getBoolean("upgrade_session_active", false)

        binding.topBar.setNavigationOnClickListener {
            finish()
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Экран может уйти в фон/закрыться в любой момент — сбрасываем буфер истории на диск,
        // не дожидаясь отложенного флеша.
        flushHistory()
    }

    override fun onDestroy() {
        super.onDestroy()
        historyFlushHandler.removeCallbacks(historyFlushRunnable)
        flushHistory()
        if (::noiseManager.isInitialized) {
            noiseManager.cleanup()
        }
    }

    /** Немедленно сохраняет буферизованную историю, если есть несохранённые изменения. */
    private fun flushHistory() {
        historyFlushHandler.removeCallbacks(historyFlushRunnable)
        if (historyDirty) {
            TerminalHistoryHelper.saveHistory(this, terminalHistory)
            historyDirty = false
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
    
    private fun addLineImmediate(text: String, type: Line.Type, timestamp: java.time.LocalTime = java.time.LocalTime.now()) {
        // Для истории и других случаев - показываем сразу
        adapter.add(Line(text, type, timestamp))
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
        val commandTimestamp = java.time.LocalTime.now()
        
        // Сохраняем команду в историю
        saveCommandToHistory(cmd, commandTimestamp)
        
        val availableModules = getAvailableModules()
        val command = TerminalCommandManager.findCommand(cmd, availableModules)
        
        Handler(Looper.getMainLooper()).postDelayed({
            if (command != null) {
                // Обрабатываем найденную команду
                processCommand(command, cmd, commandTimestamp)
            } else {
                // Неизвестная команда
                val errorMsg = "ОШИБКА: Неизвестная команда '$cmd'"
                val helpMsg = "Введите HELP для списка доступных команд"
                
                adapter.addTyping(errorMsg)
                adapter.addTyping(helpMsg)
                
                // Сохраняем ответы в историю
                saveResponseToHistory(errorMsg, commandTimestamp)
                saveResponseToHistory(helpMsg, commandTimestamp)
                
                smoothScrollToBottom()
            }
        }, 300)
    }
    
    private fun processCommand(command: TerminalCommand, fullCommand: String, commandTimestamp: java.time.LocalTime) {
        // USER.FORMAT — опасный сброс шума. Требуем явное подтверждение, чтобы случайный
        // ввод или тап автодополнения не обнуляли шум без спроса.
        if (command.name == "USER.FORMAT") {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("USER.FORMAT")
                .setMessage("Опасная команда: сброс шума на ${command.noiseIncrease}. Выполнить?")
                .setPositiveButton("Выполнить") { _, _ ->
                    lastExecutedCommand = fullCommand
                    executeGenericNoiseCommand(command, fullCommand, commandTimestamp)
                }
                .setNegativeButton("Отмена") { _, _ ->
                    val msg = "USER.FORMAT отменён"
                    adapter.addTyping(msg)
                    saveResponseToHistory(msg, commandTimestamp)
                    smoothScrollToBottom()
                }
                .setCancelable(false)
                .show()
            return
        }

        // Сохраняем команду для отправки в MG
        lastExecutedCommand = fullCommand

        when (command.name) {
            "HELP" -> {
                val helpText = TerminalCommandManager.getHelpText(getAvailableModules())
                addLine(helpText, Line.Type.RSP)
                saveResponseToHistory(helpText, commandTimestamp)
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
                executeGenericNoiseCommand(command, fullCommand, commandTimestamp)
            }
        }

        smoothScrollToBottom()
    }

    /** Обычная команда: печать статуса, изменение шума и отправка в MG-чат. */
    private fun executeGenericNoiseCommand(command: TerminalCommand, fullCommand: String, commandTimestamp: java.time.LocalTime) {
        val executingMsg = "Выполняю: $fullCommand"
        val processMsg = "Команда в процессе выполнения..."

        adapter.addTyping(executingMsg)
        adapter.addTyping(processMsg)

        // Сохраняем ответы в историю
        saveResponseToHistory(executingMsg, commandTimestamp)
        saveResponseToHistory(processMsg, commandTimestamp)

        // Отправляем команду на сервер для изменения шума
        if (command.noiseIncrease != 0) {
            adjustNoiseAndUpdateGlobal(command.noiseIncrease.toDouble())
        }

        // Отправляем команду в MG чат
        sendToMg()

        smoothScrollToBottom()
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
            2 -> visualEffects.showNoise(noiseLevel)
            3 -> {
                visualEffects.showNoise(noiseLevel)
                visualEffects.applyGlitch(noiseLevel)
                visualEffects.vibrate()
            }
            4 -> visualEffects.showRedScrim()
            5 -> {
                visualEffects.showRedScrim()
                visualEffects.demonJumpScare()
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
        terminalHistory.commands.forEach { commandItem ->
            addLineImmediate(commandItem.text, Line.Type.CMD, commandItem.timestamp)
        }
        
        terminalHistory.responses.forEach { responseItem ->
            addLineImmediate(responseItem.text, Line.Type.RSP, responseItem.timestamp)
        }
    }
    
    private fun saveCommandToHistory(command: String, timestamp: java.time.LocalTime = java.time.LocalTime.now()) {
        terminalHistory = TerminalHistoryHelper.appendCommand(terminalHistory, command, timestamp)
        scheduleHistoryFlush()
    }

    private fun saveResponseToHistory(response: String, timestamp: java.time.LocalTime = java.time.LocalTime.now()) {
        terminalHistory = TerminalHistoryHelper.appendResponse(terminalHistory, response, timestamp)
        scheduleHistoryFlush()
    }

    /** Буферизует несколько строк, сохранённых подряд (частый случай при выполнении команды), в один флеш. */
    private fun scheduleHistoryFlush() {
        historyDirty = true
        historyFlushHandler.removeCallbacks(historyFlushRunnable)
        historyFlushHandler.postDelayed(historyFlushRunnable, HISTORY_FLUSH_DELAY_MS)
    }
    
    private fun initNoiseManager() {
        val userId = UserPrefsHelper.getUserId(this)
        // ВСЕГДА создаём менеджер, даже при пустом userId. Иначе обработчики команд
        // (adjustNoiseAndUpdateGlobal, Proxy/CrossLink) обращались бы к неинициализированному
        // lateinit и роняли терминал. При пустом userId методы NoiseManager сами делают no-op.
        noiseManager = NoiseManager(this)
        noiseManager.setOnNoiseUpdateListener { newNoise ->
            noise = newNoise
            updateNoise(noise)
        }
        noiseManager.setOnGlobalNoiseUpdateListener { newGlobalNoise ->
            globalNoise = newGlobalNoise
            updateGlobalNoiseDisplay()
        }
        noiseManager.setOnCommandSuccessListener {
            // Убираем вызов sendToMg отсюда - будем вызывать из обработчиков команд
        }

        if (userId.isNotEmpty()) {
            noiseManager.setUserId(userId)
            // Запускаем периодическое обновление шума и получаем текущий шум сразу
            noiseManager.startPeriodicNoiseUpdate()
            noiseManager.fetchCurrentNoise()
        } else {
            LogHelper.e("TerminalActivity: UserId is empty, NoiseManager работает в no-op режиме")
        }
    }
    
    private fun updateGlobalNoiseDisplay() {
        val roundedNoise = String.format("%.2f", globalNoise)
        binding.globalNoiseValue.text = "Global $roundedNoise"
        
        // Устанавливаем цвет в зависимости от уровня шума
        val color = when {
            globalNoise >= 4.0 -> Color.parseColor("#FF0000") // Ярко-алый
            globalNoise >= 3.0 -> Color.parseColor("#FF4444") // Красный
            globalNoise >= 2.0 -> Color.parseColor("#FFAA00") // Желтый
            else -> Color.parseColor("#C8E1FF") // Обычный цвет
        }
        
        binding.globalNoiseValue.setTextColor(color)
    }
    
    private fun adjustNoiseAndUpdateGlobal(delta: Double) {
        noiseManager.adjustNoise(delta)
        // Глобальный шум обновится автоматически через callback в NoiseManager
    }
    
    private fun isDeepDiveSessionActive(): Boolean {
        val prefs = getSharedPreferences("terminal_prefs", MODE_PRIVATE)
        return prefs.getBoolean("isDeepDiveSessionActive", false)
    }
    
    private fun setDeepDiveSessionActive(active: Boolean) {
        val prefs = getSharedPreferences("terminal_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("isDeepDiveSessionActive", active).apply()
    }
    
    private fun sendToMg() {
        val command = lastExecutedCommand
        if (command == null) {
            LogHelper.d("TerminalActivity: No command to send to MG")
            return
        }
        
        // Проверяем, нужно ли отправлять эту команду
        if (shouldSkipCommand(command)) {
            LogHelper.d("TerminalActivity: Skipping command: $command")
            return
        }
        
        LogHelper.d("TerminalActivity: Sending command to MG: $command")
        
        // Отправляем сообщение в чат
        sendCommandToMg(command)
    }
    
    private fun shouldSkipCommand(command: String): Boolean {
        return when {
            command.startsWith("SHIFT.PROXY") -> true
            command.startsWith("USER.") && !command.startsWith("USER.FORMAT") -> true
            command.startsWith("UTILS.") -> true
            command.startsWith("SYSTEM.") -> true
            else -> false
        }
    }
    
    private fun sendCommandToMg(command: String) {
        val userId = UserPrefsHelper.getUserId(this)
        if (userId.isEmpty()) {
            LogHelper.e("TerminalActivity: UserId is empty, cannot send to MG")
            return
        }
        
        val messageText = "Команда в терминале: $command"
        
        // Создаем сообщение для отправки
        val textBody = messageText.toRequestBody("text/plain".toMediaTypeOrNull())
        val recipientBody = "MG_BAS".toRequestBody("text/plain".toMediaTypeOrNull())
        val tagsBody = "9".toRequestBody("text/plain".toMediaTypeOrNull())
        
        RetrofitClient.messagesApi.createMessage(
            userId = userId,
            text = textBody,
            recipientId = recipientBody,
            tags = tagsBody,
            answerTo = null,
            files = null
        ).enqueue(object : retrofit2.Callback<bas.app.shift.models.CreateMessageResponse> {
            override fun onResponse(call: retrofit2.Call<bas.app.shift.models.CreateMessageResponse>, response: retrofit2.Response<bas.app.shift.models.CreateMessageResponse>) {
                if (response.isSuccessful) {
                    LogHelper.d("TerminalActivity: Command sent to MG successfully")
                } else {
                    LogHelper.e("TerminalActivity: Failed to send command to MG: ${response.code()}")
                }
            }
            
            override fun onFailure(call: retrofit2.Call<bas.app.shift.models.CreateMessageResponse>, t: Throwable) {
                LogHelper.e("TerminalActivity: Error sending command to MG: ${t.message}")
            }
        })
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
                // Сохраняем флаг активной сессии в преференсы
                val prefs = getSharedPreferences("terminal_prefs", MODE_PRIVATE)
                prefs.edit().putBoolean("upgrade_session_active", true).apply()
            },
            onError = { error ->
                val errorMsg = "Ошибка получения страниц Wikipedia: $error"
                adapter.addTyping(errorMsg)
                saveResponseToHistory(errorMsg)
            }
        )
    
        
        smoothScrollToBottom()
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
        adjustNoiseAndUpdateGlobal(-2.0)
        
        // Завершаем сессию UPGRADE
        isUpgradeSessionActive = false
        // Сбрасываем флаг активной сессии в преференсах
        val prefs = getSharedPreferences("terminal_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("upgrade_session_active", false).apply()
        
        // Отправляем команду в MG чат
        //sendToMg()
        
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
        adjustNoiseAndUpdateGlobal(-1.0)
        
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
        setDeepDiveSessionActive(true)

        // Отправляем команду в MG чат
        sendToMg()

        smoothScrollToBottom()
    }

    private fun handleDeepDiveEndCommand(fullCommand: String) {
        val executingMsg = "Выполняю: DEEP_DIVE.END"
        adapter.addTyping(executingMsg)
        saveResponseToHistory(executingMsg)

        // Проверяем активную сессию DEEP_DIVE
        if (!isDeepDiveSessionActive()) {
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
        adjustNoiseAndUpdateGlobal(depth.toDouble())

        // Завершаем сессию DEEP_DIVE
        setDeepDiveSessionActive(false)

        // Отправляем команду в MG чат
        //sendToMg()

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
                        val errorMsg = "Ошибка получения данных о глобальном шуме: ${NetworkErrors.http(response.code())}"
                        adapter.addTyping(errorMsg)
                        saveResponseToHistory(errorMsg)
                    }
                    smoothScrollToBottom()
                }

                override fun onFailure(call: Call<NoiseState>, t: Throwable) {
                    val errorMsg = "Ошибка подключения: ${NetworkErrors.network(t)}"
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
                        val errorMsg = "Ошибка получения данных о количестве пользователей: ${NetworkErrors.http(response.code())}"
                        adapter.addTyping(errorMsg)
                        saveResponseToHistory(errorMsg)
                    }
                    smoothScrollToBottom()
                }

                override fun onFailure(call: Call<NoiseState>, t: Throwable) {
                    val errorMsg = "Ошибка подключения: ${NetworkErrors.network(t)}"
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
        adjustNoiseAndUpdateGlobal(2.0)
        
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
                        val resetErrorMsg = "Предупреждение: не удалось сбросить шум на Proxy узле (${NetworkErrors.http(response.code())})"
                        adapter.addTyping(resetErrorMsg)
                        saveResponseToHistory(resetErrorMsg)
                    }
                    
                    // Показываем сообщение об успешном развертывании
                    showProxyDeploySuccess(nodeName, currentUserId)
                }
                
                override fun onFailure(call: Call<bas.app.shift.models.NoiseAdjustResponse>, t: Throwable) {
                    val resetErrorMsg = "Предупреждение: не удалось сбросить шум на Proxy узле (${NetworkErrors.network(t)})"
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
                        val errorMsg = if (response.code() == 404) {
                            "Ошибка: Партнер с ID '$partnerId' не найден"
                        } else {
                            "Ошибка: Партнер с ID '$partnerId' не найден (${NetworkErrors.http(response.code())})"
                        }
                        adapter.addTyping(errorMsg)
                        saveResponseToHistory(errorMsg)
                    }
                    smoothScrollToBottom()
                }
                
                override fun onFailure(call: Call<bas.app.shift.models.User>, t: Throwable) {
                    val errorMsg = "Ошибка: Не удалось найти партнера '$partnerId' (${NetworkErrors.network(t)})"
                    adapter.addTyping(errorMsg)
                    saveResponseToHistory(errorMsg)
                    smoothScrollToBottom()
                }
            })
        
        // Отправляем команду в MG чат
        sendToMg()
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
                        val errorMsg = "Ошибка получения данных Proxy узла: ${NetworkErrors.http(response.code())}"
                        adapter.addTyping(errorMsg)
                        saveResponseToHistory(errorMsg)
                    }
                    smoothScrollToBottom()
                }
                
                override fun onFailure(call: Call<NoiseState>, t: Throwable) {
                    val errorMsg = "Ошибка подключения к Proxy узлу: ${NetworkErrors.network(t)}"
                    adapter.addTyping(errorMsg)
                    saveResponseToHistory(errorMsg)
                    smoothScrollToBottom()
                }
            })
    }


}
