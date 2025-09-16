package bas.app.shift.models

import java.time.LocalTime

data class TerminalHistoryItem(
    val text: String,
    val timestamp: LocalTime
)

data class TerminalHistory(
    val commands: List<TerminalHistoryItem> = emptyList(),
    val responses: List<TerminalHistoryItem> = emptyList()
) {
    
    fun addCommand(command: String, timestamp: LocalTime = LocalTime.now()): TerminalHistory {
        return copy(commands = commands + TerminalHistoryItem(command, timestamp))
    }
    
    fun addResponse(response: String, timestamp: LocalTime = LocalTime.now()): TerminalHistory {
        return copy(responses = responses + TerminalHistoryItem(response, timestamp))
    }
    
    fun getLastCommand(): String? {
        return commands.lastOrNull()?.text
    }
    
    fun getLastResponse(): String? {
        return responses.lastOrNull()?.text
    }
    
    fun isEmpty(): Boolean {
        return commands.isEmpty() && responses.isEmpty()
    }
}
