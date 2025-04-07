package bas.app.shift.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.os.Looper
import bas.app.shift.helpers.LogHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.reactivex.subjects.BehaviorSubject

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
        
        // Проверяем состояние игры при создании сервиса
        val isInGame = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(KEY_IN_GAME, false)
        if (isInGame) {
            startLocationUpdates()
        }
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
    }

    private fun stopLocationUpdates() {
        if (!isActive) return
        
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        isActive = false
        LogHelper.d("Обновление геолокации остановлено")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        LogHelper.d("LocationService уничтожен")
    }

    companion object {
        const val ACTION_START = "bas.app.shift.ACTION_START_LOCATION"
        const val ACTION_STOP = "bas.app.shift.ACTION_STOP_LOCATION"
        private const val PREFS_NAME = "game_state"
        private const val KEY_IN_GAME = "is_in_game"
        public var locationSource: BehaviorSubject<Location> = BehaviorSubject.create()
    }
} 