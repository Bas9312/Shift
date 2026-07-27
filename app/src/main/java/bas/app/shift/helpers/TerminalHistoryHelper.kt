package bas.app.shift.helpers

import android.content.Context
import android.content.SharedPreferences
import bas.app.shift.models.LocalTimeAdapter
import bas.app.shift.models.TerminalHistory
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.time.LocalTime

object TerminalHistoryHelper {
    private const val PREFS_NAME = "terminal_history"
    private const val KEY_HISTORY = "history"
    private const val MAX_HISTORY_SIZE = 100 // Максимум записей команд И ответов в истории

    // Общий Gson с адаптером LocalTime — без него сериализация LocalTime рефлексией
    // на новых Android падает и обнуляет историю.
    private val gson = GsonBuilder()
        .registerTypeAdapter(LocalTime::class.java, LocalTimeAdapter())
        .create()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveHistory(context: Context, history: TerminalHistory) {
        val prefs = getPrefs(context)
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
            val type = object : TypeToken<TerminalHistory>() {}.type
            gson.fromJson<TerminalHistory>(historyJson, type) ?: TerminalHistory()
        } catch (e: Exception) {
            LogHelper.e("TerminalHistoryHelper: Error loading history: ${e.message}")
            TerminalHistory()
        }
    }

    /** Ограничивает и команды, и ответы последними MAX_HISTORY_SIZE записями. */
    private fun limited(history: TerminalHistory): TerminalHistory {
        val cmds = if (history.commands.size > MAX_HISTORY_SIZE) history.commands.takeLast(MAX_HISTORY_SIZE) else history.commands
        val resps = if (history.responses.size > MAX_HISTORY_SIZE) history.responses.takeLast(MAX_HISTORY_SIZE) else history.responses
        return if (cmds.size == history.commands.size && resps.size == history.responses.size) history
        else TerminalHistory(cmds, resps)
    }

    /**
     * Чистые (без I/O) операции добавления в историю. Вызывающий держит [TerminalHistory]
     * в памяти и сам решает, когда вызвать [saveHistory] — не на каждую строку (это было
     * бы load+parse+save всей истории на каждую команду/ответ терминала), а буферизуя и
     * сохраняя периодически/на паузе экрана.
     */
    fun appendCommand(history: TerminalHistory, command: String, timestamp: LocalTime = LocalTime.now()): TerminalHistory {
        return limited(history.addCommand(command, timestamp))
    }

    fun appendResponse(history: TerminalHistory, response: String, timestamp: LocalTime = LocalTime.now()): TerminalHistory {
        return limited(history.addResponse(response, timestamp))
    }
}
