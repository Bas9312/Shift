package bas.app.shift.helpers

import bas.app.shift.models.TerminalHistory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class TerminalHistoryHelperTest {

    @Test
    fun appendCommand_underCap_appendsAndKeepsResponsesUntouched() {
        val history = TerminalHistory().let {
            TerminalHistoryHelper.appendResponse(it, "resp1", LocalTime.NOON)
        }
        val result = TerminalHistoryHelper.appendCommand(history, "cmd1", LocalTime.NOON)

        assertEquals(listOf("cmd1"), result.commands.map { it.text })
        assertEquals(listOf("resp1"), result.responses.map { it.text })
    }

    @Test
    fun appendResponse_underCap_appendsAndKeepsCommandsUntouched() {
        val history = TerminalHistory().let {
            TerminalHistoryHelper.appendCommand(it, "cmd1", LocalTime.NOON)
        }
        val result = TerminalHistoryHelper.appendResponse(history, "resp1", LocalTime.NOON)

        assertEquals(listOf("cmd1"), result.commands.map { it.text })
        assertEquals(listOf("resp1"), result.responses.map { it.text })
    }

    @Test
    fun appendCommand_exactlyAtCap_keepsAllEntries() {
        var history = TerminalHistory()
        repeat(100) { i ->
            history = TerminalHistoryHelper.appendCommand(history, "cmd$i", LocalTime.NOON)
        }

        assertEquals(100, history.commands.size)
        assertEquals("cmd0", history.commands.first().text)
        assertEquals("cmd99", history.commands.last().text)
    }

    @Test
    fun appendCommand_overCap_dropsOldestAndKeepsMaxSize() {
        var history = TerminalHistory()
        repeat(105) { i ->
            history = TerminalHistoryHelper.appendCommand(history, "cmd$i", LocalTime.NOON)
        }

        assertEquals(100, history.commands.size)
        // Oldest 5 (cmd0..cmd4) were evicted, newest 100 (cmd5..cmd104) remain.
        assertEquals("cmd5", history.commands.first().text)
        assertEquals("cmd104", history.commands.last().text)
    }

    @Test
    fun appendResponse_overCap_dropsOldestAndKeepsMaxSize() {
        var history = TerminalHistory()
        repeat(103) { i ->
            history = TerminalHistoryHelper.appendResponse(history, "resp$i", LocalTime.NOON)
        }

        assertEquals(100, history.responses.size)
        assertEquals("resp3", history.responses.first().text)
        assertEquals("resp102", history.responses.last().text)
    }

    @Test
    fun appendCommand_and_appendResponse_capsAreIndependent() {
        var history = TerminalHistory()
        repeat(101) { i -> history = TerminalHistoryHelper.appendCommand(history, "cmd$i", LocalTime.NOON) }
        repeat(3) { i -> history = TerminalHistoryHelper.appendResponse(history, "resp$i", LocalTime.NOON) }

        assertEquals(100, history.commands.size)
        assertEquals(3, history.responses.size)
    }
}
