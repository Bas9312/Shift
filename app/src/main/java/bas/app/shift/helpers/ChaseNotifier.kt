package bas.app.shift.helpers

import bas.app.shift.R
import bas.app.shift.ShiftApplication
import bas.app.shift.models.ChaseEvent
import bas.app.shift.services.LocationNotifications

/**
 * Показывает игроку, что произошло с цепочкой погони: открылась новая цель, цепочка
 * пройдена или ветка кончилась тупиком.
 *
 * Текст точки (`textToShowOnEnter`) сервер кладёт в само событие, и показываем его здесь же,
 * над служебной строкой. Раньше его показывала обычная обработка входа в точку в
 * LocationService, и на каждый шаг цепочки игрок получал два уведомления подряд: одно с
 * текстом места, второе — со статусом погони. Теперь уведомление одно, и LocationService
 * своё не показывает, если сервер засчитал вход как шаг цепочки.
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

            // Текст места идёт первым — он про то, что игрок видит вокруг; служебная строка
            // под ним объясняет, что стало с цепочкой. Уведомление разворачивается
            // (BigTextStyle), так что склейка ничего не обрезает.
            val status = context.getString(textRes)
            val placeText = event.text?.trim()?.takeIf { it.isNotEmpty() }
            notifications.showPointNotification(
                context.getString(titleRes),
                if (placeText == null) status else "$placeText\n\n$status",
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
