package bas.app.shift.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Исключение приложения из оптимизации батареи.
 *
 * Вся фоновая механика игры (вход в зону точки → уведомление, новые сообщения) держится на
 * [bas.app.shift.services.LocationService] — foreground-сервисе с таймерами. В режиме Doze
 * (экран потушен, телефон лежит неподвижно) система режет таким приложениям сеть и усыпляет
 * процессор, из-за чего проверки перестают тикать вовремя. Приложение, которое пользователь
 * добавил в исключения, из-под этих ограничений выведено.
 *
 * Просить исключение имеет смысл ровно тогда, когда игрок входит в игру. Отказ не блокирует
 * ничего — просто повышается риск, что уведомление придёт с задержкой.
 */
object BatteryOptimization {

    private const val PREFS_NAME = "battery_optimization"
    private const val KEY_LAST_ASKED_AT = "last_asked_at"

    /**
     * Повторно спрашиваем не чаще раза в сутки: игра выездная и разовая, между установкой и
     * самой игрой обычно проходит время — если игрок отказал заранее, в день игры разумно
     * спросить ещё раз, но не дёргать его на каждое включение «в игре».
     */
    private const val ASK_COOLDOWN_MS = 24 * 60 * 60 * 1000L

    /** Уже исключено — Doze приложение не режет. */
    fun isIgnoring(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (e: Exception) {
            // На некоторых прошивках PowerManager может кинуть — считаем, что не исключены,
            // но и не падаем: это подсказка, а не критичный путь.
            LogHelper.w("BatteryOptimization: не удалось проверить статус: ${e.message}")
            false
        }
    }

    /** Стоит ли показывать просьбу: ещё не исключены и не спрашивали в последние сутки. */
    fun shouldAsk(context: Context): Boolean {
        if (isIgnoring(context)) return false
        val lastAsked = prefs(context).getLong(KEY_LAST_ASKED_AT, 0L)
        return System.currentTimeMillis() - lastAsked >= ASK_COOLDOWN_MS
    }

    /** Отмечает, что просьбу показали (независимо от ответа игрока). */
    fun markAsked(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_ASKED_AT, System.currentTimeMillis()).apply()
    }

    /**
     * Системный диалог «разрешить работу в фоне без ограничений» для нашего пакета.
     * Если конкретная прошивка его не поддерживает — вернётся `null`, тогда зовите
     * [settingsIntent].
     */
    fun requestIntent(context: Context): Intent? {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
        return intent.takeIf { it.resolveActivity(context.packageManager) != null }
    }

    /** Запасной путь: общий список «Оптимизация батареи» в настройках. */
    fun settingsIntent(context: Context): Intent? {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        return intent.takeIf { it.resolveActivity(context.packageManager) != null }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
