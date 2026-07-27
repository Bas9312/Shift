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
import bas.app.shift.helpers.NoiseManager
import bas.app.shift.helpers.TerminalCommandManager
import bas.app.shift.helpers.TerminalHistoryHelper
import bas.app.shift.helpers.TerminalVisualEffects
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.helpers.NoiseHelper
import bas.app.shift.models.TerminalCommand
import bas.app.shift.models.TerminalHistory
import bas.app.shift.api.RetrofitClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

/** Как долго буферизовать строки истории терминала в памяти перед записью на диск. */
private const val HISTORY_FLUSH_DELAY_MS = 1500L

class TerminalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTerminalBinding

    private lateinit var adapter: ConsoleAdapter
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
    private val proxyCommands by lazy { TerminalProxyCommands(this, adapter, noiseManager) }
    private val upgradeRebootCommands by lazy { TerminalUpgradeRebootCommands(this, adapter) }
    private val deepDiveCommands by lazy { TerminalDeepDiveCommands(this, adapter) }

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
        adapter.cancelAllTyping()
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
    
    internal fun smoothScrollToBottom() {
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
                upgradeRebootCommands.handleRebootStartCommand()
            }
            "USER.REBOOT.END" -> {
                upgradeRebootCommands.handleRebootEndCommand()
            }
            "USER.UPGRADE.START" -> {
                upgradeRebootCommands.handleUpgradeStartCommand()
            }
            "USER.UPGRADE.END" -> {
                upgradeRebootCommands.handleUpgradeEndCommand(fullCommand)
            }
            "DEEP_DIVE.START" -> {
                deepDiveCommands.handleDeepDiveStartCommand()
            }
            "DEEP_DIVE.END" -> {
                deepDiveCommands.handleDeepDiveEndCommand(fullCommand)
            }
            "UTILS.GLOBAL_NOIZE" -> {
                deepDiveCommands.handleGlobalNoiseCommand()
            }
            "UTILS.USER_COUNT" -> {
                deepDiveCommands.handleUserCountCommand()
            }
            "SHIFT.PROXY.DEPLOY" -> {
                proxyCommands.handleProxyDeployCommand(fullCommand)
            }
            "SHIFT.PROXY.STATUS" -> {
                proxyCommands.handleProxyStatusCommand()
            }
            "CROSS.LINK" -> {
                proxyCommands.handleCrossLinkCommand(fullCommand)
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

    internal fun saveResponseToHistory(response: String, timestamp: java.time.LocalTime = java.time.LocalTime.now()) {
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
        noiseManager.setOnCommandFailureListener { errorText ->
            // Раньше при сбое запроса терминал молчал: "Команда в процессе выполнения..."
            // печаталось сразу и оставалось единственным сообщением навсегда, даже если шум
            // на сервере не изменился (нет сети/таймаут/ошибка сервера) — игрок не знал,
            // что команду нужно повторить.
            val msg = "ОШИБКА: изменение шума не применено — $errorText"
            adapter.addTyping(msg)
            saveResponseToHistory(msg)
            smoothScrollToBottom()
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
    
    internal fun adjustNoiseAndUpdateGlobal(delta: Double) {
        noiseManager.adjustNoise(delta)
        // Глобальный шум обновится автоматически через callback в NoiseManager
    }
    
    internal fun sendToMg() {
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
    
}
