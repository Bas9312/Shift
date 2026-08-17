package bas.app.shift

import android.app.Application
import android.app.ActivityManager
import android.content.Context.MODE_PRIVATE
import android.content.Intent

import androidx.lifecycle.ViewModelProvider.NewInstanceFactory.Companion.instance
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import bas.app.shift.MainActivity.Companion.KEY_IN_GAME
import bas.app.shift.MainActivity.Companion.PREFS_NAME
import bas.app.shift.helpers.AndroidStandardLogger
import bas.app.shift.helpers.BugfenderLogger
import bas.app.shift.helpers.FamiliarCatalog
import bas.app.shift.helpers.FamiliarImages
import bas.app.shift.helpers.LogHelper
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.services.LocationService
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import com.bugfender.sdk.Bugfender
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import java.io.File

class ShiftApplication : Application(), DefaultLifecycleObserver, SingletonImageLoader.Factory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Единый загрузчик картинок на всё приложение.
     *
     * Главное здесь — диск-кеш в filesDir, а не в cacheDir: систему никто не обязывает
     * хранить cacheDir, она чистит его под нехватку места. Игрок на выезде без сети должен
     * видеть фамильяра и ауру, а не пустой экран. Размер ограничиваем сами.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .diskCache {
                DiskCache.Builder()
                    .directory(File(filesDir, "image_cache").toOkioPath())
                    .maxSizeBytes(IMAGE_CACHE_BYTES)
                    .build()
            }
            .build()

    fun isInGame(): Boolean = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        .getBoolean(KEY_IN_GAME, true)

    fun setIsInGame(isInGame: Boolean) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IN_GAME, isInGame)
            .apply()
    }

    companion object {
        private const val IMAGE_CACHE_BYTES = 128L * 1024 * 1024

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
        super<Application>.onCreate()
        LogHelper.setLogLevel(LogHelper.LogLevel.DEBUG)
        // AndroidStandardLogger намеренно выключен: SDK Bugfender сам дублирует всё в
        // logcat с префиксом "BF/", поэтому отладка через `adb logcat` работает и так,
        // а второй логгер просто удваивал бы каждую строку.
        //   adb logcat | grep BF/
        //LogHelper.addLogger(AndroidStandardLogger())
        LogHelper.addLogger(BugfenderLogger())
        Bugfender.init(this, "jrdTZKyAg4q91SOxfYvaUFszBhvNihH5", true, true)
        Bugfender.setDeviceString("user id", UserPrefsHelper.getUserId(this))
        LogHelper.d("onCreate - настройка жизненного цикла приложения")
        
        // Регистрируем наблюдатель жизненного цикла
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        FirebaseCrashlytics.getInstance().setUserId(UserPrefsHelper.getUserId(this));                // correct

        // Каталог фамильяров: сначала локальный (файл или бутстрап из assets), чтобы имена
        // были доступны сразу, потом фоновое обновление с сервера и предзагрузка картинок
        // своего фамильяра — на выезде сети может не быть.
        FamiliarCatalog.loadLocal(this)
        appScope.launch {
            if (FamiliarCatalog.refresh(this@ShiftApplication)) {
                val familiar = UserPrefsHelper.getUserData(this@ShiftApplication)?.familiar
                FamiliarImages.prefetch(this@ShiftApplication, familiar)
            }
        }
        
        // НЕ запускаем LocationService автоматически на Android 15+
        // Сервис будет запускаться только при активации приложения
    }
    
    override fun onStart(owner: LifecycleOwner) {
        LogHelper.d("ShiftApplication: Приложение перешло в передний план")
        // Приложение активировано, можно попробовать запустить сервис
        if (isInGame()) {
            LogHelper.d("ShiftApplication: Персонаж в игре, пытаемся запустить LocationService")
            startLocationService()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        LogHelper.d("ShiftApplication: Приложение перешло в фон")
        // Приложение ушло в фон, но сервис продолжает работать
        // так как он Foreground Service
    }
}