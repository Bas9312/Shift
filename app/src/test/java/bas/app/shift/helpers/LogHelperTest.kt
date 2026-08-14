package bas.app.shift.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * LogHelper — singleton `object`, состояние (loggers/logLevel) общее на весь тестовый процесс.
 * Каждый тест поэтому сам выставляет нужный уровень и проверяет только СВОЙ FakeLogger —
 * это делает тесты независимыми от порядка выполнения и от загрязнения предыдущими тестами.
 */
class LogHelperTest {

    private class FakeLogger : ILogger {
        val messages = mutableListOf<Pair<String, String>>()

        override fun v(message: String) { messages.add("v" to message) }
        override fun d(message: String) { messages.add("d" to message) }
        override fun i(message: String) { messages.add("i" to message) }
        override fun w(message: String) { messages.add("w" to message) }
        override fun e(message: String) { messages.add("e" to message) }
    }

    private fun emitAllLevels() {
        LogHelper.v("v-msg")
        LogHelper.d("d-msg")
        LogHelper.i("i-msg")
        LogHelper.w("w-msg")
        LogHelper.e("e-msg")
    }

    @Test
    fun verboseLevel_deliversAllSeverities() {
        LogHelper.setLogLevel(LogHelper.LogLevel.VERBOSE)
        val logger = FakeLogger()
        LogHelper.addLogger(logger)

        emitAllLevels()

        assertEquals(
            listOf("v" to "v-msg", "d" to "d-msg", "i" to "i-msg", "w" to "w-msg", "e" to "e-msg"),
            logger.messages
        )
    }

    @Test
    fun warningLevel_filtersBelowWarningButAllowsWarningAndError() {
        LogHelper.setLogLevel(LogHelper.LogLevel.WARNING)
        val logger = FakeLogger()
        LogHelper.addLogger(logger)

        emitAllLevels()

        assertEquals(listOf("w" to "w-msg", "e" to "e-msg"), logger.messages)
    }

    @Test
    fun errorLevel_onlyErrorDelivers() {
        // e() в реализации не проверяет logLevel вовсе — на самом строгом уровне фильтра
        // это единственное, что должно дойти.
        LogHelper.setLogLevel(LogHelper.LogLevel.ERROR)
        val logger = FakeLogger()
        LogHelper.addLogger(logger)

        emitAllLevels()

        assertEquals(listOf("e" to "e-msg"), logger.messages)
    }

    @Test
    fun addLogger_dispatchesToAllRegisteredLoggers() {
        LogHelper.setLogLevel(LogHelper.LogLevel.VERBOSE)
        val first = FakeLogger()
        val second = FakeLogger()
        LogHelper.addLogger(first)
        LogHelper.addLogger(second)

        LogHelper.i("broadcast")

        assertEquals(listOf("i" to "broadcast"), first.messages)
        assertEquals(listOf("i" to "broadcast"), second.messages)
    }
}
