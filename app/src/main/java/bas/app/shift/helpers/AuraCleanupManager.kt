package bas.app.shift.helpers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import bas.app.shift.models.AuraProblemType
import bas.app.shift.receivers.AuraCleanupReadyReceiver

/**
 * Чистка проблем в ауре: экстрасенс запускает процесс на время, по истечении срока
 * подтверждает результат, и только тогда изменение уходит на сервер.
 *
 * Состояние храним в SharedPreferences АБСОЛЮТНЫМ временем старта, а не обратным отсчётом:
 * процесс идёт 5–15 минут, за это время приложение спокойно успевает уйти в фон или быть
 * убитым системой, и таймер на Handler'е этого не переживёт. При повторном открытии ауры
 * оставшееся время пересчитывается от `startedAt`.
 *
 * Автоматического применения по таймеру намеренно нет: если экстрасенса отвлекли и процесс
 * прервался, чистка не должна пройти сама.
 */
object AuraCleanupManager {

    private const val PREFS = "aura_cleanup_prefs"
    private const val SEPARATOR = "|"

    /**
     * Сколько длится чистка каждого типа проблемы.
     * Типов, которых здесь нет (HOLE, PARASITE, OTHER), экстрасенс не чистит — только мастер.
     */
    private val DURATION_MINUTES = mapOf(
        AuraProblemType.TEAR to 15,
        AuraProblemType.SCAR to 5,
    )

    /** Что происходит с проблемой после успешной чистки. */
    sealed class Outcome {
        /** Проблема снимается полностью. */
        object Removed : Outcome()

        /** Проблема не исчезает, а превращается в другую (разрыв затягивается в шрам). */
        data class Converted(val toType: AuraProblemType, val toName: String) : Outcome()
    }

    data class Progress(
        val startedAt: Long,
        val problemType: AuraProblemType,
    ) {
        val durationMs: Long get() = (DURATION_MINUTES[problemType] ?: 0) * 60_000L
        fun remainingMs(now: Long): Long = (startedAt + durationMs - now).coerceAtLeast(0L)
        fun isReady(now: Long): Boolean = now >= startedAt + durationMs
    }

    fun canClean(type: AuraProblemType): Boolean = DURATION_MINUTES.containsKey(type)

    fun durationMinutes(type: AuraProblemType): Int? = DURATION_MINUTES[type]

    fun outcomeFor(type: AuraProblemType): Outcome? = when (type) {
        AuraProblemType.TEAR -> Outcome.Converted(AuraProblemType.SCAR, "Шрам")
        AuraProblemType.SCAR -> Outcome.Removed
        AuraProblemType.HOLE,
        AuraProblemType.PARASITE,
        AuraProblemType.OTHER -> null
    }

    fun start(context: Context, entityId: String, slot: Int, type: AuraProblemType, now: Long) {
        val minutes = DURATION_MINUTES[type] ?: return
        prefs(context).edit()
            .putString(key(entityId, slot), "$now$SEPARATOR${type.name}")
            .apply()
        scheduleReadyAlarm(context, entityId, slot, now + minutes * 60_000L)
        LogHelper.d("AuraCleanupManager: старт чистки $entityId slot=$slot type=$type на $minutes мин")
    }

    fun progress(context: Context, entityId: String, slot: Int): Progress? {
        val raw = prefs(context).getString(key(entityId, slot), null) ?: return null
        val parts = raw.split(SEPARATOR)
        val type = parts.getOrNull(1)?.let { name -> AuraProblemType.values().find { it.name == name } }
        val startedAt = parts.getOrNull(0)?.toLongOrNull()
        if (parts.size != 2 || startedAt == null || type == null || type !in DURATION_MINUTES) {
            // Битая запись (например, после смены формата) — молча выкидываем, чтобы
            // экстрасенс не застрял с чисткой, которую нельзя ни продолжить, ни отменить.
            LogHelper.w("AuraCleanupManager: битое состояние чистки '$raw', сбрасываю")
            cancel(context, entityId, slot)
            return null
        }
        val progress = Progress(startedAt, type)
        // Alarms don't survive a reboot; re-arming here (cheap, idempotent) means opening the
        // aura screen after a restart is enough to restore the pending notification, no
        // BOOT_COMPLETED receiver needed.
        val now = System.currentTimeMillis()
        if (!progress.isReady(now)) {
            scheduleReadyAlarm(context, entityId, slot, startedAt + progress.durationMs)
        }
        return progress
    }

    fun cancel(context: Context, entityId: String, slot: Int) {
        prefs(context).edit().remove(key(entityId, slot)).apply()
        alarmManager(context).cancel(readyAlarmPendingIntent(context, entityId, slot))
    }

    /**
     * Inexact, Doze-tolerant alarm — no SCHEDULE_EXACT_ALARM permission needed. A cleanup
     * window is 5-15 minutes, so a few minutes of slack on the notification doesn't matter;
     * the player still sees the accurate countdown whenever they reopen the aura screen.
     */
    private fun scheduleReadyAlarm(context: Context, entityId: String, slot: Int, triggerAtMillis: Long) {
        alarmManager(context).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            readyAlarmPendingIntent(context, entityId, slot),
        )
    }

    private fun readyAlarmPendingIntent(context: Context, entityId: String, slot: Int): PendingIntent {
        val intent = Intent(context, AuraCleanupReadyReceiver::class.java).apply {
            putExtra(AuraCleanupReadyReceiver.EXTRA_ENTITY_ID, entityId)
            putExtra(AuraCleanupReadyReceiver.EXTRA_SLOT, slot)
        }
        return PendingIntent.getBroadcast(
            context, key(entityId, slot).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alarmManager(context: Context) =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** "5:03" — для живого отсчёта на экране. */
    fun formatRemaining(remainingMs: Long): String {
        val totalSeconds = remainingMs / 1000
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(entityId: String, slot: Int) = "cleanup_${entityId}_$slot"
}
