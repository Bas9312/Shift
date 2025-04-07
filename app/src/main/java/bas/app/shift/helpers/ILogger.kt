package bas.app.shift.helpers

interface ILogger {
    /**
     * Send a VERBOSE log message.
     * @param message The message you would like logged.
     */
    fun v(message: String)

    /**
     * Send a DEBUG log message.
     * @param message The message you would like logged.
     */
    fun d(message: String)

    /**
     * Send a INFO log message.
     * @param message The message you would like logged.
     */
    fun i(message: String)

    /**
     * Send a WARNING log message.
     * @param message The message you would like logged.
     */
    fun w(message: String)

    /**
     * Send a ERROR log message.
     * @param message The message you would like logged.
     */
    fun e(message: String)
}