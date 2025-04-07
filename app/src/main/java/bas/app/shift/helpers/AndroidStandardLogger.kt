package bas.app.shift.helpers

import android.util.Log

class AndroidStandardLogger : ILogger {
    override fun v(message: String) {
        Log.v(tag, message)
    }

    override fun d(message: String) {
        val maxLogSize = 1500
        for (i in 0..message.length / maxLogSize) {
            val start = i * maxLogSize
            var end = (i + 1) * maxLogSize
            end = if (end > message.length) message.length else end
            Log.d(tag, message.substring(start, end))
        }
    }

    override fun i(message: String) {
        Log.i(tag, message)
    }

    override fun w(message: String) {
        Log.w(tag, message)
    }

    override fun e(message: String) {
        Log.e(tag, message)
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