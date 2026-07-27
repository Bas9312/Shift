package bas.app.shift.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import bas.app.shift.helpers.LogHelper
import bas.app.shift.helpers.ProfileDiffer
import bas.app.shift.models.Point
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.os.Handler
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.api.RetrofitClient
import bas.app.shift.models.User
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocationService : Service() {
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private val locationUpdateInterval = 30000L // 30 секунд
    private val pointsCheckInterval = 30000L // 30 секунд для проверки точек
    private val profileUpdateInterval = 60000L // 1 минута для обновления профиля
    private val messagesCheckInterval = 60000L // 30 секунд для проверки сообщений
    private var lastLocationUpdate = 0L
    private var lastPointsCheck = 0L
    private var lastProfileUpdate = 0L
    private var lastMessagesCheck = 0L
    private var isActive = false
    private var currentLocation: Location? = null
    private var pointsInRange = mutableSetOf<String>() // Точки, в которых мы находимся
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notifications: LocationNotifications
    private lateinit var messagesChecker: NewMessagesChecker
    private val pointsCheckRunnable = object : Runnable {
        override fun run() {
            if (isActive) {
                checkPointsInRange()
                handler.postDelayed(this, pointsCheckInterval)
            } else {
                LogHelper.d("LocationService: Проверка точек остановлена, не планируем следующую")
            }
        }
    }

    private val profileUpdateRunnable = object : Runnable {
        override fun run() {
            if (isActive) {
                updateProfile()
                handler.postDelayed(this, profileUpdateInterval)
            } else {
                LogHelper.d("LocationService: Обновление профиля остановлено, не планируем следующую")
            }
        }
    }

    private val messagesCheckRunnable = object : Runnable {
        override fun run() {
            if (isActive) {
                checkForNewMessages()
                handler.postDelayed(this, messagesCheckInterval)
            } else {
                LogHelper.d("LocationService: Проверка сообщений остановлена, не планируем следующую")
            }
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                val currentTime = System.currentTimeMillis()
                currentLocation = location
                //LogHelper.d("LocationService: Обновление локации: ${location.latitude}, ${location.longitude}")
                if (currentTime - lastLocationUpdate >= locationUpdateInterval) {
                    ServerService.sendLocation(location)
                    lastLocationUpdate = currentTime
                }
                _locationSource.value = location
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(applicationContext)
        notifications = LocationNotifications(this)
        messagesChecker = NewMessagesChecker(this) { unreadCount, isMG ->
            notifications.showMessagesNotification(unreadCount, isMG)
        }
        LogHelper.d("LocationService создан")

        // Создаем канал уведомлений для Android 8.0+
        notifications.createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogHelper.d("LocationService: Получена команда: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> {
                LogHelper.d("LocationService: Запуск обновлений локации")
                startLocationUpdates()
            }
            ACTION_STOP -> {
                LogHelper.d("LocationService: Остановка обновлений локации")
                stopLocationUpdates()
            }
            else -> {
                LogHelper.d("LocationService: Неизвестная команда, запускаем обновления")
                startLocationUpdates()
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (isActive) {
            LogHelper.d("LocationService: Обновления локации уже активны, пропускаем")
            return
        }
        
        LogHelper.d("LocationService: Начинаем запуск обновлений локации")
        
        // Проверяем разрешения на геолокацию
        if (!hasLocationPermission()) {
            LogHelper.e("LocationService: Нет разрешений на геолокацию")
            stopSelf()
            return
        }
        
        try {
            // Запускаем как Foreground Service для Android 8.0+
            startForeground(LocationNotifications.NOTIFICATION_ID, notifications.buildServiceNotification())

            val locationRequest = LocationRequest.Builder(10000)
                .setMinUpdateIntervalMillis(10000)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .build()

            fusedLocationProviderClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            
            // Запускаем проверку точек
            handler.post(pointsCheckRunnable)
            
            // Запускаем обновление профиля
            handler.post(profileUpdateRunnable)
            
            // Запускаем проверку сообщений
            handler.post(messagesCheckRunnable)
            
            isActive = true
            LogHelper.d("Обновление геолокации, проверка точек и обновление профиля запущены")
        } catch (e: Exception) {
            LogHelper.e("Ошибка при запуске обновления геолокации: ${e.message}")
            // Если не удалось запустить как Foreground Service, останавливаем
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                stopForeground(true)
            }
            stopSelf()
        }
    }

    private fun stopLocationUpdates() {
        if (!isActive) {
            LogHelper.d("LocationService: Обновления локации уже остановлены, пропускаем")
            return
        }
        
        LogHelper.d("LocationService: Останавливаем обновления локации")
        
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        handler.removeCallbacks(pointsCheckRunnable)
        handler.removeCallbacks(profileUpdateRunnable)
        handler.removeCallbacks(messagesCheckRunnable)
        isActive = false
        
        // Останавливаем Foreground Service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true)
        }
        
        LogHelper.d("LocationService: Обновление геолокации, проверка точек и обновление профиля остановлены")
    }

    private fun checkPointsInRange() {
        val location = currentLocation ?: return
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastPointsCheck < pointsCheckInterval) return
        lastPointsCheck = currentTime
        
        //LogHelper.d("LocationService: Проверяем точки в радиусе, текущая локация: ${location.latitude}, ${location.longitude}")
        
        serviceScope.launch {
            try {
                // null = сетевой сбой: пропускаем цикл, сохраняя текущие pointsInRange.
                // Иначе разовый обрыв «вывел» бы игрока из всех зон и сыпал ложными
                // уведомлениями о входе/выходе на следующем успешном запросе.
                val points = ServerService.getPoints() ?: return@launch
                val newPointsInRange = mutableSetOf<String>()

                points.forEach { point ->
                    // Для обычных пользователей: пропускаем точки типа POINT_WITH_TEXT
                    
                    val distance = calculateDistance(
                        location.latitude, location.longitude,
                        point.lat, point.lng
                    )
                    
                    // Для фамильяров используем расстояние 30 метров вместо радиуса точки
                    val checkDistance = if (point.type == "FAMILIAR") 50.0 else point.radius
                    
                    if (distance <= checkDistance) {
                        newPointsInRange.add(point.pointId)
                        
                        // Если мы только что вошли в точку
                        if (!pointsInRange.contains(point.pointId)) {
                            onEnterPoint(point)
                        }
                    }
                }
                
                // Проверяем точки, из которых мы вышли
                val exitedPoints = pointsInRange - newPointsInRange
                exitedPoints.forEach { pointId ->
                    onExitPoint(pointId)
                }
                
                pointsInRange = newPointsInRange
                
            } catch (e: Exception) {
                LogHelper.e("Ошибка при проверке точек: ${e.message}")
            }
        }
    }
    
    private fun onEnterPoint(point: Point) {
        LogHelper.d("Вход в точку: ${point.pointId}, тип: ${point.type}")
        
        when (point.type) {
            "HIDDEN_EFFECT_AREA" -> {
                notifications.showPointNotification(
                    "⚠️ Неприятный эффект!",
                    point.textToShowOnEnter ?: "Почему-то тут нет текста, обратитесь к МГ!",
                    point.pointId.hashCode()
                )
            }
            "FAMILIAR" -> {
                // Для фамильяров показываем специальное уведомление
                notifications.showFamiliarNotification(point)
            }
            "POINT_WITH_TEXT" -> {
                // Для точек с текстом используем специальный заголовок
                point.textToShowOnEnter?.let { text ->
                    if (text.isNotEmpty()) {
                        notifications.showPointNotification(
                            "📍 Важная информация",
                            text,
                            point.pointId.hashCode()
                        )
                    }
                }
            }
            else -> {
                // Проверяем, есть ли текст для показа при входе для других типов точек
                point.textToShowOnEnter?.let { text ->
                    if (text.isNotEmpty()) {
                        val description = point.description ?: "Точка на карте"
                        notifications.showPointNotification(
                            "📍 $description",
                            text,
                            point.pointId.hashCode()
                        )
                    }
                }
            }
        }
    }

    private fun onExitPoint(pointId: String) {
        LogHelper.d("Выход из точки: $pointId")
        notifications.cancelPointNotification(pointId)
    }

    private fun updateProfile() {
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastProfileUpdate < profileUpdateInterval) return
        lastProfileUpdate = currentTime
        
        val userId = UserPrefsHelper.getUserId(this)
        if (userId.isNullOrEmpty()) {
            LogHelper.w("LocationService: ID пользователя не найден для обновления профиля")
            return
        }
        
        // Проверяем, не является ли пользователь MG
        if (userId.startsWith("MG", ignoreCase = true)) {
            LogHelper.d("LocationService: Пользователь MG, пропускаем обновление профиля")
            return
        }
        
        LogHelper.d("LocationService: Обновляем профиль для пользователя: $userId")
        
        RetrofitClient.userProfileApi.getUserProfile(userId)
            .enqueue(object : Callback<User> {
                override fun onResponse(call: Call<User>, response: Response<User>) {
                    if (response.isSuccessful && response.body() != null) {
                        val userServer = response.body()!!
                        val oldProfile = UserPrefsHelper.getUserData(this@LocationService)
                        
                        if (oldProfile != null) {
                            val changes = ProfileDiffer.diff(oldProfile, userServer)
                            if (changes.isNotEmpty()) {
                                LogHelper.d("LocationService: Обнаружены изменения в профиле: ${changes.size}")
                                notifications.showProfileChangeNotifications(changes)
                            }
                        }
                        
                        // Сохраняем новый профиль
                        UserPrefsHelper.saveUserData(this@LocationService, userServer)
                        LogHelper.d("LocationService: Профиль обновлен и сохранен")
                    } else {
                        LogHelper.e("LocationService: Ошибка загрузки профиля: ${response.code()}")
                    }
                }
                
                override fun onFailure(call: Call<User>, t: Throwable) {
                    LogHelper.e("LocationService: Ошибка сети при загрузке профиля: ${t.localizedMessage}")
                }
            })
    }
    
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Радиус Земли в метрах
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopLocationUpdates()
        serviceScope.cancel()
        LogHelper.d("LocationService уничтожен")
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkForNewMessages() {
        val userId = UserPrefsHelper.getUserId(this@LocationService)
        if (userId.isNullOrEmpty()) return
        messagesChecker.check(userId)
    }

    companion object {
        const val ACTION_START = "bas.app.shift.ACTION_START_LOCATION"
        const val ACTION_STOP = "bas.app.shift.ACTION_STOP_LOCATION"
        private const val PREFS_NAME = "game_state"
        private const val KEY_IN_GAME = "is_in_game"
        private val _locationSource = MutableStateFlow<Location?>(null)
        val locationSource: StateFlow<Location?> = _locationSource.asStateFlow()
        
        private var instance: LocationService? = null
        
        fun getCurrentLocation(): Location? {
            return instance?.currentLocation
        }
    }
} 