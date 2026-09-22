package bas.app.shift.helpers

/**
 * Works out which line of the app asked for a log line, for use as the log tag.
 *
 * Both loggers used to hardcode `stackTrace[5]`, which only holds while the call chain is
 * exactly caller → LogHelper → logger → tag getter. Any extra frame (a helper method, or a
 * method R8 chooses to inline in a release build) silently shifts the index and the tag then
 * points at LogHelper instead of the real caller. Searching for the LogHelper frame instead
 * does not care how deep the chain is.
 */
internal object CallerTag {

    private val LOG_HELPER_CLASS = LogHelper::class.java.name

    fun resolve(): String {
        val stack = Thread.currentThread().stackTrace
        val lastLogHelperFrame = stack.indexOfLast { it.className == LOG_HELPER_CLASS }
        val caller = stack.getOrNull(lastLogHelperFrame + 1) ?: return "unknown"
        val className = caller.className.substringAfterLast('.')
        return "$className.${caller.methodName}():${caller.lineNumber}"
    }
}
