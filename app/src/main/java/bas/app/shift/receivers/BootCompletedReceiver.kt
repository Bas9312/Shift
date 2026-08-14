package bas.app.shift.receivers

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import bas.app.shift.helpers.LogHelper
import bas.app.shift.services.LocationService

/**
 * Поднимает [LocationService] обратно после перезагрузки телефона и после обновления
 * приложения (самообновление через установку APK убивает процесс).
 *
 * Раньше в обоих случаях фоновая механика молча переставала работать до тех пор, пока игрок
 * сам не откроет приложение. На выезде это значит «телефон разрядился и перезагрузился —
 * игрок больше не получает уведомлений о точках, и не знает об этом».
 *
 * Поднимаем только если персонаж реально в игре и разрешение на геолокацию есть — иначе
 * сервис всё равно остановил бы сам себя.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        LogHelper.d("BootCompletedReceiver: получено $action")

        if (!isInGame(context)) {
            LogHelper.d("BootCompletedReceiver: персонаж не в игре, сервис не поднимаем")
            return
        }
        if (!hasLocationPermission(context)) {
            LogHelper.w("BootCompletedReceiver: нет разрешения на геолокацию, сервис не поднимаем")
            return
        }

        try {
            val serviceIntent = Intent(context, LocationService::class.java).apply {
                this.action = LocationService.ACTION_START
            }
            ContextCompat.startForegroundService(context, serviceIntent)
            LogHelper.d("BootCompletedReceiver: LocationService поднят")
        } catch (e: Exception) {
            LogHelper.e("BootCompletedReceiver: не удалось поднять сервис: ${e.message}")
        }
    }

    private fun isInGame(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_IN_GAME, false)

    private fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val PREFS_NAME = "game_state"
        private const val KEY_IN_GAME = "is_in_game"
    }
}
