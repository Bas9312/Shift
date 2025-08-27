package bas.app.shift.helpers

import com.bugfender.sdk.Bugfender

class BugfenderLogger: ILogger {

    override fun v(message: String) {
        Bugfender.d(tag, message)
    }

    override fun d(message: String) {
        Bugfender.d(tag, message)
    }

    override fun i(message: String) {
        Bugfender.i(tag, message)
    }

    override fun w(message: String) {
        Bugfender.w(tag, message)
    }

    override fun e(message: String) {
        Bugfender.e(tag, message)
    }



    private val tag: String
        get() {
            val stackTraceElement = Thread.currentThread().stackTrace[5]
            val fullClassName = stackTraceElement.className
            val className = fullClassName.substring(fullClassName.lastIndexOf(".") + 1)
            val methodName = stackTraceElement.methodName
            val lineNumber = stackTraceElement.lineNumber
            return "$className.$methodName():$lineNumber"
        }
}