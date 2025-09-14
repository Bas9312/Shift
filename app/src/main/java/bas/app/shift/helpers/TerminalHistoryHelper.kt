package bas.app.shift.helpers

import android.content.Context
import android.content.SharedPreferences
import bas.app.shift.models.TerminalHistory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object TerminalHistoryHelper {
    private const val PREFS_NAME = "terminal_history"
    private const val KEY_HISTORY = "history"
    private const val MAX_HISTORY_SIZE = 100 // Максимальное количество команд в истории
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun saveHistory(context: Context, history: TerminalHistory) {
        val prefs = getPrefs(context)
        val gson = Gson()
        val historyJson = gson.toJson(history)
        prefs.edit().putString(KEY_HISTORY, historyJson).apply()
    }
    
    fun loadHistory(context: Context): TerminalHistory {
        val prefs = getPrefs(context)
        val historyJson = prefs.getString(KEY_HISTORY, null)
        
        if (historyJson == null) {
            return TerminalHistory()
        }
        
        return try {
            val gson = Gson()
            val type = object : TypeToken<TerminalHistory>() {}.type
            gson.fromJson(historyJson, type) ?: TerminalHistory()
        } catch (e: Exception) {
            LogHelper.e("TerminalHistoryHelper: Error loading history: ${e.message}")
            TerminalHistory()
        }
    }
    
    fun clearHistory(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().remove(KEY_HISTORY).apply()
    }
    
    fun addCommandToHistory(context: Context, command: String) {
        val currentHistory = loadHistory(context)
        val newHistory = currentHistory.addCommand(command)
        
        // Ограничиваем размер истории
        val limitedHistory = if (newHistory.commands.size > MAX_HISTORY_SIZE) {
            val limitedCommands = newHistory.commands.takeLast(MAX_HISTORY_SIZE)
            val limitedResponses = newHistory.responses.takeLast(MAX_HISTORY_SIZE)
            TerminalHistory(limitedCommands, limitedResponses)
        } else {
            newHistory
        }
        
        saveHistory(context, limitedHistory)
    }
    
    fun addResponseToHistory(context: Context, response: String) {
        val currentHistory = loadHistory(context)
        val newHistory = currentHistory.addResponse(response)
        saveHistory(context, newHistory)
    }
}
