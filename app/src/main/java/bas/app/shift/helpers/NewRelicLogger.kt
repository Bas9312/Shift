package bas.app.shift.helpers

import com.newrelic.agent.android.NewRelic

/**
 * Ships LogHelper output to New Relic Logs. Replaces the Bugfender logger.
 *
 * New Relic's log API takes a bare message with no tag parameter, so the caller location
 * that Bugfender used to carry in its tag field is prepended to the message instead —
 * otherwise a line in the New Relic UI would not say where it came from. Resolving that
 * location is [CallerTag]'s job, shared with [AndroidStandardLogger] so that what logcat
 * shows locally and what New Relic receives cannot drift apart.
 */
class NewRelicLogger : ILogger {

    override fun v(message: String) {
        NewRelic.logVerbose(withTag(message))
    }

    override fun d(message: String) {
        NewRelic.logDebug(withTag(message))
    }

    override fun i(message: String) {
        NewRelic.logInfo(withTag(message))
    }

    override fun w(message: String) {
        NewRelic.logWarning(withTag(message))
    }

    override fun e(message: String) {
        NewRelic.logError(withTag(message))
    }

    private fun withTag(message: String) = "${CallerTag.resolve()} $message"
}
