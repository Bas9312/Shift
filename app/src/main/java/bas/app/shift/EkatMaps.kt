package bas.app.shift

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.LayoutInflater
import bas.app.shift.helpers.LogHelper
import android.view.View
import android.widget.TextView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.util.LinkifyCompat
import androidx.lifecycle.lifecycleScope
import bas.app.shift.databinding.ActivityEkatMapsBinding
import bas.app.shift.databinding.DialogCreatePointBinding
import bas.app.shift.models.FamiliarData
import bas.app.shift.databinding.DialogPointInfoBinding
import bas.app.shift.ui.FamiliarChatActivity
import bas.app.shift.ui.FamiliarFoundActivity
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

    private var currentLocation: Location? = null
    private var locationUpdateDisposable: Disposable? = null
    private var updatePointsRunnable: Runnable? = null
    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityEkatMapsBinding
    private var cancellationTokenSource = CancellationTokenSource()
    private var currentLocationMarker: Marker? = null
    private val pointsOfInterest = mutableMapOf<String, Triple<Point, Circle?, Marker?>>()
    private val handler = Handler(Looper.getMainLooper())
    private val pointsUpdateInterval = 10000L // 10 секунд
    private var lastPointsUpdate = 0L
    private var isMgUser = false
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogHelper.d("EkatMaps: onCreate: запуск активности карты")

        // Проверяем состояние игры
        val isInGame = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
            .getBoolean(MainActivity.KEY_IN_GAME, false)
        if (!isInGame) {
            LogHelper.w("onCreate: персонаж не в игре")
            Toast.makeText(this, "Персонаж не в игре", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Проверяем, является ли пользователь MG
        checkIfMgUser()
        
        LogHelper.d("EkatMaps: onCreate: карта доступна всем пользователям, ${if (isMgUser) "MG пользователь получает дополнительный функционал (лонг тапы)" else "обычный пользователь получает базовый функционал (просмотр точек)"}")
        binding = ActivityEkatMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            LogHelper.d("EkatMaps: Нажата кнопка назад")
            finish()
        }
        
        // Настраиваем кнопку возврата к геолокации
        binding.fabMyLocation.setOnClickListener {
            LogHelper.d("EkatMaps: Нажата кнопка возврата к геолокации")
            moveToCurrentLocation()
        }

        if (isMgUser) {
            binding.fabSearchPlayer.visibility = View.VISIBLE
            binding.fabSearchPlayer.setOnClickListener {
                showPlayersPickerDialog()
            }
        }
        
        // Настраиваем кнопку управления видимостью для MG пользователей
        if (isMgUser) {
            setupVisibilityToggleButton()
        }
        
        LogHelper.d("EkatMaps: onCreate: инициализация завершена для ${if (isMgUser) "MG" else "обычного"} пользователя")
    }

    override fun onResume() {
        super.onResume()
        LogHelper.d("EkatMaps: onResume: возобновление активности карты")
        
        // Проверяем состояние игры
        val isInGame = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
            .getBoolean(MainActivity.KEY_IN_GAME, false)
        if (!isInGame) {
            LogHelper.w("onResume: персонаж не в игре")
            Toast.makeText(this, "Персонаж не в игре", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        LogHelper.d("onResume: карта доступна всем пользователям, ${if (isMgUser) "MG пользователь получает дополнительный функционал (лонг тапы)" else "обычный пользователь получает базовый функционал (просмотр точек)"}")
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        LogHelper.d("onResume: карта загружается асинхронно для ${if (isMgUser) "MG" else "обычного"} пользователя")
    }

    override fun onPause() {
        super.onPause()
        LogHelper.d("onPause: приостановка активности карты")
        updatePointsRunnable?.let { handler.removeCallbacks(it) }

        locationUpdateDisposable?.dispose()
        LogHelper.d("onPause: ресурсы освобождены")
    }

    override fun onDestroy() {
        super.onDestroy()
        LogHelper.d("onDestroy: уничтожение активности карты")
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
        LogHelper.d("Карта готова к использованию")
        mMap = googleMap
        mMap.setIndoorEnabled(false)
        mMap.isTrafficEnabled = false
        if (isMgUser) {
            mMap.setMinZoomPreference(9.0f)
        } else {
            mMap.setMinZoomPreference(13.0f)
        }
        
        // Включаем отображение зданий и улиц
        mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isCompassEnabled = true
        mMap.uiSettings.isMapToolbarEnabled = true
        mMap.uiSettings.isTiltGesturesEnabled = true
        mMap.uiSettings.isRotateGesturesEnabled = true
        mMap.uiSettings.isScrollGesturesEnabled = true
        mMap.uiSettings.isZoomGesturesEnabled = true

        // Добавляем обработчики событий для всех пользователей
        if (isMgUser) {
            LogHelper.d("Настройка карты для MG пользователя с дополнительным функционалом (лонг тапы)")
            setupMgUserMapHandlers()
        } else {
            LogHelper.d("Настройка карты для обычного пользователя (базовый функционал с нажатиями на маркеры)")
            setupRegularUserMapHandlers()
        }

        // Запускаем периодическое обновление точек
        startPointsUpdate()
        
        // Запрашиваем локацию и подписываемся на обновления
        requestForLocation()
        
        LogHelper.d("Инициализация карты завершена: ${if (isMgUser) "MG пользователь с полным функционалом" else "обычный пользователь с базовым функционалом"}")
    }

    private fun setupMgUserMapHandlers() {
        LogHelper.d("Настройка дополнительных обработчиков событий для MG пользователя (лонг тапы)")
        
        // Обработчик лонг тапа по маркерам
        mMap.setOnMarkerClickListener { marker ->
            LogHelper.d("MG пользователь: лонг тап по маркеру: ${marker.title}")
            showPointInfoDialog(marker)
            true
        }

        // Обработчик лонг тапа по карте
        mMap.setOnMapLongClickListener { latLng ->
            LogHelper.d("MG пользователь: лонг тап по карте: ${latLng.latitude}, ${latLng.longitude}")
            showCreatePointDialog(latLng)
        }
        
        LogHelper.d("Дополнительные обработчики событий для MG пользователя настроены (лонг тапы по маркерам и карте)")
    }

    private fun setupRegularUserMapHandlers() {
        LogHelper.d("Настройка обработчиков событий для обычного пользователя (нажатия на маркеры)")
        
        // Обработчик нажатия по маркерам
        mMap.setOnMarkerClickListener { marker ->
            LogHelper.d("Обычный пользователь: нажатие по маркеру: ${marker.title}")
            handleMarkerClick(marker)
            true
        }
        
        LogHelper.d("Обработчики событий для обычного пользователя настроены (нажатия на маркеры)")
    }

    private fun handleMarkerClick(marker: Marker) {
        LogHelper.d("Обработка нажатия на маркер: ${marker.title}")
        
        // Находим точку по маркеру
        val pointData = pointsOfInterest.values.find { (_, _, markerRef) -> markerRef == marker }
        if (pointData == null) {
            LogHelper.w("Точка не найдена для маркера: ${marker.title}")
            return
        }

        val (point, _, _) = pointData
        LogHelper.d("Информация о точке: ID=${point.pointId}, тип=${point.type}")
        
        // Проверяем, является ли это фамильяром
        if (point.type == "FAMILIAR") {
            showFamiliarDialog(point)
        } else {
            // Для других типов точек показываем базовую информацию
            showBasicPointInfoDialog(point)
        }
    }

    private fun showFamiliarDialog(point: Point) {
        LogHelper.d("Показ диалога фамильяра для точки: ${point.pointId}")
        
        // Проверяем расстояние до фамильяра
        val currentLocation = this.currentLocation
        if (currentLocation == null) {
            LogHelper.w("Текущая локация недоступна")
            Toast.makeText(this, getString(R.string.location_unavailable), Toast.LENGTH_SHORT).show()
            return
        }
        
        val distance = calculateDistance(
            LatLng(currentLocation.latitude, currentLocation.longitude),
            LatLng(point.lat, point.lng)
        )
        
        // Используем то же расстояние, что и в LocationService (50 метров для фамильяров)
        val maxDistance = 50.0
        
        if (distance <= maxDistance) {
            // Пользователь достаточно близко - показываем диалог с кнопкой "поговорить"
            showFamiliarTalkDialog(point, distance)
        } else {
            // Пользователь слишком далеко
            Toast.makeText(
                this, 
                getString(R.string.familiar_too_far_message, distance.toInt(), maxDistance.toInt()), 
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showFamiliarTalkDialog(point: Point, distance: Float) {
        LogHelper.d("Показ диалога разговора с фамильяром, расстояние: ${distance.toInt()}м")
        
        val familiarName = getPointTitle(PointType.fromServerValue(point.type))
        val message = getString(R.string.familiar_dialog_message, familiarName, distance.toInt())
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.familiar_dialog_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.familiar_talk_button)) { _, _ ->
                // Открываем экран "фамильяр рядом"
                openFamiliarFound(point)
            }
            .setNegativeButton(getString(R.string.familiar_cancel_button), null)
            .show()
    }

    private fun showBasicPointInfoDialog(point: Point) {
        LogHelper.d("Показ базовой информации о точке: ${point.pointId}")
        
        val title = getPointTitle(PointType.fromServerValue(point.type))
        val message = if (point.type == "USER") "Здесь кто-то есть (мастер или игротех)"
        else getString(R.string.point_basic_radius, point.radius) + "\n" +
                getString(R.string.point_basic_description, point.description ?: getString(R.string.point_no_description)) + "\n" +
                if (point.textToShowOnEnter.isNullOrEmpty()) "" else "При входе: ${point.textToShowOnEnter}"

        val tv = TextView(this).apply {
            text = message
            setTextIsSelectable(true)
            setPadding(48, 32, 48, 16)
            LinkifyCompat.addLinks(this, Linkify.ALL)
            movementMethod = LinkMovementMethod.getInstance()
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(tv)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun openFamiliarFound(point: Point) {
        LogHelper.d("Открытие экрана 'фамильяр рядом' для точки: ${point.pointId}")
        
        // Извлекаем ID фамильяра из pointId или используем дефолтный
        // Предполагаем, что pointId содержит информацию о фамильяре
        val familiarId = extractFamiliarIdFromPoint(point)
        
        val intent = Intent(this, FamiliarFoundActivity::class.java).apply {
            putExtra("familiar_id", familiarId)
        }
        startActivity(intent)
    }

    private fun extractFamiliarIdFromPoint(point: Point): String {
        // Пытаемся извлечь ID фамильяра из pointId или description
        // Если не получается, используем дефолтный
        val familiarId = when {
            point.description?.contains("familiar_") == true -> {
                // Ищем familiar_ в описании
                val match = Regex("familiar_[a-zA-Z_]+").find(point.description)
                match?.value ?: "familiar_malachite_lizard"
            }
            point.pointId.contains("familiar_") -> {
                // Ищем familiar_ в pointId
                val match = Regex("familiar_[a-zA-Z_]+").find(point.pointId)
                match?.value ?: "familiar_malachite_lizard"
            }
            else -> "familiar_malachite_lizard" // Дефолтный фамильяр
        }
        
        LogHelper.d("Извлечен ID фамильяра: $familiarId")
        return familiarId
    }

    private fun showPointInfoDialog(marker: Marker) {
        LogHelper.d("Показ диалога информации о точке")
        
        // Находим точку по маркеру
        val pointData = pointsOfInterest.values.find { (_, _, markerRef) -> markerRef == marker }
        if (pointData == null) {
            LogHelper.w("Точка не найдена для маркера: ${marker.title}")
            return
        }

        val (point, _, _) = pointData
        LogHelper.d("Информация о точке: ID=${point.pointId}, тип=${point.type}")
        
        val dialogBinding = DialogPointInfoBinding.inflate(LayoutInflater.from(this))
        
        // Заполняем информацию о точке
        dialogBinding.tvPointTitle.text = getPointTitle(PointType.fromServerValue(point.type))
        dialogBinding.tvPointType.text = getString(R.string.point_radius_label) + " " + point.radius + "м"
        dialogBinding.tvPointRadius.text = getString(R.string.point_coordinates_label) + " " + String.format("%.6f", point.lat) + ", " + String.format("%.6f", point.lng)
        dialogBinding.tvPointCoordinates.text = getString(R.string.point_description_label) + " " + (point.description ?: getString(R.string.point_no_description))
        dialogBinding.tvPointDescription.text = getString(R.string.point_text_on_enter_label) + " " + (point.textToShowOnEnter ?: getString(R.string.point_no_text_on_enter))
        dialogBinding.tvPointTextToShowOnEnter.text = "" // Скрываем это поле, так как оно не всегда нужно

        listOf(dialogBinding.tvPointCoordinates, dialogBinding.tvPointDescription).forEach { tv ->
            tv.setTextIsSelectable(true)
            LinkifyCompat.addLinks(tv, Linkify.ALL)
            tv.movementMethod = LinkMovementMethod.getInstance()
        }

        // Создаем диалог
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        // Обработчик кнопки удаления
        dialogBinding.btnDeletePoint.setOnClickListener {
            LogHelper.d("Удаление точки: ${point.pointId}")
            scope.launch {
                try {
                    val response = ServerService.deletePoint(point.pointId)
                    if (response.isSuccessful) {
                        Toast.makeText(this@EkatMaps, getString(R.string.point_deleted_success), Toast.LENGTH_SHORT).show()
                        LogHelper.d("Точка успешно удалена")
                        // Обновляем карту
                        updatePointsFromServer()
                    } else {
                        Toast.makeText(this@EkatMaps, getString(R.string.point_delete_error), Toast.LENGTH_SHORT).show()
                        LogHelper.e("Ошибка при удалении точки: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@EkatMaps, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    LogHelper.e("Исключение при удалении точки: ${e.message}")
                }
            }
            // Закрываем диалог
            dialog.dismiss()
        }

        LogHelper.d("Диалог информации о точки показан")
        dialog.show()
    }

    private fun showCreatePointDialog(latLng: LatLng) {
        LogHelper.d("Показ диалога создания точки: ${latLng.latitude}, ${latLng.longitude}")
        
        val dialogBinding = DialogCreatePointBinding.inflate(LayoutInflater.from(this))
        
        // Показываем координаты
        dialogBinding.tvCoordinates.text = getString(R.string.point_coordinates_label) + " " + String.format("%.6f", latLng.latitude) + ", " + String.format("%.6f", latLng.longitude)
        
        // Настраиваем спиннер типов точек (исключаем USER)
        val pointTypes = PointType.values()
            .filter { it != PointType.USER }
        val pointTypeNames = pointTypes.map { getPointTitle(it) }
        val pointTypeValues = pointTypes.map { it.serverValue }
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, pointTypeNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spinnerPointType.adapter = adapter
        
        // Настраиваем спиннер фамильяров
        val familiarNames = FamiliarData.familiars.values.toList()
        val familiarIds = FamiliarData.familiars.keys.toList()
        val familiarAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, familiarNames)
        familiarAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spinnerFamiliar.adapter = familiarAdapter
        
        // Показываем/скрываем поля в зависимости от выбранного типа
        dialogBinding.spinnerPointType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedType = pointTypeValues[position]
                LogHelper.d("Выбран тип точки: $selectedType")
                
                when (selectedType) {
                    "FAMILIAR" -> {
                        // Для фамильяра показываем спиннер фамильяров, скрываем описание и текст при входе
                        dialogBinding.tvDescriptionLabel.visibility = View.GONE
                        dialogBinding.etDescription.visibility = View.GONE
                        dialogBinding.tvFamiliarLabel.visibility = View.VISIBLE
                        dialogBinding.spinnerFamiliar.visibility = View.VISIBLE
                        dialogBinding.tvTextToShowLabel.visibility = View.GONE
                        dialogBinding.etTextToShowOnEnter.visibility = View.GONE
                        dialogBinding.tvExpireLabel.visibility = View.GONE
                        dialogBinding.etExpireMinutes.visibility = View.GONE
                    }
                    "SHRINKING_CIRCLE" -> {
                        // Для сужающегося круга показываем поле времени истечения
                        dialogBinding.tvDescriptionLabel.visibility = View.VISIBLE
                        dialogBinding.etDescription.visibility = View.VISIBLE
                        dialogBinding.tvFamiliarLabel.visibility = View.GONE
                        dialogBinding.spinnerFamiliar.visibility = View.GONE
                        dialogBinding.tvTextToShowLabel.visibility = View.VISIBLE
                        dialogBinding.etTextToShowOnEnter.visibility = View.VISIBLE
                        dialogBinding.tvExpireLabel.visibility = View.GONE
                        dialogBinding.etExpireMinutes.visibility = View.GONE
                    }
                    else -> {
                        // Для остальных типов показываем стандартные поля
                        dialogBinding.tvDescriptionLabel.visibility = View.VISIBLE
                        dialogBinding.etDescription.visibility = View.VISIBLE
                        dialogBinding.tvFamiliarLabel.visibility = View.GONE
                        dialogBinding.spinnerFamiliar.visibility = View.GONE
                        dialogBinding.tvTextToShowLabel.visibility = View.VISIBLE
                        dialogBinding.etTextToShowOnEnter.visibility = View.VISIBLE
                        dialogBinding.tvExpireLabel.visibility = View.GONE
                        dialogBinding.etExpireMinutes.visibility = View.GONE
                    }
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
            val selectedPosition = dialogBinding.spinnerPointType.selectedItemPosition
            val selectedType = pointTypeValues[selectedPosition]
            val textToShowOnEnter = dialogBinding.etTextToShowOnEnter.text.toString()
            
            // Определяем описание в зависимости от типа точки
            val description = when (selectedType) {
                "FAMILIAR" -> {
                    val selectedFamiliarPosition = dialogBinding.spinnerFamiliar.selectedItemPosition
                    if (selectedFamiliarPosition >= 0 && selectedFamiliarPosition < familiarIds.size) {
                        familiarIds[selectedFamiliarPosition]
                    } else {
                        Toast.makeText(this, "Выберите фамильяра", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                }
                else -> {
                    val descriptionText = dialogBinding.etDescription.text.toString()
                    if (descriptionText.isBlank()) {
                        Toast.makeText(this, "Введите описание точки", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    descriptionText
                }
            }
            
            LogHelper.d("Создание точки: тип=$selectedType, описание=$description, текст при входе=$textToShowOnEnter")
            
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
                    LogHelper.d("Точка истечет через: $expireMinutes минут, expireAt: $expireAt")
                }
            }
            
            // Создаем точку
            scope.launch {
                try {
                    val userId = UserPrefsHelper.getUserId(this@EkatMaps)
                    LogHelper.d("Создание точки для пользователя: $userId")
                    
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
                    
                    LogHelper.d("Отправка запроса на создание точки: $pointRequest")
                    val response = ServerService.createPoint(pointRequest)
                    if (response.isSuccessful) {
                        Toast.makeText(this@EkatMaps, "Точка создана", Toast.LENGTH_SHORT).show()
                        LogHelper.d("Точка успешно создана")
                        // Обновляем карту
                        updatePointsFromServer()
                    } else {
                        Toast.makeText(this@EkatMaps, "Ошибка при создании точки", Toast.LENGTH_SHORT).show()
                        LogHelper.e("Ошибка при создании точки: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@EkatMaps, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    LogHelper.e("Исключение при создании точки: ${e.message}")
                }
            }
            
            // Закрываем диалог
            dialog.dismiss()
        }
        
        LogHelper.d("Диалог создания точки показан")
        dialog.show()
    }

    private fun generatePointId(): String {
        val pointId = "MG_${System.currentTimeMillis()}"
        LogHelper.d("Сгенерирован ID точки: $pointId")
        return pointId
    }

    private val fusedLocationProviderClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(applicationContext)
    }

    private fun startPointsUpdate() {
        LogHelper.d("Запуск периодического обновления точек, интервал: ${pointsUpdateInterval}ms")
        updatePointsRunnable = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastPointsUpdate >= pointsUpdateInterval) {
                    LogHelper.d("Выполняется обновление точек")
                    updatePointsFromServer()
                    lastPointsUpdate = currentTime
                }
                handler.postDelayed(this, pointsUpdateInterval)
            }
        }
        updatePointsRunnable?.let {
            handler.post(it)
        }
    }

    private fun updatePointsFromServer() {
        LogHelper.d("Обновление точек с сервера")
        lifecycleScope.launch {
            try {
                val serverPoints = ServerService.getPoints()
                if (serverPoints.isEmpty()) {
                    LogHelper.d("Сервер не вернул точки")
                    // Если сервер не вернул точки, используем тестовые
                    //addTestPoints()
                } else {
                    LogHelper.d("Получено ${serverPoints.size} точек с сервера")
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

                    // Обновляем карту только если есть текущая локация
                    if (currentLocation != null) {
                        updateForLocation()
                    } else {
                        LogHelper.d("Локация недоступна, маркеры будут добавлены позже")
                    }
                }
            } catch (e: Exception) {
                LogHelper.e("Ошибка при обновлении точек с сервера: ${e.message}")
            }
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
        //LogHelper.d("Добавление точки: ID=${point.pointId}, тип=${point.type}, координаты=(${point.lat}, ${point.lng})")
        
        // Для обычных пользователей: не показываем точки типа POINT_WITH_TEXT
        if (!isMgUser && point.type == "POINT_WITH_TEXT") {
            LogHelper.d("Обычный пользователь: пропускаем точку POINT_WITH_TEXT: ${point.pointId}")
            return
        }
        
        // Удаляем старую точку, если она существует
        pointsOfInterest[point.pointId]?.let { (_, circle, marker) ->
            circle?.remove() // Круг может быть null для USER точек
            marker?.remove()
            LogHelper.d("Удалена старая точка: ${point.pointId}")
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
        //LogHelper.d("Точка добавлена в список: ${point.pointId} (маркер будет создан позже, круг: ${if (circle != null) "создан" else "не создан для USER"})")
    }

    private fun updateForLocation() {
        // Проверяем, есть ли текущая локация
        if (currentLocation == null) {
            LogHelper.d("Текущая локация недоступна, пропускаем обновление карты")
            return
        }
        
        //LogHelper.d("Обновление карты для местоположения: ${currentLocation!!.latitude}, ${currentLocation!!.longitude}")
        val latLng = LatLng(currentLocation!!.latitude, currentLocation!!.longitude)
        
        // Получаем ID текущего пользователя для проверки дублирования USER точек
        val currentUserId = UserPrefsHelper.getUserId(this)
        //LogHelper.d("Текущий пользователь: $currentUserId, тип: ${if (isMgUser) "MG" else "обычный"}")
        
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
            LogHelper.d("MG пользователь: показываем все точки на карте (расстояние не учитывается)")
            pointsOfInterest.forEach { (id, pointData) ->
                val (point, circle, currentMarker) = pointData
                
                // Пропускаем точки типа USER (у них нет кругов и они не нужны на карте)
                if (point.type == "USER" && !isMgUser) {
                    LogHelper.d("MG пользователь: пропускаем точку USER: $id (нет круга)")
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
                    LogHelper.d("MG пользователь: добавлен маркер для точки: $id")
                }
            }
        } else {
            // Для обычных пользователей проверяем, находится ли пользователь в каких-либо кругах
            LogHelper.d("Обычный пользователь: проверяем расстояние до точек для отображения маркеров (только в кругах)")
            pointsOfInterest.forEach { (id, pointData) ->
                val (point, circle, currentMarker) = pointData
                
                val virtualCenter = LatLng(point.vLat, point.vLng)
                val distance = if (point.type == "USER") 0f else calculateDistance(latLng, virtualCenter)
                
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
                        LogHelper.d("Обычный пользователь: в круге (${distance.toInt()}м), добавлен маркер для точки: $id")
                    }
                } else {
                    // Если пользователь вне круга и маркер существует - удаляем его
                    if (currentMarker != null) {
                        currentMarker.remove()
                        pointsOfInterest[id] = Triple(point, circle, null)
                        LogHelper.d("Обычный пользователь: вне круга (${distance.toInt()}м), удален маркер для точки: $id")
                    }
                }
            }
        }

        //LogHelper.d("Обновление карты завершено")
    }

    private fun getPointTitle(type: PointType): String {
        val title = when (type) {
            PointType.USER -> "Кто-то в игре"
            PointType.FAMILIAR -> "Фамильяр"
            PointType.HIDDEN_EFFECT_AREA -> "Скрытая зона эффекта"
            PointType.FAKE_FAMILIAR_BITER -> "'Фамильяр'"
            PointType.APPROACHING_BITER -> "Приближающийся `Фамильяр`"
            PointType.OPEN_PROBLEM -> "Открытая Проблема"
            PointType.SHRINKING_CIRCLE -> "Сужающийся Круг"
            PointType.DEMON_BLACK_CIRCLE -> "Демон Черный Круг"
            PointType.APPROACHING_VIRTUAL -> "Приближающаяся Виртуальная проблема"
            PointType.HIDDEN_AR_POINT -> "Скрытая AR точка"
            PointType.POINT_WITH_TEXT -> "Точка с текстом"
        }
        //LogHelper.d("Заголовок для типа ${type.serverValue}: $title")
        return title
    }

    private fun getPointDescription(point: Point): String {
        val description = when (PointType.fromServerValue(point.type)) {
            PointType.SHRINKING_CIRCLE -> {
                "Радиус: ${point.radius}м\nДлительность: 30 мин"
            }
            else -> "Радиус: ${point.radius}м"
        }
        //LogHelper.d("Описание для точки ${point.pointId}: $description")
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
        //LogHelper.d("Расстояние между точками: ${point1.latitude},${point1.longitude} и ${point2.latitude},${point2.longitude} = ${distance}м")
        return distance
    }

    private fun requestForLocation() {
        LogHelper.d("Запрос разрешений на геолокацию")
        when {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                LogHelper.d("Разрешения на геолокацию уже получены")
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
                LogHelper.d("Запрашиваем разрешения на геолокацию")
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
        LogHelper.d("Запрос текущего местоположения")
        val currentTask: Task<Location> = fusedLocationProviderClient.getCurrentLocation(
            PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token
        )

        currentTask.addOnCompleteListener { task ->
            if (task.isSuccessful && task.result != null) {
                currentLocation = task.result
                val latLng = LatLng(currentLocation!!.latitude, currentLocation!!.longitude)
                LogHelper.d("Получено текущее местоположение: ${currentLocation!!.latitude}, ${currentLocation!!.longitude}")
                
                // Центрируем карту на текущем местоположении
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))

                // Обновляем карту
                updateForLocation()
            } else {
                LogHelper.e("Ошибка при получении местоположения: ${task.exception}")
                // Локация не получена, но это не критично - она придет через LocationService
                LogHelper.d("Ожидаем локацию через LocationService")
            }
        }

        // Подписываемся на обновления локации через LocationService
        locationUpdateDisposable = LocationService.locationSource.subscribe(
            { location ->
                currentLocation = location
                LogHelper.d("Обновление местоположения через LocationService: ${location.latitude}, ${location.longitude}")
                
                // Центрируем карту на текущем местоположении при первом получении
                if (currentLocationMarker == null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                }
                
                // Обновляем карту
                updateForLocation()
            },
            { error ->
                LogHelper.e("Ошибка при получении локации: ${error.message}")
                Toast.makeText(this, "Ошибка при получении локации", Toast.LENGTH_SHORT).show()
            }
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_LOCATION_PERMISSION && grantResults.isNotEmpty()) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                LogHelper.d("Разрешения на геолокацию получены")
                requestCurrentLocation()
            } else {
                LogHelper.w("Разрешения на геолокацию не получены")
            }
        }
    }

    private fun checkIfMgUser() {
        val userName = UserPrefsHelper.getUserId(this)
        isMgUser = userName.startsWith("MG", ignoreCase = true)
        LogHelper.d("Проверка MG пользователя: $userName, результат: $isMgUser")
    }

    private fun setupVisibilityToggleButton() {
        LogHelper.d("Настройка кнопки управления видимостью для MG пользователя")
        
        // Показываем кнопку
        binding.btnToggleVisibility.visibility = View.VISIBLE
        
        // Обновляем текст кнопки в зависимости от текущего состояния
        updateVisibilityButtonText()
        
        // Настраиваем обработчик нажатия
        binding.btnToggleVisibility.setOnClickListener {
            toggleVisibility()
        }
    }

    private fun updateVisibilityButtonText() {
        val isVisible = UserPrefsHelper.getShowOnMap(this)
        val buttonText = if (isVisible) {
            getString(R.string.hide_on_map_button)
        } else {
            getString(R.string.show_on_map_button)
        }
        binding.btnToggleVisibility.text = buttonText
        LogHelper.d("Обновлен текст кнопки видимости: $buttonText (show = $isVisible)")
    }

    private fun toggleVisibility() {
        val currentVisibility = UserPrefsHelper.getShowOnMap(this)
        val newVisibility = !currentVisibility
        
        // Сохраняем новое состояние
        UserPrefsHelper.setShowOnMap(this, newVisibility)
        
        // Обновляем текст кнопки
        updateVisibilityButtonText()
        
        // Отправляем обновленную геолокацию с новым состоянием show
        if (currentLocation != null) {
            ServerService.sendLocation(currentLocation!!)
        }
        
        val message = if (newVisibility) {
            "Теперь вы видимы на карте для других игроков"
        } else {
            "Теперь вы скрыты на карте от других игроков"
        }
        
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        LogHelper.d("Переключена видимость на карте: $currentVisibility -> $newVisibility")
    }

    /**
     * Перемещает камеру к текущей геолокации пользователя
     */
    private fun moveToCurrentLocation() {
        if (currentLocation != null) {
            val latLng = LatLng(currentLocation!!.latitude, currentLocation!!.longitude)
            LogHelper.d("Перемещение камеры к текущей геолокации: ${currentLocation!!.latitude}, ${currentLocation!!.longitude}")
            
            // Анимированно перемещаем камеру к текущему местоположению
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            
            Toast.makeText(this, "Переход к вашему местоположению", Toast.LENGTH_SHORT).show()
        } else {
            LogHelper.w("Текущая геолокация недоступна")
            Toast.makeText(this, "Геолокация недоступна", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPlayersPickerDialog() {
        val userPoints = pointsOfInterest.values
            .map { it.first }
            .filter { it.type == "USER" }
            .distinctBy { it.pointId }
            .sortedBy { (it.description ?: it.pointId).lowercase(Locale.getDefault()) }

        if (userPoints.isEmpty()) {
            Toast.makeText(this, "Игроки не найдены", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = userPoints.map { point ->
            point.description?.takeIf { it.isNotBlank() } ?: point.pointId
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Игроки")
            .setItems(labels) { dialog, which ->
                val point = userPoints[which]
                val latLng = LatLng(point.lat, point.lng)
                LogHelper.d("MG пользователь: центрируем карту на игроке: ${labels[which]} (${latLng.latitude}, ${latLng.longitude})")
                mMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        latLng,
                        maxOf(mMap.cameraPosition.zoom, 15f)
                    )
                )
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    companion object {
        private const val REQUEST_CODE_LOCATION_PERMISSION = 100
    }
}