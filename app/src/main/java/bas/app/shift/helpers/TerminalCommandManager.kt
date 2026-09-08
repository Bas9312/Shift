package bas.app.shift.helpers

import bas.app.shift.models.TerminalCommand

object TerminalCommandManager {
    
    private val allCommands = listOf(
        // CAMERA группа
        TerminalCommand("CAMERA", "CAMERA.FIND", "<объект>", "Найти камеру и показать кадр/фрагмент"),
        TerminalCommand("CAMERA", "CAMERA.ERASE", "<фрагмент>", "Стереть/замазать кусок записи"),
        
        // NET группа
        TerminalCommand("NET", "NET.SEARCH", "<запрос>", "Глубокий поиск по сетям/источникам"),
        
        // DEVICE группа
        TerminalCommand("DEVICE", "DEVICE.UNLOCK", "<замок>", "Открыть электронный замок/дверь"),
        TerminalCommand("DEVICE", "DEVICE.OFF", "<система>", "Отключить систему/свет/сигнализацию"),
        TerminalCommand("DEVICE", "DEVICE.CONTROL", "<дрон/робот>", "Перехватить управление"),
        
        // INVENTORY группа
        TerminalCommand("INVENTORY", "INVENTORY.STORE", "<предмет>", "Оцифровать вещь и убрать «в облако»"),
        TerminalCommand("INVENTORY", "INVENTORY.RETRIEVE", "<предмет>", "Материализовать вещь обратно"),
        
        // TRACE группа
        TerminalCommand("TRACE", "TRACE.PHONE", "<номер>", "Приблизительная геолокация телефона"),
        TerminalCommand("TRACE", "TRACE.USER", "<аккаунт>", "Цифровой след: IP/сервисы/логины"),
        
        // SHIFT-Proxy группа (требует модуль 91)
        TerminalCommand("SHIFT-Proxy", "SHIFT.PROXY.DEPLOY", "<node>", "Развернуть узел на 24 часа", 91),
        TerminalCommand("SHIFT-Proxy", "SHIFT.PROXY.STATUS", "", "Проверить статус Proxy узла", 91),
        
        // Cross-Vault группа (требует модуль 92)
        TerminalCommand("Cross-Vault", "CROSS.LINK", "<partner>", "Связать хранилища (хэндшейк)", 92),
        TerminalCommand("Cross-Vault", "CROSS.RETRIEVE", "<item> HERE TO_PARTNER", "Достать общий предмет", 92),
        TerminalCommand("Cross-Vault", "CROSS.CAST", "VIA PARTNER <…>", "Запустить команду через партнёра/его узел", 92),
        
        // Human-Vault группа (требует модуль 93)
        TerminalCommand("Human-Vault", "HUMAN.UPLOAD", "", "«Само-оцифровка»/выход в транзит", 93),
        TerminalCommand("Human-Vault", "HUMAN.EXIT", "", "Материализация из транзита", 93),
        
        // USER группа
        TerminalCommand("USER", "USER.REBOOT.START", "", "Начать перезагрузку: осознанный цифровой отдых"),
        TerminalCommand("USER", "USER.REBOOT.END", "", "Завершить перезагрузку: вернуться в систему"),
        TerminalCommand("USER", "USER.UPGRADE.START", "", "Начать вики-серфинг: получить случайные страницы"),
        TerminalCommand("USER", "USER.UPGRADE.END", "<статьи>", "Завершить вики-серфинг: отправить путь из статей"),
        TerminalCommand("USER", "USER.FORMAT", "", "Сброс шума (ОПАСНО!)"),
        
        // DEEP_DIVE группа
        TerminalCommand("DEEP_DIVE", "DEEP_DIVE.START", "", "Начать глубокое погружение в цифровую реальность"),
        TerminalCommand("DEEP_DIVE", "DEEP_DIVE.END", "<глубина>", "Завершить погружение: вернуться с указанной глубины"),
        
        // UTILS группа
        TerminalCommand("UTILS", "UTILS.GLOBAL_NOIZE", "", "Получить текущий уровень глобального шума"),
        TerminalCommand("UTILS", "UTILS.USER_COUNT", "", "Получить количество активных Шумомантов"),
        
        // HELP команда
        TerminalCommand("SYSTEM", "HELP", "", "Показать все доступные команды")
    )
    
