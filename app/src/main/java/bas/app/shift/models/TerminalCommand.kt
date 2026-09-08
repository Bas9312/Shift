package bas.app.shift.models

data class TerminalCommand(
    val group: String,
    val name: String,
    val parameters: String,
    val description: String,
    /**
     * Модуль, без которого команда не показывается. Цены здесь нет намеренно: сколько шума
     * стоит команда, знает только сервер (таблица noise_command_costs), и правится это из
     * панели мастера. Держать вторую копию в приложении незачем — без сети команду всё
     * равно не выполнить.
     */
    val requiredModuleId: Int? = null
) {
    val fullCommand: String
        get() = if (parameters.isNotEmpty()) "$name $parameters" else name
    
    val displayText: String
        get() = if (parameters.isNotEmpty()) "$name $parameters - $description" else "$name - $description"
}
