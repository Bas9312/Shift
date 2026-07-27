package bas.app.shift.helpers

object LogHelper {
    private val loggers: MutableList<ILogger> = ArrayList()
    private var logLevel = LogLevel.VERBOSE
    fun setLogLevel(level: LogLevel) {
        logLevel = level
    }

    @JvmStatic
    fun addLogger(logger: ILogger) {
        loggers.add(logger)
    }

    /**
     * Send a VERBOSE log message.
     *
     * @param message The message you would like logged.
     */
    @JvmStatic
    fun v(message: String) {
        if (logLevel.ordinal <= LogLevel.VERBOSE.ordinal) for (logger in loggers) logger.v(message)
    }

    /**
     * Send a DEBUG log message.
     *
     * @param message The message you would like logged.
     */
    @JvmStatic
    fun d(message: String) {
        if (logLevel.ordinal <= LogLevel.DEBUG.ordinal) for (logger in loggers) logger.d(message)
    }

    /**
     * Send a INFO log message.
     *
     * @param message The message you would like logged.
     */
    @JvmStatic
    fun i(message: String) {
        if (logLevel.ordinal <= LogLevel.INFO.ordinal) for (logger in loggers) logger.i(message)
    }

    /**
     * Send a WARNING log message.
     *
     * @param message The message you would like logged.
     */
    @JvmStatic
    fun w(message: String) {
        if (logLevel.ordinal <= LogLevel.WARNING.ordinal) for (logger in loggers) logger.w(message)
    }

    /**
     * Send a ERROR log message.
     *
     * @param message The message you would like logged.
     */
    @JvmStatic
    fun e(message: String) {
        for (logger in loggers) logger.e(message)
    }

    enum class LogLevel {
        VERBOSE, DEBUG, INFO, WARNING, ERROR
    }
}