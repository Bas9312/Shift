package bas.app.shift

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import bas.app.shift.helpers.LogHelper

/**
 * Последовательный запрос разрешений при старте MainActivity: сначала уведомления (Android 13+),
 * затем геолокация. Вынесено из MainActivity механическим переносом, без изменения логики.
 */
class MainActivityPermissions(private val activity: MainActivity) {

    fun checkPermissionsSequentially() {
        LogHelper.d("MainActivity: Начинаем последовательную проверку разрешений")
        // Сначала проверяем разрешение на уведомления
        if (!hasNotificationPermission()) {
            LogHelper.d("MainActivity: Запрашиваем разрешение на уведомления")
            requestNotificationPermission()
        } else {
            LogHelper.d("MainActivity: Разрешение на уведомления уже есть, проверяем местоположение")
            // Если разрешение на уведомления уже есть, проверяем местоположение
            checkLocationPermission()
        }
    }

    private fun hasNotificationPermission(): Boolean {
        // POST_NOTIFICATIONS появился только в Android 13 (API 33). На более старых версиях
        // разрешение выдаётся автоматически, отдельного runtime-запроса нет — считаем выданным.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotificationPermission() {
        // На Android < 13 запрашивать нечего — сразу переходим к геолокации.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            LogHelper.d("MainActivity: API<33, POST_NOTIFICATIONS не требуется, проверяем геолокацию")
            checkLocationPermission()
            return
        }
        LogHelper.d("MainActivity: Отправляем запрос на разрешение уведомлений")
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATION_PERMISSION
        )
    }

    fun checkLocationPermission() {
        if (!hasLocationPermission()) {
            LogHelper.d("MainActivity: Запрашиваем разрешение на местоположение")
            requestLocationPermission()
        } else {
            LogHelper.d("MainActivity: Разрешение на местоположение уже есть")
        }
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestLocationPermission() {
        LogHelper.d("MainActivity: Отправляем запрос на разрешение местоположения")
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            REQUEST_LOCATION_PERMISSION
        )
    }

    /** Обрабатывает результат запроса разрешений; true если requestCode относился к этому флоу. */
    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray): Boolean {
        when (requestCode) {
            REQUEST_NOTIFICATION_PERMISSION -> {
                LogHelper.d("MainActivity: Получен результат запроса разрешения на уведомления")
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    LogHelper.d("MainActivity: Разрешение на уведомления получено")
                    activity.updateUI()
                    Toast.makeText(activity, "Разрешение на уведомления получено", Toast.LENGTH_SHORT).show()
                    // После получения разрешения на уведомления, запрашиваем разрешение на геолокацию с небольшой задержкой
                    LogHelper.d("MainActivity: Планируем запрос разрешения на местоположение через 1 секунду")
                    activity.scheduleOnRoot(1000) { checkLocationPermission() }
                } else {
                    LogHelper.w("MainActivity: Пользователь отказал в разрешении на уведомления")
                    Toast.makeText(activity, "Для полноценной работы приложения требуется разрешение на уведомления", Toast.LENGTH_LONG).show()
                    // Даже если пользователь отказал в уведомлениях, все равно запрашиваем местоположение с задержкой
                    LogHelper.d("MainActivity: Планируем запрос разрешения на местоположение через 1 секунду (после отказа в уведомлениях)")
                    activity.scheduleOnRoot(1000) { checkLocationPermission() }
                }
                return true
            }
            REQUEST_LOCATION_PERMISSION -> {
                LogHelper.d("MainActivity: Получен результат запроса разрешения на местоположение")
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    LogHelper.d("MainActivity: Разрешение на местоположение получено")
                    activity.updateUI()
                    Toast.makeText(activity, "Разрешение на геолокацию получено", Toast.LENGTH_SHORT).show()
                    // Проверяем, нужно ли запустить LocationService
                    activity.checkAndStartLocationService()
                } else {
                    LogHelper.w("MainActivity: Пользователь отказал в разрешении на местоположение")
                    Toast.makeText(activity, "Для полноценной работы приложения требуется разрешение на геолокацию", Toast.LENGTH_LONG).show()
                }
                return true
            }
            else -> return false
        }
    }

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 100
        private const val REQUEST_LOCATION_PERMISSION = 101
    }
}
