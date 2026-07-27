package bas.app.shift.helpers

import bas.app.shift.models.TerminalCommand

object TerminalCommandManager {
    
    private val allCommands = listOf(
        // CAMERA группа
        TerminalCommand("CAMERA", "CAMERA.FIND", "<объект>", "Найти камеру и показать кадр/фрагмент", 1),
        TerminalCommand("CAMERA", "CAMERA.ERASE", "<фрагмент>", "Стереть/замазать кусок записи", 2),
        
        // NET группа
        TerminalCommand("NET", "NET.SEARCH", "<запрос>", "Глубокий поиск по сетям/источникам", 2),
        
        // DEVICE группа
        TerminalCommand("DEVICE", "DEVICE.UNLOCK", "<замок>", "Открыть электронный замок/дверь", 1),
        TerminalCommand("DEVICE", "DEVICE.OFF", "<система>", "Отключить систему/свет/сигнализацию", 1),
        TerminalCommand("DEVICE", "DEVICE.CONTROL", "<дрон/робот>", "Перехватить управление", 2),
        
        // INVENTORY группа
        TerminalCommand("INVENTORY", "INVENTORY.STORE", "<предмет>", "Оцифровать вещь и убрать «в облако»", 2),
        TerminalCommand("INVENTORY", "INVENTORY.RETRIEVE", "<предмет>", "Материализовать вещь обратно", 1),
        
        // TRACE группа
        TerminalCommand("TRACE", "TRACE.PHONE", "<номер>", "Приблизительная геолокация телефона", 2),
        TerminalCommand("TRACE", "TRACE.USER", "<аккаунт>", "Цифровой след: IP/сервисы/логины", 2),
        
        // SHIFT-Proxy группа (требует модуль 91)
        TerminalCommand("SHIFT-Proxy", "SHIFT.PROXY.DEPLOY", "<node>", "Развернуть узел на 24 часа", 2, 91),
        TerminalCommand("SHIFT-Proxy", "SHIFT.PROXY.STATUS", "", "Проверить статус Proxy узла", 0, 91),
        
        // Cross-Vault группа (требует модуль 92)
        TerminalCommand("Cross-Vault", "CROSS.LINK", "<partner>", "Связать хранилища (хэндшейк)", 1, 92),
        TerminalCommand("Cross-Vault", "CROSS.RETRIEVE", "<item> HERE TO_PARTNER", "Достать общий предмет", 1, 92),
        TerminalCommand("Cross-Vault", "CROSS.CAST", "VIA PARTNER <…>", "Запустить команду через партнёра/его узел", 0, 92),
        
        // Human-Vault группа (требует модуль 93)
        TerminalCommand("Human-Vault", "HUMAN.UPLOAD", "", "«Само-оцифровка»/выход в транзит", 4, 93),
        TerminalCommand("Human-Vault", "HUMAN.EXIT", "", "Материализация из транзита", 0, 93),
        
        // USER группа
        TerminalCommand("USER", "USER.REBOOT.START", "", "Начать перезагрузку: осознанный цифровой отдых", 0),
        TerminalCommand("USER", "USER.REBOOT.END", "", "Завершить перезагрузку: вернуться в систему", -1),
        TerminalCommand("USER", "USER.UPGRADE.START", "", "Начать вики-серфинг: получить случайные страницы", 0),
        TerminalCommand("USER", "USER.UPGRADE.END", "<статьи>", "Завершить вики-серфинг: отправить путь из статей", -2),
        TerminalCommand("USER", "USER.FORMAT", "", "Сброс шума (ОПАСНО!)", -10),
        
        // DEEP_DIVE группа
        TerminalCommand("DEEP_DIVE", "DEEP_DIVE.START", "", "Начать глубокое погружение в цифровую реальность", 0),
        TerminalCommand("DEEP_DIVE", "DEEP_DIVE.END", "<глубина>", "Завершить погружение: вернуться с указанной глубины", 0),
        
        // UTILS группа
        TerminalCommand("UTILS", "UTILS.GLOBAL_NOIZE", "", "Получить текущий уровень глобального шума", 0),
        TerminalCommand("UTILS", "UTILS.USER_COUNT", "", "Получить количество активных Шумомантов", 0),
        
        // HELP команда
        TerminalCommand("SYSTEM", "HELP", "", "Показать все доступные команды", 0)
    )
    
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
                helpText.append("  ${command.fullCommand} - ${command.description} (шум: ${formatNoiseChange(command.noiseIncrease)})\n")
            }
            helpText.append("\n")
        }
        
        return helpText.toString()
    }
    
    private fun formatNoiseChange(noiseChange: Int): String {
        return when {
            noiseChange > 0 -> "+$noiseChange"
            noiseChange < 0 -> "$noiseChange"
            else -> "0"
        }
    }
}
