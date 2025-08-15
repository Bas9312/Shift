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
import bas.app.shift.helpers.LogHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.reactivex.subjects.BehaviorSubject
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class LocationService : Service() {
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private val locationUpdateInterval = 30000L // 30 секунд
    private var lastLocationUpdate = 0L
    private var isActive = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                val currentTime = System.currentTimeMillis()
                LogHelper.d("LocationService update location $location")
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
        when (intent?.action) {
            ACTION_START -> startLocationUpdates()
            ACTION_STOP -> stopLocationUpdates()
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (isActive) return
        
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
            isActive = true
            LogHelper.d("Обновление геолокации запущено")
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
        if (!isActive) return
        
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        isActive = false
        
        // Останавливаем Foreground Service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true)
        }
        
        LogHelper.d("Обновление геолокации остановлено")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Сервис отслеживания геолокации"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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
        private const val NOTIFICATION_ID = 1001
        public var locationSource: BehaviorSubject<Location> = BehaviorSubject.create()
    }
} 