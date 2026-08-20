package bas.app.shift.helpers

import bas.app.shift.R
import bas.app.shift.ShiftApplication
import bas.app.shift.models.ChaseEvent
import bas.app.shift.services.LocationNotifications

/**
 * Показывает игроку, что произошло с цепочкой погони: открылась новая цель, цепочка
 * пройдена или ветка кончилась тупиком.
 *
 * Сам текст точки (`textToShowOnEnter`) здесь не показывается — его уже показывает
 * обычная обработка входа в точку в LocationService, и второе такое же уведомление
 * игроку ничего не добавит.
 */
object ChaseNotifier {

    /**
     * Одно и то же событие приходит дважды, когда отправка геолокации и явное сообщение
     * о входе успевают разойтись по времени. Держим недавно показанные, чтобы игрок не
     * получил два одинаковых уведомления подряд; через минуту тот же ключ снова годится
     * (цепочку можно перепройти заново).
     */
    private const val DEDUP_WINDOW_MS = 60_000L
    private val recentlyShown = mutableMapOf<String, Long>()

    fun notify(events: List<ChaseEvent>?) {
        if (events.isNullOrEmpty()) return

        val context = ShiftApplication.instance
        val notifications = LocationNotifications(context)

        events.forEach { event ->
            val key = "${event.event}|${event.questId}|${event.pointId}"
            if (!claim(key)) return@forEach

            val (titleRes, textRes) = when (event.event) {
                ChaseEvent.STARTED -> R.string.chase_started_title to R.string.chase_started_text
                ChaseEvent.ADVANCED -> R.string.chase_advanced_title to R.string.chase_advanced_text
                ChaseEvent.FINISHED -> R.string.chase_finished_title to R.string.chase_finished_text
                ChaseEvent.DEAD_END -> R.string.chase_dead_end_title to R.string.chase_dead_end_text
                else -> {
                    LogHelper.d("ChaseNotifier: неизвестное событие цепочки ${event.event}")
                    return@forEach
                }
            }

            notifications.showPointNotification(
                context.getString(titleRes),
                context.getString(textRes),
                ("chase-${event.pointId}-${event.event}").hashCode(),
            )
            LogHelper.d("ChaseNotifier: событие ${event.event} по цепочке ${event.questId}, точка ${event.pointId}")
        }
    }

    /** true, если это событие ещё не показывали в последнюю минуту. */
    @Synchronized
    private fun claim(key: String): Boolean {
        val now = System.currentTimeMillis()
        recentlyShown.entries.removeAll { now - it.value > DEDUP_WINDOW_MS }
        if (recentlyShown.containsKey(key)) return false
        recentlyShown[key] = now
        return true
    }
}
