package bas.app.shift

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import bas.app.shift.databinding.ActivityEkatMapsBinding
import bas.app.shift.models.PointOfInterest
import bas.app.shift.models.PointType
import bas.app.shift.services.LocationService
import bas.app.shift.services.ServerService
import bas.app.shift.utils.PointVisualizer
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import io.reactivex.disposables.Disposable

class EkatMaps : AppCompatActivity(), OnMapReadyCallback {

    private var locationUpdateDisposable: Disposable? = null
    private lateinit var updatePointsRunnable: Runnable
    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityEkatMapsBinding
    private var cancellationTokenSource = CancellationTokenSource()
    private var currentLocationMarker: Marker? = null
    private val pointsOfInterest = mutableMapOf<String, Triple<PointOfInterest, Circle, Marker?>>()
    private val handler = Handler(Looper.getMainLooper())
    private val pointsUpdateInterval = 60000L // 1 минута
    private var lastPointsUpdate = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Проверяем состояние игры
        val isInGame = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
            .getBoolean(MainActivity.KEY_IN_GAME, false)
        if (!isInGame) {
            Toast.makeText(this, "Персонаж не в игре", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding = ActivityEkatMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
    }

    override fun onResume() {
        super.onResume()
        
        // Проверяем состояние игры
        val isInGame = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
            .getBoolean(MainActivity.KEY_IN_GAME, false)
        if (!isInGame) {
            Toast.makeText(this, "Персонаж не в игре", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updatePointsRunnable)
        locationUpdateDisposable?.dispose()
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setIndoorEnabled(false)
        mMap.isTrafficEnabled = false
        mMap.setMinZoomPreference(13.0f)
        
        // Включаем отображение зданий и улиц
        mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isCompassEnabled = true
        mMap.uiSettings.isMapToolbarEnabled = true
        mMap.uiSettings.isTiltGesturesEnabled = true
        mMap.uiSettings.isRotateGesturesEnabled = true
        mMap.uiSettings.isScrollGesturesEnabled = true
        mMap.uiSettings.isZoomGesturesEnabled = true

        // Запускаем периодическое обновление точек
        startPointsUpdate()
        
        requestForLocation()
    }

    private val fusedLocationProviderClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(applicationContext)
    }

    private fun startPointsUpdate() {
        updatePointsRunnable = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastPointsUpdate >= pointsUpdateInterval) {
                    updatePointsFromServer()
                    lastPointsUpdate = currentTime
                }
                handler.postDelayed(this, pointsUpdateInterval)
            }
        }
        handler.post(updatePointsRunnable)
    }

    private fun updatePointsFromServer() {
        val serverPoints = ServerService.getPointsOfInterest()
        if (serverPoints.isEmpty()) {
            // Если сервер не вернул точки, используем тестовые
            addTestPoints()
        } else {
            // Удаляем все существующие точки
            pointsOfInterest.values.forEach { (_, circle, marker) ->
                circle.remove()
                marker?.remove()
            }
            pointsOfInterest.clear()

            // Добавляем новые точки с сервера
            serverPoints.forEach { point ->
                addPointOfInterest(point)
            }
        }
    }

    // Добавляем тестовые точки разных типов
    val testPoints = listOf(
        PointVisualizer.createPointOfInterest("1", LatLng(56.840527, 60.652171), 500f, PointType.FAMILIAR),
        PointVisualizer.createPointOfInterest("2", LatLng(56.837609, 60.633470), 300f, PointType.FAKE_FAMILIAR),
        PointVisualizer.createPointOfInterest("3", LatLng(56.835325, 60.613837), 400f, PointType.OPEN_PROBLEM),
        PointVisualizer.createPointOfInterest("4", LatLng(56.834090, 60.599902), 600f, PointType.AGGRESSIVE_FAMILIAR),
        PointVisualizer.createPointOfInterest("5", LatLng(56.841096, 60.659535), 200f, PointType.HIDDEN_EFFECT),
        PointVisualizer.createPointOfInterest("666", LatLng(56.835325, 60.613837), 800f, PointType.HIDDEN_EFFECT),
        PointVisualizer.createPointOfInterest("6", LatLng(56.838011, 60.597465), 800f, PointType.SHRINKING_CIRCLE,
            mapOf("duration" to 30)) // 30 минут
    )

    private fun addTestPoints() {

        testPoints.forEach { point ->
            addPointOfInterest(point)
        }
    }

    private fun addPointOfInterest(point: PointOfInterest) {
        // Удаляем старую точку, если она существует
        pointsOfInterest[point.id]?.let { (_, circle, marker) ->
            circle.remove()
            marker?.remove()
        }
        pointsOfInterest.remove(point.id)

        // Создаем круг с виртуальным центром
        val circle = mMap.addCircle(
            PointVisualizer.getCircleOptions(point.virtualCenter, point.radius, point.type)
        )

        // Сохраняем точку, круг и null для маркера (он будет добавлен позже)
        pointsOfInterest[point.id] = Triple(point, circle, null)

        // Если это сужающийся круг, запускаем анимацию
        if (point.type == PointType.SHRINKING_CIRCLE) {
            val duration = point.additionalInfo["duration"] as? Int ?: 30
            startShrinkingAnimation(point.id, duration)
        }
    }

    private fun startShrinkingAnimation(pointId: String, durationMinutes: Int) {
        val point = pointsOfInterest[pointId]?.first ?: return
        val circle = pointsOfInterest[pointId]?.second ?: return
        
        val startRadius = point.radius
        val endRadius = 0f
        val durationMillis = durationMinutes * 60 * 1000L
        val startTime = System.currentTimeMillis()

        /*updateShrinkingCircleRunnable = object : Runnable {
            override fun run() { TODO
                val currentTime = System.currentTimeMillis()
                val elapsed = currentTime - startTime
                val progress = (elapsed.toFloat() / durationMillis).coerceIn(0f, 1f)
                
                val currentRadius = startRadius * (1 - progress)
                circle.radius = currentRadius.toDouble()


                if (progress < 1f) {
                    handler.postDelayed(this, 100) // Обновляем каждые 100мс
                } else {
                    // Удаляем точку после завершения анимации
                    circle.remove()
                    pointsOfInterest[pointId]?.third?.remove() // Удаляем маркер
                    pointsOfInterest.remove(pointId)
                }
            }
        }

        handler.post(updateShrinkingCircleRunnable)*/
    }

    private fun updateForLocation(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        
        // Удаляем предыдущий маркер, если он существует
        currentLocationMarker?.remove()
        
        // Создаем новый маркер для текущего местоположения (синий)
        currentLocationMarker = mMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Ваше местоположение")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        )

        // Проверяем, находится ли пользователь в каких-либо кругах
        pointsOfInterest.forEach { (id, pointData) ->
            val (point, circle, currentMarker) = pointData
            val distance = calculateDistance(latLng, point.virtualCenter)
            
            if (distance <= point.radius) {
                // Если пользователь в круге и маркера еще нет - создаем его
                if (currentMarker == null) {
                    val newMarker = mMap.addMarker(
                        PointVisualizer.getMarkerOptions(
                            point.position,
                            point.type,
                            getPointTitle(point.type),
                            getPointDescription(point)
                        )
                    )
                    pointsOfInterest[id] = Triple(point, circle, newMarker)

                    // Если это скрытый эффект, уведомляем сервер
                    if (point.type == PointType.HIDDEN_EFFECT) {
                        ServerService.notifyHiddenEffectEnter(point.id, point.position)
                    }
                }
            } else {
                // Если пользователь вне круга и маркер существует - удаляем его
                if (currentMarker != null) {
                    currentMarker.remove()
                    pointsOfInterest[id] = Triple(point, circle, null)

                    // Если это скрытый эффект, уведомляем сервер
                    if (point.type == PointType.HIDDEN_EFFECT) {
                        ServerService.notifyHiddenEffectExit(point.id, point.position)
                    }
                }
            }
        }
        
        Log.d("Location", "Location is ${location.latitude}, ${location.longitude}")
    }

    private fun getPointTitle(type: PointType): String {
        return when (type) {
            PointType.FAMILIAR -> "Фамильяр"
            PointType.FAKE_FAMILIAR -> "Поддельный Фамильяр"
            PointType.OPEN_PROBLEM -> "Открытая Проблема"
            PointType.AGGRESSIVE_FAMILIAR -> "Агрессивный Фамильяр"
            PointType.HIDDEN_EFFECT -> "Скрытый Эффект"
            PointType.SHRINKING_CIRCLE -> "Сужающийся Круг"
        }
    }

    private fun getPointDescription(point: PointOfInterest): String {
        return when (point.type) {
            PointType.SHRINKING_CIRCLE -> {
                val duration = point.additionalInfo["duration"] as? Int ?: 30
                "Радиус: ${point.radius}м\nДлительность: $duration мин"
            }
            else -> "Радиус: ${point.radius}м"
        }
    }

    private fun calculateDistance(point1: LatLng, point2: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            point1.latitude, point1.longitude,
            point2.latitude, point2.longitude,
            results
        )
        return results[0]
    }

    private fun requestForLocation() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                requestCurrentLocation()
            }
            /*ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.REQUESTED_PERMISSION) -> {
                // In an educational UI, explain to the user why your app requires this
                // permission for a specific feature to behave as expected, and what
                // features are disabled if it's declined. In this UI, include a
                // "cancel" or "no thanks" button that lets the user continue
                // using your app without granting the permission.
                showInContextUI(...)
            }*/
            else -> {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                requestPermissions(
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_CODE_LOCATION_PERMISSION
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestCurrentLocation() {
        val currentTask: Task<Location> = fusedLocationProviderClient.getCurrentLocation(
            PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token
        )

        currentTask.addOnCompleteListener { task ->
            if (task.isSuccessful && task.result != null) {
                val result = task.result
                val latLng = LatLng(result.latitude, result.longitude)
                // Центрируем карту на текущем местоположении
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                updateForLocation(result)
            } else Log.e("Location", task.exception.toString())
        }

        locationUpdateDisposable = LocationService.locationSource.subscribe {
            updateForLocation(it)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_LOCATION_PERMISSION && grantResults.isNotEmpty()) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) requestCurrentLocation()
        }
    }

    companion object {
        private const val REQUEST_CODE_LOCATION_PERMISSION = 100
    }
}