    /**
     * Цены, присланные сервером: он источник правды, потому что их правит мастер из панели.
     * Пока ответ не пришёл (первый запуск, нет сети), показываем зашитые значения — они
     * совпадают с тем, чем справочник был заполнен изначально.
     * На само начисление не влияет: серверу отправляется имя команды, цену он берёт у себя.
     */
    private var serverCosts: Map<String, Double> = emptyMap()

    fun setServerCosts(costs: Map<String, Double>) {
        serverCosts = costs
    }

    /**
     * Цена команды для показа игроку. null — сервер ещё не отвечал и кэша нет; тогда и
     * выполнить команду нельзя, так что честнее показать прочерк, чем выдуманное число.
     */
    fun costOf(command: TerminalCommand): Double? = serverCosts[command.name]

    fun getAvailableCommands(availableModules: List<Int> = emptyList()): List<TerminalCommand> {
        return allCommands.filter { command ->
            command.requiredModuleId == null || availableModules.contains(command.requiredModuleId)
        }
    }
    
    fun findCommand(commandText: String, availableModules: List<Int> = emptyList()): TerminalCommand? {
        val availableCommands = getAvailableCommands(availableModules)
        // Сопоставляем по ПЕРВОМУ токену (имя команды до пробела), а не по префиксу.
        // Прежний startsWith по имени ошибочно матчил похожие вводы:
        // "CROSS.LINKAGE" -> "CROSS.LINK", "USER.REBOOT" -> "USER.REBOOT.START".
        // Теперь имя должно совпасть точно, при этом аргументы после пробела допускаются
        // ("CAMERA.FIND 123" -> команда "CAMERA.FIND").
        val firstToken = commandText.trim().substringBefore(' ')
        return availableCommands.find { command ->
            command.fullCommand.equals(commandText, ignoreCase = true) ||
            command.name.equals(firstToken, ignoreCase = true)
        }
    }
    
    fun getHelpText(availableModules: List<Int> = emptyList()): String {
        val commands = getAvailableCommands(availableModules)
        val groupedCommands = commands.groupBy { it.group }
        
        val helpText = StringBuilder()
        helpText.append("=== ДОСТУПНЫЕ КОМАНДЫ ===\n\n")
        
        groupedCommands.forEach { (group, groupCommands) ->
            helpText.append("[$group]\n")
            groupCommands.forEach { command ->
                helpText.append("  ${command.fullCommand} - ${command.description} (шум: ${formatNoiseChange(costOf(command))})\n")
            }
            helpText.append("\n")
        }
        
        return helpText.toString()
    }
    
    private fun formatNoiseChange(noiseChange: Double?): String {
        if (noiseChange == null) return "?"
        // Цены приходят дробными (в панели можно задать 0.5), но целые показываем без хвоста.
        val text = if (noiseChange == noiseChange.toLong().toDouble()) {
            noiseChange.toLong().toString()
        } else {
            noiseChange.toString()
        }
        return if (noiseChange > 0) "+$text" else text
    }

    /**
     * Команды, ответ на которые не нужно дублировать в MG-чат (либо у них свой канал
     * общения с MG, либо это чисто локальные/служебные команды).
     */
    fun shouldSkipMgNotification(command: String): Boolean {
        return when {
            command.startsWith("SHIFT.PROXY") -> true
            command.startsWith("USER.") && !command.startsWith("USER.FORMAT") -> true
            command.startsWith("UTILS.") -> true
            command.startsWith("SYSTEM.") -> true
            else -> false
        }
    }
}
