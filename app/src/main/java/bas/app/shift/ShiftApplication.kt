package bas.app.shift

import android.app.Application
import android.app.ActivityManager
import android.content.Context.MODE_PRIVATE
import android.content.Intent

import androidx.lifecycle.ViewModelProvider.NewInstanceFactory.Companion.instance
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import bas.app.shift.MainActivity.Companion.KEY_IN_GAME
import bas.app.shift.MainActivity.Companion.PREFS_NAME
import bas.app.shift.helpers.AndroidStandardLogger
import bas.app.shift.helpers.BugfenderLogger
import bas.app.shift.helpers.LogHelper
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.services.LocationService
import com.bugfender.sdk.Bugfender

class ShiftApplication : Application(), LifecycleObserver {

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
        try {
            // Всегда отправляем команду START сервису
            // Сервис сам проверит, нужно ли ему активироваться
            startService(Intent(this, LocationService::class.java).apply {
                action = LocationService.ACTION_START
            })
            LogHelper.d("ShiftApplication: LocationService запущен успешно")
        } catch (e: Exception) {
            LogHelper.e("ShiftApplication: Ошибка при запуске LocationService: ${e.message}")
            // На Android 15+ сервис может не запуститься автоматически
            // Это нормально, он запустится позже при активации приложения
        }
    }

    fun stopLocationService() {
        try {
            // Отправляем команду STOP сервису
            startService(Intent(this, LocationService::class.java).apply {
                action = LocationService.ACTION_STOP
            })
            LogHelper.d("ShiftApplication: LocationService остановлен")
        } catch (e: Exception) {
            LogHelper.e("ShiftApplication: Ошибка при остановке LocationService: ${e.message}")
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
        LogHelper.setLogLevel(LogHelper.LogLevel.DEBUG)
        //LogHelper.addLogger(AndroidStandardLogger())
        LogHelper.addLogger(BugfenderLogger())
        Bugfender.init(this, "jrdTZKyAg4q91SOxfYvaUFszBhvNihH5", true, true)
        Bugfender.setDeviceString("user id", UserPrefsHelper.getUserId(this))
        LogHelper.d("onCreate - настройка жизненного цикла приложения")
        
        // Регистрируем наблюдатель жизненного цикла
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        
        // НЕ запускаем LocationService автоматически на Android 15+
        // Сервис будет запускаться только при активации приложения
    }
    
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppForegrounded() {
        LogHelper.d("ShiftApplication: Приложение перешло в передний план")
        // Приложение активировано, можно попробовать запустить сервис
        if (isInGame()) {
            LogHelper.d("ShiftApplication: Персонаж в игре, пытаемся запустить LocationService")
            startLocationService()
        }
    }
    
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onAppBackgrounded() {
        LogHelper.d("ShiftApplication: Приложение перешло в фон")
        // Приложение ушло в фон, но сервис продолжает работать
        // так как он Foreground Service
    }
}