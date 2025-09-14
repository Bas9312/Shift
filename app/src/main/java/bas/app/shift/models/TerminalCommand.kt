package bas.app.shift.models

data class TerminalCommand(
    val group: String,
    val name: String,
    val parameters: String,
    val description: String,
    val noiseIncrease: Int,
    val requiredModuleId: Int? = null
) {
    val fullCommand: String
        get() = if (parameters.isNotEmpty()) "$name $parameters" else name
    
    val displayText: String
        get() = if (parameters.isNotEmpty()) "$name $parameters - $description" else "$name - $description"
}
