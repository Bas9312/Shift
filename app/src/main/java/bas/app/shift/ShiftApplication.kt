package bas.app.shift

import android.app.Application
import android.app.ActivityManager
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModelProvider.NewInstanceFactory.Companion.instance
import bas.app.shift.MainActivity.Companion.KEY_IN_GAME
import bas.app.shift.MainActivity.Companion.PREFS_NAME
import bas.app.shift.helpers.AndroidStandardLogger
import bas.app.shift.helpers.LogHelper
import bas.app.shift.services.LocationService

class ShiftApplication : Application() {

    fun isInGame(): Boolean = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        .getBoolean(KEY_IN_GAME, true)

    fun setIsInGame(isInGame: Boolean) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IN_GAME, isInGame)
            .apply()
    }

    companion object {
        @JvmStatic
        lateinit var instance: ShiftApplication
            private set
    }

    init {
        instance = this
    }

    fun startLocationService() {
        // Проверяем, не запущен ли уже сервис
        if (!isLocationServiceRunning()) {
            // Проверяем, что приложение не в фоне
            if (isAppInForeground()) {
                startService(Intent(this, LocationService::class.java).apply {
                    action = LocationService.ACTION_START
                })
            } else {
                LogHelper.w("ShiftApplication: Не удается запустить LocationService - приложение в фоне")
            }
        }
    }

    fun stopLocationService() {
        if (isLocationServiceRunning()) {
            startService(Intent(this, LocationService::class.java).apply {
                action = LocationService.ACTION_STOP
            })
        }
    }

    fun isLocationServiceRunning(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
        
        for (service in runningServices) {
            if (LocationService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses
        
        if (appProcesses == null) return false
        
        val packageName = packageName
        for (appProcess in appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                appProcess.processName == packageName) {
                return true
            }
        }
        return false
    }

    override fun onCreate() {
        super.onCreate()
        LogHelper.addLogger(AndroidStandardLogger())
        LogHelper.d("onCreate - LocationService не запускается автоматически")
        // Убираем автоматический запуск LocationService
        // Сервис будет запускаться только когда пользователь явно включит режим "в игре"
    }
}