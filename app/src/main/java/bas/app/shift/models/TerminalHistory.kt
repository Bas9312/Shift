package bas.app.shift.models

data class TerminalHistory(
    val commands: List<String> = emptyList(),
    val responses: List<String> = emptyList()
) {
    
    fun addCommand(command: String): TerminalHistory {
        return copy(commands = commands + command)
    }
    
    fun addResponse(response: String): TerminalHistory {
        return copy(responses = responses + response)
    }
    
    fun getLastCommand(): String? {
        return commands.lastOrNull()
    }
    
    fun getLastResponse(): String? {
        return responses.lastOrNull()
    }
    
    fun isEmpty(): Boolean {
        return commands.isEmpty() && responses.isEmpty()
    }
}
