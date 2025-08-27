package bas.app.shift.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import bas.app.shift.MainActivity
import bas.app.shift.R
import bas.app.shift.ShiftApplication
import bas.app.shift.helpers.LogHelper
import bas.app.shift.models.Point
import bas.app.shift.models.FamiliarData
import bas.app.shift.ui.FamiliarFoundActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.reactivex.subjects.BehaviorSubject
import android.Manifest
import android.content.pm.PackageManager
import android.media.session.PlaybackState.ACTION_STOP
import androidx.core.content.ContextCompat
import android.os.Handler
import bas.app.shift.helpers.UserPrefsHelper

class LocationService : Service() {
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private val locationUpdateInterval = 30000L // 30 секунд
    private val pointsCheckInterval = 10000L // 10 секунд для проверки точек
    private var lastLocationUpdate = 0L
    private var lastPointsCheck = 0L
    private var isActive = false
    private var currentLocation: Location? = null
    private var pointsInRange = mutableSetOf<String>() // Точки, в которых мы находимся
    private val handler = Handler(Looper.getMainLooper())
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

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                val currentTime = System.currentTimeMillis()
                currentLocation = location
                LogHelper.d("LocationService: Обновление локации: ${location.latitude}, ${location.longitude}")
                if (currentTime - lastLocationUpdate >= locationUpdateInterval) {
                    ServerService.sendLocation(location)
                    lastLocationUpdate = currentTime
                }
                locationSource.onNext(location)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(applicationContext)
        LogHelper.d("LocationService создан")
        
        // Создаем канал уведомлений для Android 8.0+
        createNotificationChannel()
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForeground(NOTIFICATION_ID, createNotification())
            }
            
            val locationRequest = LocationRequest.Builder(10000)
                .setMinUpdateIntervalMillis(5000)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .build()

            fusedLocationProviderClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            
            // Запускаем проверку точек
            handler.post(pointsCheckRunnable)
            
            isActive = true
            LogHelper.d("Обновление геолокации и проверка точек запущены")
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
        isActive = false
        
        // Останавливаем Foreground Service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true)
        }
        
        LogHelper.d("LocationService: Обновление геолокации и проверка точек остановлены")
    }

    private fun checkPointsInRange() {
        val location = currentLocation ?: return
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastPointsCheck < pointsCheckInterval) return
        lastPointsCheck = currentTime
        
        LogHelper.d("LocationService: Проверяем точки в радиусе, текущая локация: ${location.latitude}, ${location.longitude}")
        
        try {
            val points = ServerService.getPoints()
            val newPointsInRange = mutableSetOf<String>()
            
            points.forEach { point ->
                // Для обычных пользователей: пропускаем точки типа POINT_WITH_TEXT
                
                val distance = calculateDistance(
                    location.latitude, location.longitude,
                    point.lat, point.lng
                )
                
                // Для фамильяров используем расстояние 30 метров вместо радиуса точки
                val checkDistance = if (point.type == "FAMILIAR") 30.0 else point.radius
                
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
    
    private fun onEnterPoint(point: Point) {
        LogHelper.d("Вход в точку: ${point.pointId}, тип: ${point.type}")
        
        when (point.type) {
            "HIDDEN_EFFECT_AREA" -> {
                showNotification(
                    "⚠️ Неприятный эффект!",
                    "Туду, пока не реализовано, но вы поймали неприятный эффект.\n\nВы находитесь в зоне скрытого воздействия. Будьте осторожны!",
                    point.pointId.hashCode()
                )
            }
            "FAMILIAR" -> {
                // Для фамильяров показываем специальное уведомление
                showFamiliarNotification(point)
            }
            "POINT_WITH_TEXT" -> {
                // Для точек с текстом используем специальный заголовок
                point.textToShowOnEnter?.let { text ->
                    if (text.isNotEmpty()) {
                        showNotification(
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
                        showNotification(
                            "📍 $description",
                            text,
                            point.pointId.hashCode()
                        )
                    }
                }
            }
        }
    }
    
    private fun showFamiliarNotification(point: Point) {
        // Создаем специальное уведомление для фамильяра
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Формируем детальное описание
        val familiarId = point.description ?: "familiar_malachite_lizard" // fallback на малахитовую ящерицу
        
        // Создаем Intent для открытия экрана найденного фамильяра
        val intent = Intent(this, bas.app.shift.ui.FamiliarFoundActivity::class.java)
        intent.putExtra("familiar_id", familiarId)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val familiarName = FamiliarData.getNameById(familiarId)
        val notificationText = "Вы чувствуете здесь присутствие $familiarName. " +
                "Это существо готово к общению. " +
                "Нажмите на уведомление, чтобы пообщаться с ним!"
        
        val notification = NotificationCompat.Builder(this, POINTS_CHANNEL_ID)
            .setContentTitle("🐉 Фамильяр рядом!")
            .setContentText("Вы чувствуете здесь $familiarName. Нажмите для общения!")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(notificationText)
                .setBigContentTitle("🐉 Фамильяр рядом!"))
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 500, 200, 500)) // Специальная вибрация для фамильяра
            .build()
        
        notificationManager.notify(point.pointId.hashCode(), notification)
        LogHelper.d("LocationService: Показано уведомление о фамильяре для точки ${point.pointId}: $familiarName")
    }
    
    private fun onExitPoint(pointId: String) {
        LogHelper.d("Выход из точки: $pointId")
        // Убираем уведомление для этой точки
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(pointId.hashCode())
    }
    
    private fun showNotification(title: String, text: String, notificationId: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Создаем стиль для большого текста
        val bigTextStyle = NotificationCompat.BigTextStyle()
            .bigText(text)
            .setBigContentTitle(title)
        
        val notification = NotificationCompat.Builder(this, POINTS_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text) // Краткий текст для свернутого состояния
            .setStyle(bigTextStyle) // Расширенный стиль для полного текста
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Звук, вибрация, свет
            .build()
        
        notificationManager.notify(notificationId, notification)
        LogHelper.d("LocationService: Показано уведомление для точки $notificationId: $title")
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Канал для основного сервиса
            val locationChannel = NotificationChannel(
                CHANNEL_ID,
                "Location Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Сервис отслеживания геолокации"
                setShowBadge(false)
            }
            
            // Канал для уведомлений о точках
            val pointsChannel = NotificationChannel(
                POINTS_CHANNEL_ID,
                "Уведомления о точках",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления при входе в игровые точки с важной информацией"
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
                setVibrationPattern(longArrayOf(0, 250, 250, 250)) // Короткая вибрация
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(locationChannel)
            notificationManager.createNotificationChannel(pointsChannel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Shift - Отслеживание геолокации")
            .setContentText("Сервис активен и отслеживает ваше местоположение")
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
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

    companion object {
        const val ACTION_START = "bas.app.shift.ACTION_START_LOCATION"
        const val ACTION_STOP = "bas.app.shift.ACTION_STOP_LOCATION"
        private const val PREFS_NAME = "game_state"
        private const val KEY_IN_GAME = "is_in_game"
        private const val CHANNEL_ID = "location_service_channel"
        private const val POINTS_CHANNEL_ID = "points_notifications_channel"
        private const val NOTIFICATION_ID = 1001
        public var locationSource: BehaviorSubject<Location> = BehaviorSubject.create()
    }
} 