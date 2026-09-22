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
import bas.app.shift.helpers.FamiliarCatalog
import bas.app.shift.helpers.FamiliarImages
import bas.app.shift.helpers.LogHelper
import bas.app.shift.helpers.NewRelicLogger
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.services.LocationService
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.newrelic.agent.android.FeatureFlag
import com.newrelic.agent.android.NewRelic
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

    // false — та же безопасная дефолтная семантика "ещё не решил", что и у остальных
    // читателей этого ключа (EkatMaps, LocationHeartbeatReceiver, BootCompletedReceiver).
    // Раньше был true: на самом первом запуске (пока is_in_game ни разу не записан)
    // MainActivity показывал переключатель уже включённым и пытался поднять
    // LocationService, хотя игрок ещё ни разу не нажимал "В игре".
    fun isInGame(): Boolean = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        .getBoolean(KEY_IN_GAME, false)

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
        // AndroidStandardLogger is back on: Bugfender used to mirror every line into logcat
        // itself (prefix "BF/"), so a second logger would have doubled each one. New Relic
        // does not mirror anything, so without this `adb logcat` would show nothing at all.
        LogHelper.addLogger(AndroidStandardLogger())
        LogHelper.addLogger(NewRelicLogger())
        // LogReporting is what makes NewRelic.logInfo()/logError() reach New Relic Logs.
        // It is already on by default in agent 7.8.2; stated explicitly so an agent upgrade
        // that changes the default does not silently stop shipping our logs. Must precede start().
        FeatureFlag.enableFeature(FeatureFlag.LogReporting)
        NewRelic.withApplicationToken(
            "eu01xa26df0283f11c861c13e80e409380634c0f68-NRMA"
        ).start(this.applicationContext)
        NewRelic.setUserId(UserPrefsHelper.getUserId(this))
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