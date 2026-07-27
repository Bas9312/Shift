package bas.app.shift

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import bas.app.shift.api.RetrofitClient
import bas.app.shift.helpers.NetworkErrors
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.models.PointRequest
import bas.app.shift.models.PointType
import bas.app.shift.services.LocationService
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Ритуал: кулдаун в SharedPreferences (переживает поворот экрана и перезапуск процесса),
 * создание точки SHRINKING_CIRCLE на сервере, состояние кнопки. Вынесено из MainActivity
 * механическим переносом, без изменения логики.
 */
class RitualManager(
    private val activity: MainActivity,
    private val button: MaterialButton,
) {
    private val tick = Runnable { updateButtonState() }

    fun perform() {
        if (isOnCooldown()) {
            val minutes = remainingCooldownMs() / 60000 + 1
            Toast.makeText(activity, "Ритуал недавно проводился. Подождите ещё ~$minutes мин.", Toast.LENGTH_SHORT).show()
            return
        }

        val location = LocationService.getCurrentLocation()
        if (location == null) {
            Toast.makeText(activity, "Не удалось получить текущую локацию", Toast.LENGTH_SHORT).show()
            return
        }

        val user = UserPrefsHelper.getUserData(activity)
        if (user == null) {
            Toast.makeText(activity, "Ошибка: данные пользователя не найдены", Toast.LENGTH_SHORT).show()
            return
        }

        button.isEnabled = false
        button.text = "Ритуал выполняется..."

        val pointRequest = PointRequest(
            type = PointType.SHRINKING_CIRCLE.serverValue,
            lat = location.latitude,
            lng = location.longitude,
            radius = 50.0, // Радиус 50 метров
            ownerId = user.userId,
            description = "Здесь случилась сильная магия",
            textToShowOnEnter = "Здесь прошёл ритуал",
        )

        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.shiftApi.createPoint(pointRequest)
                if (response.isSuccessful) {
                    // Фиксируем время ритуала в prefs — кулдаун переживает поворот экрана,
                    // уход на карту и даже перезапуск процесса (раньше жил только в поле Activity).
                    activity.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE).edit()
                        .putLong(KEY_LAST_RITUAL, System.currentTimeMillis()).apply()
                }
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    if (response.isSuccessful) {
                        Toast.makeText(activity, "Точка создана. Не забудьте написать МГ", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(activity, NetworkErrors.http(response.code()), Toast.LENGTH_SHORT).show()
                    }
                    updateButtonState()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    Toast.makeText(activity, NetworkErrors.network(e), Toast.LENGTH_SHORT).show()
                    updateButtonState()
                }
            }
        }
    }

    private fun isOnCooldown(): Boolean = remainingCooldownMs() > 0

    private fun remainingCooldownMs(): Long {
        val last = activity.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_RITUAL, 0L)
        return (RITUAL_COOLDOWN_MS - (System.currentTimeMillis() - last)).coerceAtLeast(0L)
    }

    /**
     * Приводит состояние кнопки ритуала в соответствие с кулдауном из prefs.
     * Вызывается из MainActivity.updateUI() и после каждой попытки ритуала.
     */
    fun updateButtonState() {
        if (button.visibility != View.VISIBLE) return
        button.removeCallbacks(tick)
        val remaining = remainingCooldownMs()
        if (remaining > 0) {
            button.isEnabled = false
            button.text = "Ритуал: ждите ~${remaining / 60000 + 1} мин"
            // Перепланируем обновление подписи/разблокировку (не реже раза в минуту)
            button.postDelayed(tick, minOf(remaining, 60000L))
        } else {
            button.isEnabled = ShiftApplication.instance.isInGame()
            button.text = "Я провёл ритуал"
        }
    }

    fun cancelScheduledUpdates() {
        button.removeCallbacks(tick)
    }

    companion object {
        private const val KEY_LAST_RITUAL = "last_ritual_time"
        private const val RITUAL_COOLDOWN_MS = 30 * 60 * 1000L // 30 минут
    }
}
