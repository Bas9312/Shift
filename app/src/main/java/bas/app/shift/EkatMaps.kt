package bas.app.shift

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import bas.app.shift.databinding.ActivityEkatMapsBinding
import bas.app.shift.databinding.DialogCreatePointBinding
import bas.app.shift.databinding.DialogPointInfoBinding
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.models.Point
import bas.app.shift.models.PointRequest
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class EkatMaps : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var currentLocation: Location
    private var locationUpdateDisposable: Disposable? = null
    private lateinit var updatePointsRunnable: Runnable
    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityEkatMapsBinding
    private var cancellationTokenSource = CancellationTokenSource()
    private var currentLocationMarker: Marker? = null
    private val pointsOfInterest = mutableMapOf<String, Triple<Point, Circle?, Marker?>>()
    private val handler = Handler(Looper.getMainLooper())
    private val pointsUpdateInterval = 10000L // 1 минута
    private var lastPointsUpdate = 0L
    private var isMgUser = false
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("EkatMaps", "onCreate: запуск активности карты")

        // Проверяем состояние игры
        val isInGame = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
            .getBoolean(MainActivity.KEY_IN_GAME, false)
        if (!isInGame) {
            Log.w("EkatMaps", "onCreate: персонаж не в игре")
            Toast.makeText(this, "Персонаж не в игре", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Проверяем, является ли пользователь MG
        checkIfMgUser()
        
        Log.d("EkatMaps", "onCreate: карта доступна всем пользователям, ${if (isMgUser) "MG пользователь получает дополнительный функционал (лонг тапы)" else "обычный пользователь получает базовый функционал (просмотр точек)"}")
        binding = ActivityEkatMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            Log.d("EkatMaps", "Нажата кнопка назад")
            finish()
        }
        Log.d("EkatMaps", "onCreate: инициализация завершена для ${if (isMgUser) "MG" else "обычного"} пользователя")
    }

    override fun onResume() {
        super.onResume()
        Log.d("EkatMaps", "onResume: возобновление активности карты")
        
        // Проверяем состояние игры
        val isInGame = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
            .getBoolean(MainActivity.KEY_IN_GAME, false)
        if (!isInGame) {
            Log.w("EkatMaps", "onResume: персонаж не в игре")
            Toast.makeText(this, "Персонаж не в игре", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d("EkatMaps", "onResume: карта доступна всем пользователям, ${if (isMgUser) "MG пользователь получает дополнительный функционал (лонг тапы)" else "обычный пользователь получает базовый функционал (просмотр точек)"}")
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        Log.d("EkatMaps", "onResume: карта загружается асинхронно для ${if (isMgUser) "MG" else "обычного"} пользователя")
    }

    override fun onPause() {
        super.onPause()
        Log.d("EkatMaps", "onPause: приостановка активности карты")
        handler.removeCallbacks(updatePointsRunnable)
        locationUpdateDisposable?.dispose()
        Log.d("EkatMaps", "onPause: ресурсы освобождены")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("EkatMaps", "onDestroy: уничтожение активности карты")
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
        Log.d("EkatMaps", "Карта готова к использованию")
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

        // Добавляем обработчики событий для MG пользователей
        if (isMgUser) {
            Log.d("EkatMaps", "Настройка карты для MG пользователя с дополнительным функционалом (лонг тапы)")
            setupMgUserMapHandlers()
        } else {
            Log.d("EkatMaps", "Настройка карты для обычного пользователя (базовый функционал без лонг тапов)")
        }

        // Запускаем периодическое обновление точек
        startPointsUpdate()
        
        requestForLocation()
        Log.d("EkatMaps", "Инициализация карты завершена: ${if (isMgUser) "MG пользователь с полным функционалом" else "обычный пользователь с базовым функционалом"}")
    }

    private fun setupMgUserMapHandlers() {
        Log.d("EkatMaps", "Настройка дополнительных обработчиков событий для MG пользователя (лонг тапы)")
        
        // Обработчик лонг тапа по маркерам
        mMap.setOnMarkerClickListener { marker ->
            Log.d("EkatMaps", "MG пользователь: лонг тап по маркеру: ${marker.title}")
            showPointInfoDialog(marker)
            true
        }

        // Обработчик лонг тапа по карте
        mMap.setOnMapLongClickListener { latLng ->
            Log.d("EkatMaps", "MG пользователь: лонг тап по карте: ${latLng.latitude}, ${latLng.longitude}")
            showCreatePointDialog(latLng)
        }
        
        Log.d("EkatMaps", "Дополнительные обработчики событий для MG пользователя настроены (лонг тапы по маркерам и карте)")
    }

    private fun showPointInfoDialog(marker: Marker) {
        Log.d("EkatMaps", "Показ диалога информации о точке")
        
        // Находим точку по маркеру
        val pointData = pointsOfInterest.values.find { (_, _, markerRef) -> markerRef == marker }
        if (pointData == null) {
            Log.w("EkatMaps", "Точка не найдена для маркера: ${marker.title}")
            return
        }

        val (point, _, _) = pointData
        Log.d("EkatMaps", "Информация о точке: ID=${point.pointId}, тип=${point.type}")
        
        val dialogBinding = DialogPointInfoBinding.inflate(LayoutInflater.from(this))
        
        // Заполняем информацию о точке
        dialogBinding.tvPointTitle.text = getPointTitle(PointType.fromServerValue(point.type))
        dialogBinding.tvPointType.text = "Тип: ${point.type}"
        dialogBinding.tvPointRadius.text = "Радиус: ${point.radius}м"
        dialogBinding.tvPointCoordinates.text = "Координаты: ${String.format("%.6f", point.lat)}, ${String.format("%.6f", point.lng)}"
        dialogBinding.tvPointDescription.text = "Описание: ${point.description ?: "Нет описания"}"
        dialogBinding.tvPointTextToShowOnEnter.text = "Текст при входе: ${point.textToShowOnEnter ?: "Не задан"}"

        // Создаем диалог
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        // Обработчик кнопки удаления
        dialogBinding.btnDeletePoint.setOnClickListener {
            Log.d("EkatMaps", "Удаление точки: ${point.pointId}")
            scope.launch {
                try {
                    val response = ServerService.deletePoint(point.pointId)
                    if (response.isSuccessful) {
                        Toast.makeText(this@EkatMaps, "Точка удалена", Toast.LENGTH_SHORT).show()
                        Log.d("EkatMaps", "Точка успешно удалена")
                        // Обновляем карту
                        updatePointsFromServer()
                    } else {
                        Toast.makeText(this@EkatMaps, "Ошибка при удалении точки", Toast.LENGTH_SHORT).show()
                        Log.e("EkatMaps", "Ошибка при удалении точки: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@EkatMaps, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("EkatMaps", "Исключение при удалении точки", e)
                }
            }
            // Закрываем диалог
            dialog.dismiss()
        }

        Log.d("EkatMaps", "Диалог информации о точке показан")
        dialog.show()
    }

    private fun showCreatePointDialog(latLng: LatLng) {
        Log.d("EkatMaps", "Показ диалога создания точки: ${latLng.latitude}, ${latLng.longitude}")
        
        val dialogBinding = DialogCreatePointBinding.inflate(LayoutInflater.from(this))
        
        // Показываем координаты
        dialogBinding.tvCoordinates.text = "Координаты: ${String.format("%.6f", latLng.latitude)}, ${String.format("%.6f", latLng.longitude)}"
        
        // Настраиваем спиннер типов точек (исключаем USER)
        val pointTypes = PointType.values()
            .filter { it != PointType.USER }
            .map { it.serverValue }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, pointTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spinnerPointType.adapter = adapter
        
        // Показываем/скрываем поле "истечет через" в зависимости от выбранного типа
        dialogBinding.spinnerPointType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedType = pointTypes[position]
                Log.d("EkatMaps", "Выбран тип точки: $selectedType")
                if (selectedType == "SHRINKING_CIRCLE") {
                    dialogBinding.tvExpireLabel.visibility = View.VISIBLE
                    dialogBinding.etExpireMinutes.visibility = View.VISIBLE
                } else {
                    dialogBinding.tvExpireLabel.visibility = View.GONE
                    dialogBinding.etExpireMinutes.visibility = View.GONE
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Создаем диалог
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()
        
        // Обработчик кнопки создания
        dialogBinding.btnCreatePoint.setOnClickListener {
            val selectedType = dialogBinding.spinnerPointType.selectedItem as String
            val description = dialogBinding.etDescription.text.toString()
            val textToShowOnEnter = dialogBinding.etTextToShowOnEnter.text.toString()
            
            Log.d("EkatMaps", "Создание точки: тип=$selectedType, описание=$description, текст при входе=$textToShowOnEnter")
            
            if (description.isBlank()) {
                Toast.makeText(this, "Введите описание точки", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Для точек типа POINT_WITH_TEXT поле textToShowOnEnter обязательно
            if (selectedType == "POINT_WITH_TEXT" && textToShowOnEnter.isBlank()) {
                Toast.makeText(this, "Для точек типа 'Точка с текстом' обязательно заполните поле 'Текст при входе'", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            
            // Формируем expireAt если нужно
            var expireAt: String? = null
            if (selectedType == "SHRINKING_CIRCLE") {
                val expireMinutes = dialogBinding.etExpireMinutes.text.toString().toIntOrNull()
                if (expireMinutes != null && expireMinutes > 0) {
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.MINUTE, expireMinutes)
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    expireAt = dateFormat.format(calendar.time)
                    Log.d("EkatMaps", "Точка истечет через: $expireMinutes минут, expireAt: $expireAt")
                }
            }
            
            // Создаем точку
            scope.launch {
                try {
                    val userId = UserPrefsHelper.getUserId(this@EkatMaps)
                    Log.d("EkatMaps", "Создание точки для пользователя: $userId")
                    
                    val pointRequest = PointRequest(
                        lat = latLng.latitude,
                        lng = latLng.longitude,
                        pointId = generatePointId(),
                        type = selectedType,
                        radius = 0.0, // Сервер сам рассчитывает радиус
                        description = description,
                        ownerId = userId,
                        expireAt = expireAt,
                        textToShowOnEnter = textToShowOnEnter.takeIf { it.isNotEmpty() }
                    )
                    
                    Log.d("EkatMaps", "Отправка запроса на создание точки: $pointRequest")
                    val response = ServerService.createPoint(pointRequest)
                    if (response.isSuccessful) {
                        Toast.makeText(this@EkatMaps, "Точка создана", Toast.LENGTH_SHORT).show()
                        Log.d("EkatMaps", "Точка успешно создана")
                        // Обновляем карту
                        updatePointsFromServer()
                    } else {
                        Toast.makeText(this@EkatMaps, "Ошибка при создании точки", Toast.LENGTH_SHORT).show()
                        Log.e("EkatMaps", "Ошибка при создании точки: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@EkatMaps, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("EkatMaps", "Исключение при создании точки", e)
                }
            }
            
            // Закрываем диалог
            dialog.dismiss()
        }
        
        Log.d("EkatMaps", "Диалог создания точки показан")
        dialog.show()
    }

    private fun generatePointId(): String {
        val pointId = "MG_${System.currentTimeMillis()}"
        Log.d("EkatMaps", "Сгенерирован ID точки: $pointId")
        return pointId
    }

    private val fusedLocationProviderClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(applicationContext)
    }

    private fun startPointsUpdate() {
        Log.d("EkatMaps", "Запуск периодического обновления точек, интервал: ${pointsUpdateInterval}ms")
        updatePointsRunnable = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastPointsUpdate >= pointsUpdateInterval) {
                    Log.d("EkatMaps", "Выполняется обновление точек")
                    updatePointsFromServer()
                    lastPointsUpdate = currentTime
                }
                handler.postDelayed(this, pointsUpdateInterval)
            }
        }
        handler.post(updatePointsRunnable)
    }

    private fun updatePointsFromServer() {
        Log.d("EkatMaps", "Обновление точек с сервера")
        val serverPoints = ServerService.getPoints()
        if (serverPoints.isEmpty()) {
            Log.d("EkatMaps", "Сервер не вернул точки")
            // Если сервер не вернул точки, используем тестовые
            //addTestPoints()
        } else {
            Log.d("EkatMaps", "Получено ${serverPoints.size} точек с сервера")
            // Удаляем все существующие точки
            pointsOfInterest.values.forEach { (_, circle, marker) ->
                circle?.remove() // Круг может быть null для USER точек
                marker?.remove()
            }
            pointsOfInterest.clear()

            // Добавляем новые точки с сервера
            serverPoints.forEach { point ->
                addPoint(point)
            }

            updateForLocation()
        }
    }

    // Добавляем тестовые точки разных типов
    /*val testPoints = listOf(
        PointVisualizer.createPointOfInterest("1", LatLng(56.840527, 60.652171), 500f, PointType.FAMILIAR),
        PointVisualizer.createPointOfInterest("2", LatLng(56.837609, 60.633470), 300f, PointType.FAKE_FAMILIAR_BITER),
        PointVisualizer.createPointOfInterest("3", LatLng(56.835325, 60.613837), 400f, PointType.OPEN_PROBLEM),
        PointVisualizer.createPointOfInterest("4", LatLng(56.834090, 60.599902), 600f, PointType.AGGRESSIVE_FAMILIAR),
        PointVisualizer.createPointOfInterest("5", LatLng(56.841096, 60.659535), 200f, PointType.HIDDEN_EFFECT),
        PointVisualizer.createPointOfInterest("666", LatLng(56.835325, 60.613837), 800f, PointType.HIDDEN_EFFECT),
        PointVisualizer.createPointOfInterest("6", LatLng(56.838011, 60.597465), 800f, PointType.SHRINKING_CIRCLE,
            mapOf("duration" to 30)) // 30 минут
    )*/

   /* private fun addTestPoints() {

        testPoints.forEach { point ->
            addPointOfInterest(point)
        }
    }*/

    private fun addPoint(point: Point) {
        Log.d("EkatMaps", "Добавление точки: ID=${point.pointId}, тип=${point.type}, координаты=(${point.lat}, ${point.lng})")
        
        // Для обычных пользователей: не показываем точки типа POINT_WITH_TEXT
        if (!isMgUser && point.type == "POINT_WITH_TEXT") {
            Log.d("EkatMaps", "Обычный пользователь: пропускаем точку POINT_WITH_TEXT: ${point.pointId}")
            return
        }
        
        // Удаляем старую точку, если она существует
        pointsOfInterest[point.pointId]?.let { (_, circle, marker) ->
            circle?.remove() // Круг может быть null для USER точек
            marker?.remove()
            Log.d("EkatMaps", "Удалена старая точка: ${point.pointId}")
        }
        pointsOfInterest.remove(point.pointId)

        // Для точек типа USER не создаем круги
        val circle = if (point.type == "USER") {
            null
        } else {
            mMap.addCircle(
                PointVisualizer.getCircleOptions(
                    LatLng(point.vLat, point.vLng),
                    point.radius.toFloat(),
                    PointType.fromServerValue(point.type)
                )
            )
        }

        // Сохраняем точку, круг и null для маркера (он будет добавлен позже)
        // Маркеры создаются только в updateForLocation() для избежания дублирования
        // Для MG пользователей: показываем все точки, кроме своей USER точки
        // Для обычных пользователей: показываем только точки в кругах, кроме USER точек и POINT_WITH_TEXT
        // Для точек типа USER круг = null
        pointsOfInterest[point.pointId] = Triple(point, circle, null)
        Log.d("EkatMaps", "Точка добавлена в список: ${point.pointId} (маркер будет создан позже, круг: ${if (circle != null) "создан" else "не создан для USER"})")
    }

    private fun updateForLocation() {
        Log.d("EkatMaps", "Обновление карты для местоположения: ${currentLocation.latitude}, ${currentLocation.longitude}")
        val latLng = LatLng(currentLocation.latitude, currentLocation.longitude)
        
        // Получаем ID текущего пользователя для проверки дублирования USER точек
        val currentUserId = UserPrefsHelper.getUserId(this)
        Log.d("EkatMaps", "Текущий пользователь: $currentUserId, тип: ${if (isMgUser) "MG" else "обычный"}")
        
        // Удаляем предыдущий маркер, если он существует
        currentLocationMarker?.remove()
        
        // Создаем новый маркер для текущего местоположения (синий)
        // Этот маркер показывает реальное местоположение пользователя
        currentLocationMarker = mMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Ваше местоположение")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        )

        // Обрабатываем точки интереса
        // Для MG пользователей: показываем все точки, кроме своей собственной USER точки (чтобы не дублировать маркер геолокации)
        // Для обычных пользователей: показываем только точки в кругах, кроме USER точек (чтобы не дублировать маркер геолокации)
        // Для MG пользователей показываем все точки всегда
        if (isMgUser) {
            Log.d("EkatMaps", "MG пользователь: показываем все точки на карте (расстояние не учитывается)")
            pointsOfInterest.forEach { (id, pointData) ->
                val (point, circle, currentMarker) = pointData
                
                // Пропускаем точки типа USER (у них нет кругов и они не нужны на карте)
                if (point.type == "USER") {
                    Log.d("EkatMaps", "MG пользователь: пропускаем точку USER: $id (нет круга)")
                    return@forEach
                }
                
                // Если маркера еще нет - создаем его
                if (currentMarker == null) {
                    val newMarker = mMap.addMarker(
                        PointVisualizer.getMarkerOptions(
                            LatLng(point.lat, point.lng),
                            PointType.fromServerValue(point.type),
                            getPointTitle(PointType.fromServerValue(point.type)),
                            getPointDescription(point)
                        )
                    )
                    pointsOfInterest[id] = Triple(point, circle, newMarker)
                    Log.d("EkatMaps", "MG пользователь: добавлен маркер для точки: $id")
                }
            }
        } else {
            // Для обычных пользователей проверяем, находится ли пользователь в каких-либо кругах
            Log.d("EkatMaps", "Обычный пользователь: проверяем расстояние до точек для отображения маркеров (только в кругах)")
            pointsOfInterest.forEach { (id, pointData) ->
                val (point, circle, currentMarker) = pointData
                
                // Пропускаем точки типа USER (у них нет кругов)
                if (point.type == "USER") {
                    Log.d("EkatMaps", "Обычный пользователь: пропускаем точку USER: $id (нет круга)")
                    return@forEach
                }
                
                // Пропускаем точки без кругов
                if (circle == null) {
                    Log.d("EkatMaps", "Обычный пользователь: пропускаем точку без круга: $id")
                    return@forEach
                }
                
                val virtualCenter = LatLng(point.vLat, point.vLng)
                val distance = calculateDistance(latLng, virtualCenter)
                
                if (distance <= point.radius) {
                    // Если пользователь в круге и маркера еще нет - создаем его
                    if (currentMarker == null) {
                        val newMarker = mMap.addMarker(
                            PointVisualizer.getMarkerOptions(
                                LatLng(point.lat, point.lng),
                                PointType.fromServerValue(point.type),
                                getPointTitle(PointType.fromServerValue(point.type)),
                                getPointDescription(point)
                            )
                        )
                        pointsOfInterest[id] = Triple(point, circle, newMarker)
                        Log.d("EkatMaps", "Обычный пользователь: в круге (${distance.toInt()}м), добавлен маркер для точки: $id")
                    }
                } else {
                    // Если пользователь вне круга и маркер существует - удаляем его
                    if (currentMarker != null) {
                        currentMarker.remove()
                        pointsOfInterest[id] = Triple(point, circle, null)
                        Log.d("EkatMaps", "Обычный пользователь: вне круга (${distance.toInt()}м), удален маркер для точки: $id")
                    }
                }
            }
        }

        Log.d("EkatMaps", "Обновление карты завершено")
    }

    private fun getPointTitle(type: PointType): String {
        val title = when (type) {
            PointType.USER -> "Пользователь"
            PointType.FAMILIAR -> "Фамильяр"
            PointType.HIDDEN_EFFECT_AREA -> "Скрытая зона эффекта"
            PointType.FAKE_FAMILIAR_BITER -> "Поддельный Фамильяр"
            PointType.APPROACHING_BITER -> "Приближающийся Фамильяр"
            PointType.OPEN_PROBLEM -> "Открытая Проблема"
            PointType.SHRINKING_CIRCLE -> "Сужающийся Круг"
            PointType.DEMON_BLACK_CIRCLE -> "Демон Черный Круг"
            PointType.APPROACHING_VIRTUAL -> "Приближающийся Виртуальный"
            PointType.HIDDEN_AR_POINT -> "Скрытая AR точка"
            PointType.POINT_WITH_TEXT -> "Точка с текстом"
        }
        Log.d("EkatMaps", "Заголовок для типа ${type.serverValue}: $title")
        return title
    }

    private fun getPointDescription(point: Point): String {
        val description = when (PointType.fromServerValue(point.type)) {
            PointType.SHRINKING_CIRCLE -> {
                "Радиус: ${point.radius}м\nДлительность: 30 мин"
            }
            else -> "Радиус: ${point.radius}м"
        }
        Log.d("EkatMaps", "Описание для точки ${point.pointId}: $description")
        return description
    }

    private fun calculateDistance(point1: LatLng, point2: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            point1.latitude, point1.longitude,
            point2.latitude, point2.longitude,
            results
        )
        val distance = results[0]
        Log.d("EkatMaps", "Расстояние между точками: ${point1.latitude},${point1.longitude} и ${point2.latitude},${point2.longitude} = ${distance}м")
        return distance
    }

    private fun requestForLocation() {
        Log.d("EkatMaps", "Запрос разрешений на геолокацию")
        when {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.d("EkatMaps", "Разрешения на геолокацию уже получены")
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
                Log.d("EkatMaps", "Запрашиваем разрешения на геолокацию")
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
        Log.d("EkatMaps", "Запрос текущего местоположения")
        val currentTask: Task<Location> = fusedLocationProviderClient.getCurrentLocation(
            PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token
        )

        currentTask.addOnCompleteListener { task ->
            if (task.isSuccessful && task.result != null) {
                currentLocation = task.result
                val latLng = LatLng(currentLocation.latitude, currentLocation.longitude)
                Log.d("EkatMaps", "Получено текущее местоположение: ${currentLocation.latitude}, ${currentLocation.longitude}")
                // Центрируем карту на текущем местоположении
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))

                updateForLocation()
            } else {
                Log.e("EkatMaps", "Ошибка при получении местоположения: ${task.exception}")
            }
        }

        locationUpdateDisposable = LocationService.locationSource.subscribe {
            currentLocation = it
            Log.d("EkatMaps", "Обновление местоположения через LocationService: ${it.latitude}, ${it.longitude}")
            updateForLocation()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_LOCATION_PERMISSION && grantResults.isNotEmpty()) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("EkatMaps", "Разрешения на геолокацию получены")
                requestCurrentLocation()
            } else {
                Log.w("EkatMaps", "Разрешения на геолокацию не получены")
            }
        }
    }

    private fun checkIfMgUser() {
        val userName = UserPrefsHelper.getUserId(this)
        isMgUser = userName.startsWith("MG", ignoreCase = true)
        Log.d("EkatMaps", "Проверка MG пользователя: $userName, результат: $isMgUser")
    }

    companion object {
        private const val REQUEST_CODE_LOCATION_PERMISSION = 100
    }
}