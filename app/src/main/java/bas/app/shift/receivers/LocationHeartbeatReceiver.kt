package bas.app.shift.receivers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import bas.app.shift.helpers.LogHelper
import bas.app.shift.services.LocationService

/**
 * Пульс для [LocationService] в режиме Doze.
 *
 * Таймеры сервиса построены на `Handler.postDelayed`, а он считает время по
 * `SystemClock.uptimeMillis()`, которое во время глубокого сна не идёт: телефон лежит с
 * потушенным экраном — проверки точек и сообщений просто не тикают, пока его не разбудят.
 * Сам foreground-сервис при этом жив, ему нужен только толчок.
 *
 * Толчок даёт `AlarmManager.setAndAllowWhileIdle` — единственный вид будильника, который
 * срабатывает в Doze и при этом **не требует особых разрешений** (в отличие от
 * `setExactAndAllowWhileIdle`, которому на Android 12+ нужен `SCHEDULE_EXACT_ALARM`).
 * Система сама не даёт таким будильникам срабатывать чаще, чем примерно раз в 9–15 минут,
 * поэтому просить чаще смысла нет — [HEARTBEAT_INTERVAL_MS] выставлен под этот потолок.
 *
 * Будильник одноразовый и перевзводится на каждом срабатывании: повторяющийся
 * (`setRepeating`) в Doze не работает.
 */
class LocationHeartbeatReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        LogHelper.d("LocationHeartbeatReceiver: пульс")

        // Перевзводим следующий пульс сразу: даже если разбудить сервис не выйдет,
        // цепочка не должна оборваться.
        schedule(context)

        if (!isInGame(context)) {
            LogHelper.d("LocationHeartbeatReceiver: персонаж не в игре, сервис не трогаем")
            cancel(context)
            return
        }

        try {
            val serviceIntent = Intent(context, LocationService::class.java).apply {
                action = LocationService.ACTION_TICK
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            // На Android 12+ запуск foreground-сервиса из фона разрешён не всегда. Если
            // сервис жив (обычный случай — он foreground и Doze его не убивает), это
            // просто доставка команды и всё пройдёт. Если он всё-таки умер и запустить его
            // отсюда нельзя — поднимет MainActivity при следующем выходе на передний план.
            LogHelper.w("LocationHeartbeatReceiver: не удалось разбудить сервис: ${e.message}")
        }
    }

    private fun isInGame(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_IN_GAME, false)

    companion object {
        /**
         * Doze всё равно прижимает `setAndAllowWhileIdle` примерно к одному срабатыванию
         * в 9–15 минут, так что меньший интервал ничего не даст, а больший — потеряет
         * события.
         */
        private const val HEARTBEAT_INTERVAL_MS = 15 * 60 * 1000L

        private const val REQUEST_CODE = 4271
        private const val PREFS_NAME = "game_state"
        private const val KEY_IN_GAME = "is_in_game"

        /** Взводит следующий пульс. Вызывать при старте сервиса и на каждом срабатывании. */
        fun schedule(context: Context) {
            try {
                alarmManager(context)?.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + HEARTBEAT_INTERVAL_MS,
                    pendingIntent(context)
                )
            } catch (e: Exception) {
                LogHelper.w("LocationHeartbeatReceiver: не удалось взвести пульс: ${e.message}")
            }
        }

        /** Снимает пульс — при остановке сервиса и при выходе из игры. */
        fun cancel(context: Context) {
            try {
                alarmManager(context)?.cancel(pendingIntent(context))
            } catch (e: Exception) {
                LogHelper.w("LocationHeartbeatReceiver: не удалось снять пульс: ${e.message}")
            }
        }

        private fun pendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, LocationHeartbeatReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun alarmManager(context: Context): AlarmManager? =
            context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    }
}
