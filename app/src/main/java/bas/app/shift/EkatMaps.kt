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
import bas.app.shift.helpers.PointRadiusMath
import android.view.View
import android.widget.TextView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import android.widget.NumberPicker
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
import bas.app.shift.models.isExtrasensory
import bas.app.shift.models.vLatOrLat
import bas.app.shift.models.vLngOrLng
import bas.app.shift.services.LocationService
import bas.app.shift.services.ServerService
import bas.app.shift.utils.MapPointsRenderer
import bas.app.shift.utils.PointVisualizer
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.filterNotNull
import java.text.SimpleDateFormat
import java.util.*

class EkatMaps : AppCompatActivity(), OnMapReadyCallback {

    private var currentLocation: Location? = null
    private var locationUpdateJob: Job? = null
    private var updatePointsRunnable: Runnable? = null
    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityEkatMapsBinding
    private var cancellationTokenSource = CancellationTokenSource()
    private lateinit var pointsRenderer: MapPointsRenderer
    private val handler = Handler(Looper.getMainLooper())
    private val pointsUpdateInterval = 10000L // 10 секунд
    private var lastPointsUpdate = 0L
    private var isMgUser = false

    /** Дисциплина «Экстрасенсорика»: такому игроку доступно чтение ауры места. */
    private var isExtrasensory = false
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
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        if (mapFragment == null) {
            // Гонка восстановления состояния/раннего finish — фрагмент карты ещё не приложен.
            // Раньше здесь был непроверенный `as`, падавший с ClassCastException/NPE.
            LogHelper.w("onResume: фрагмент карты не найден, пропускаем инициализацию")
            return
        }
        mapFragment.getMapAsync(this)
        LogHelper.d("onResume: карта загружается асинхронно для ${if (isMgUser) "MG" else "обычного"} пользователя")
    }

    override fun onPause() {
        super.onPause()
        LogHelper.d("onPause: приостановка активности карты")
        updatePointsRunnable?.let { handler.removeCallbacks(it) }

        locationUpdateJob?.cancel()
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
        pointsRenderer = MapPointsRenderer(mMap, isMgUser)
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

        // Клик по кругу trackable-точки: единственный способ узнать, что сюда нужен мастер,
        // НЕ доходя до места (маркер у обычного игрока появляется только внутри радиуса).
        mMap.setOnCircleClickListener { circle ->
            val point = pointsRenderer.findPointForCircle(circle)
            if (point?.trackable == 1) {
                showTrackableWarningDialog()
            }
        }

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
        val point = pointsRenderer.findPointForMarker(marker)
        if (point == null) {
            LogHelper.w("Точка не найдена для маркера: ${marker.title}")
            return
        }

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
        
        val distance = pointsRenderer.calculateDistance(
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

        val myUserId = UserPrefsHelper.getUserId(this)
        val holder = point.assigned_player

        // Фамильяр занят кем-то другим. Данные из последнего опроса точек — сервер к этому
        // моменту уже снял привязки, молчавшие дольше 15 минут, так что «занято» тут живое.
        if (holder != null && holder != myUserId) {
            LogHelper.d("Фамильяр ${point.pointId} занят игроком $holder")
            showFamiliarBusyDialog()
            return
        }

        val familiarName = pointsRenderer.getPointTitle(PointType.fromServerValue(point.type))
        val message = getString(R.string.familiar_dialog_message, familiarName, distance.toInt())

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.familiar_dialog_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.familiar_talk_start_button)) { _, _ ->
                bindAndOpenFamiliar(point, myUserId)
            }
            .setNegativeButton(getString(R.string.familiar_cancel_button), null)
            .show()
    }

    /**
     * Занимает фамильяра под игрока и открывает чат.
     *
     * Не занял — не пускаем, какой бы ни была причина. Смысл механики в том, что говорящий
     * ровно один; если пускать при сбое сети, двое с плохой связью окажутся в чате вдвоём —
     * ровно та ситуация, ради которой всё и делалось.
     */
    private fun bindAndOpenFamiliar(point: Point, myUserId: String) {
        scope.launch {
            try {
                val response = ServerService.bindFamiliar(point.pointId, myUserId)
                when {
                    response.isSuccessful -> {
                        LogHelper.d("Фамильяр ${point.pointId} занят игроком $myUserId")
                        openFamiliarFound(point)
                        updatePointsFromServer()
                    }
                    response.code() == 409 -> {
                        LogHelper.d("Фамильяр ${point.pointId} перехвачен другим игроком")
                        showFamiliarBusyDialog()
                        updatePointsFromServer()
                    }
                    else -> {
                        LogHelper.e("Не удалось занять фамильяра: ${response.code()}")
                        showFamiliarBindFailedDialog()
                    }
                }
            } catch (e: Exception) {
                LogHelper.e("Исключение при попытке занять фамильяра: ${e.message}")
                showFamiliarBindFailedDialog()
            }
        }
    }

    private fun showOkDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun showFamiliarBusyDialog() =
        showOkDialog(getString(R.string.familiar_busy_title), getString(R.string.familiar_busy_message))

    private fun showFamiliarBindFailedDialog() =
        showOkDialog(getString(R.string.familiar_bind_failed_title), getString(R.string.familiar_bind_failed))

    private fun showTrackableWarningDialog() =
        showOkDialog(getString(R.string.point_trackable_player_title), getString(R.string.point_trackable_player_message))

    private fun showBasicPointInfoDialog(point: Point) {
        LogHelper.d("Показ базовой информации о точке: ${point.pointId}")

        val title = pointsRenderer.getPointTitle(PointType.fromServerValue(point.type))
        val trackableLine = if (point.trackable == 1) {
            "⚠ " + getString(R.string.point_trackable_player_message) + "\n\n"
        } else ""
        val message = if (point.type == "USER") "Здесь кто-то есть (мастер или игротех)"
        else trackableLine +
                getString(R.string.point_basic_radius, point.radius) + "\n" +
                getString(R.string.point_basic_description, point.description ?: getString(R.string.point_no_description)) + "\n" +
                if (point.textToShowOnEnter.isNullOrEmpty()) "" else "При входе: ${point.textToShowOnEnter}"

        val tv = TextView(this).apply {
            text = message
            setTextIsSelectable(true)
            setPadding(48, 32, 48, 16)
            LinkifyCompat.addLinks(this, Linkify.ALL)
            movementMethod = LinkMovementMethod.getInstance()
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(tv)
            .setPositiveButton("OK", null)

        if (canReadAuraOf(point)) {
            builder.setNeutralButton(getString(R.string.aura_of_place_button)) { _, _ ->
                showAuraOfPlaceDialog(point)
            }
        }

        builder.show()
    }

    /**
     * Кнопка чтения ауры места. Маркер игрок и так видит только внутри радиуса, но радиус
     * бывает и в километр, поэтому отдельно требуем подойти вплотную — аура читается с места,
     * а не с другого конца парка. У USER-точек (человек на карте) ауры места нет.
     */
    private fun canReadAuraOf(point: Point): Boolean {
        if (!isExtrasensory || point.type == "USER") return false

        val location = currentLocation ?: return false
        val distance = pointsRenderer.calculateDistance(
            LatLng(location.latitude, location.longitude),
            LatLng(point.lat, point.lng)
        )
        return distance <= AURA_READ_MAX_DISTANCE_M
    }

    private fun showAuraOfPlaceDialog(point: Point) {
        val auraText = point.aura_text?.takeIf { it.isNotBlank() }
        LogHelper.d("Чтение ауры места ${point.pointId}: ${if (auraText == null) "текста нет" else "текст есть"}")

        val tv = TextView(this).apply {
            text = auraText ?: getString(R.string.aura_of_place_empty)
            setTextIsSelectable(true)
            setPadding(48, 32, 48, 16)
            LinkifyCompat.addLinks(this, Linkify.ALL)
            movementMethod = LinkMovementMethod.getInstance()
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.aura_of_place_title))
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
            // pointId нужен дальше в чате, чтобы продлевать привязку на каждое сообщение
            putExtra("point_id", point.pointId)
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
        val point = pointsRenderer.findPointForMarker(marker)
        if (point == null) {
            LogHelper.w("Точка не найдена для маркера: ${marker.title}")
            return
        }

        LogHelper.d("Информация о точке: ID=${point.pointId}, тип=${point.type}")

        val dialogBinding = DialogPointInfoBinding.inflate(LayoutInflater.from(this))

        // Заполняем информацию о точке
        dialogBinding.tvPointTitle.text = pointsRenderer.getPointTitle(PointType.fromServerValue(point.type))
        dialogBinding.tvPointRadius.text = getString(R.string.point_radius_label) + " " + point.radius + "м"
        dialogBinding.tvPointCoordinates.text = getString(R.string.point_coordinates_label) + " " + String.format("%.6f", point.lat) + ", " + String.format("%.6f", point.lng)
        dialogBinding.tvPointDescription.text = getString(R.string.point_description_label) + " " + (point.description ?: getString(R.string.point_no_description))
        dialogBinding.tvPointTextOnEnter.text = getString(R.string.point_text_on_enter_label) + " " + (point.textToShowOnEnter ?: getString(R.string.point_no_text_on_enter))
        // Aura of the place: what a psychic reads here. Editable, because the MG cannot see
        // the current text anywhere else, and a typo used to mean recreating the point.
        val initialAura = point.aura_text?.takeIf { it.isNotBlank() } ?: ""
        dialogBinding.etAuraText.setText(initialAura)

        listOf(dialogBinding.tvPointDescription, dialogBinding.tvPointTextOnEnter).forEach { tv ->
            tv.setTextIsSelectable(true)
            LinkifyCompat.addLinks(tv, Linkify.ALL)
            tv.movementMethod = LinkMovementMethod.getInstance()
        }

        // Скрытость и «нужен мастер» (по доку приходят 0/1)
        val initialHidden = point.hidden == 1
        val initialTrackable = point.trackable == 1
        dialogBinding.cbHidden.isChecked = initialHidden
        dialogBinding.cbTrackable.isChecked = initialTrackable

        // Создаем bottom sheet
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogBinding.root)
        dialog.setCancelable(true)

        dialogBinding.btnSavePoint.setOnClickListener {
            val newHidden = dialogBinding.cbHidden.isChecked
            val newTrackable = dialogBinding.cbTrackable.isChecked
            val newAura = dialogBinding.etAuraText.text.toString().trim()
            if (newHidden == initialHidden && newTrackable == initialTrackable && newAura == initialAura) {
                dialog.dismiss()
                return@setOnClickListener
            }

            scope.launch {
                try {
                    // Шлём только реально изменившееся: null в теле означает «не трогать».
                    // Для ауры пустая строка — это «стереть», её отличаем от null осознанно.
                    val response = ServerService.updatePoint(
                        pointId = point.pointId,
                        hidden = newHidden.takeIf { it != initialHidden },
                        trackable = newTrackable.takeIf { it != initialTrackable },
                        auraText = newAura.takeIf { it != initialAura },
                    )
                    if (response.isSuccessful) {
                        Toast.makeText(this@EkatMaps, getString(R.string.save), Toast.LENGTH_SHORT).show()
                        updatePointsFromServer()
                    } else {
                        Toast.makeText(this@EkatMaps, "Ошибка при сохранении", Toast.LENGTH_SHORT).show()
                        LogHelper.e("Ошибка при обновлении точки: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@EkatMaps, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    LogHelper.e("Исключение при обновлении hidden: ${e.message}")
                }
            }
            dialog.dismiss()
        }

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

        val createdAtFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val nowCalendar = Calendar.getInstance()

        fun updateRadiusUi() {
            val isCustom = dialogBinding.cbCustomRadius.isChecked
            dialogBinding.tvRadiusValue.visibility = if (isCustom) View.VISIBLE else View.GONE
            dialogBinding.sliderRadius.visibility = if (isCustom) View.VISIBLE else View.GONE

            if (isCustom) {
                val radius = PointRadiusMath.radiusFromSlider(dialogBinding.sliderRadius.value)
                dialogBinding.tvRadiusValue.text = getString(R.string.point_radius_current, PointRadiusMath.formatRadius(radius))
            }
        }

        fun selectedCreatedAtString(): String {
            val cal = Calendar.getInstance()
            // month/year всегда текущие
            cal.set(Calendar.YEAR, nowCalendar.get(Calendar.YEAR))
            cal.set(Calendar.MONTH, nowCalendar.get(Calendar.MONTH))
            cal.set(Calendar.DAY_OF_MONTH, dialogBinding.npDay.value)
            cal.set(Calendar.HOUR_OF_DAY, dialogBinding.npHour.value)
            cal.set(Calendar.MINUTE, dialogBinding.npMinute.value)
            cal.set(Calendar.SECOND, 0)
            return createdAtFormat.format(cal.time)
        }

        fun updateCreatedAtUi() {
            val isCustom = dialogBinding.cbCustomCreatedAt.isChecked
            dialogBinding.tvCreatedAtValue.visibility = if (isCustom) View.VISIBLE else View.GONE
            dialogBinding.layoutCreatedAtPickers.visibility = if (isCustom) View.VISIBLE else View.GONE

            if (isCustom) {
                dialogBinding.tvCreatedAtValue.text =
                    getString(R.string.point_created_at_current, selectedCreatedAtString())
            }
        }
        
        // Показываем координаты
        dialogBinding.tvCoordinates.text = getString(R.string.point_coordinates_label) + " " + String.format("%.6f", latLng.latitude) + ", " + String.format("%.6f", latLng.longitude)
        
        // Настраиваем спиннер типов точек (исключаем USER и служебный UNKNOWN)
        val pointTypes = PointType.values()
            .filter { it != PointType.USER && it != PointType.UNKNOWN }
        val pointTypeNames = pointTypes.map { pointsRenderer.getPointTitle(it) }
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

        // Радиус: нелинейный слайдер 5м..1500м
        dialogBinding.sliderRadius.value = PointRadiusMath.sliderFromRadius(PointRadiusMath.DEFAULT_CUSTOM_RADIUS_METERS)
            .coerceIn(dialogBinding.sliderRadius.valueFrom, dialogBinding.sliderRadius.valueTo)
        dialogBinding.cbCustomRadius.setOnCheckedChangeListener { _, _ -> updateRadiusUi() }
        dialogBinding.sliderRadius.addOnChangeListener { _, _, _ -> updateRadiusUi() }
        updateRadiusUi()

        // createdAt: год/месяц текущие, выбираем день/час/минуту
        dialogBinding.npDay.minValue = 1
        dialogBinding.npDay.maxValue = nowCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        dialogBinding.npDay.value = nowCalendar.get(Calendar.DAY_OF_MONTH)

        dialogBinding.npHour.minValue = 0
        dialogBinding.npHour.maxValue = 23
        dialogBinding.npHour.value = nowCalendar.get(Calendar.HOUR_OF_DAY)

        dialogBinding.npMinute.minValue = 0
        dialogBinding.npMinute.maxValue = 59
        dialogBinding.npMinute.value = nowCalendar.get(Calendar.MINUTE)

        val createdAtChangeListener = NumberPicker.OnValueChangeListener { _, _, _ -> updateCreatedAtUi() }
        dialogBinding.npDay.setOnValueChangedListener(createdAtChangeListener)
        dialogBinding.npHour.setOnValueChangedListener(createdAtChangeListener)
        dialogBinding.npMinute.setOnValueChangedListener(createdAtChangeListener)

        dialogBinding.cbCustomCreatedAt.setOnCheckedChangeListener { _, _ -> updateCreatedAtUi() }
        updateCreatedAtUi()
        
        // Создаем bottom sheet
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogBinding.root)
        dialog.setCancelable(true)
        
        // Обработчик кнопки создания
        dialogBinding.btnCreatePoint.setOnClickListener {
            val selectedPosition = dialogBinding.spinnerPointType.selectedItemPosition
            val selectedType = pointTypeValues[selectedPosition]
            val textToShowOnEnter = dialogBinding.etTextToShowOnEnter.text.toString()
            val auraText = dialogBinding.etAuraText.text.toString()
            val isHidden = dialogBinding.cbHidden.isChecked
            val isTrackable = dialogBinding.cbTrackable.isChecked
            val radius: Double? = if (dialogBinding.cbCustomRadius.isChecked) {
                PointRadiusMath.radiusFromSlider(dialogBinding.sliderRadius.value)
            } else {
                null
            }
            val createdAt: String? = if (dialogBinding.cbCustomCreatedAt.isChecked) {
                selectedCreatedAtString()
            } else {
                null
            }
            
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
                        type = selectedType,
                        lat = latLng.latitude,
                        lng = latLng.longitude,
                        radius = radius, // null => серверный дефолт
                        ownerId = userId,
                        description = description,
                        textToShowOnEnter = textToShowOnEnter.takeIf { it.isNotEmpty() },
                        aura_text = auraText.takeIf { it.isNotBlank() },
                        hidden = isHidden,
                        trackable = isTrackable,
                        createdAt = createdAt,
                    )
                    
                    LogHelper.d("Отправка запроса на создание точки: $pointRequest")
                    val response = ServerService.createPoint(pointRequest)
                    if (response.isSuccessful) {
                        Toast.makeText(this@EkatMaps, "Точка создана", Toast.LENGTH_SHORT).show()
                        LogHelper.d("Точка успешно создана")
                        // Для маленьких радиусов — автоматически приблизим камеру,
                        // иначе круг 5–20м выглядит как "точка" на обычном зуме.
                        withContext(Dispatchers.Main) {
                            val zoom = PointRadiusMath.zoomForRadiusMeters(radius ?: 0.0)
                            if (zoom != null) {
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
                            }
                        }
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
                if (serverPoints == null) {
                    // Сетевой сбой: оставляем текущие точки на карте как есть,
                    // не мигаем и не стираем их из-за разового обрыва.
                    LogHelper.w("EkatMaps: не удалось получить точки (сеть), оставляем текущие")
                } else if (serverPoints.isEmpty()) {
                    LogHelper.d("Сервер не вернул точки")
                } else {
                    LogHelper.d("Получено ${serverPoints.size} точек с сервера")

                    pointsRenderer.syncPoints(serverPoints)

                    // Обновляем карту только если есть текущая локация
                    if (currentLocation != null) {
                        updateForLocation()
                    } else {
                        LogHelper.d("Локация недоступна, маркеры будут добавлены позже")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogHelper.e("Ошибка при обновлении точек с сервера: ${e.message}")
            }
        }
    }

    private fun updateForLocation() {
        val location = currentLocation
        if (location == null) {
            LogHelper.d("Текущая локация недоступна, пропускаем обновление карты")
            return
        }
        pointsRenderer.refreshMarkersForLocation(location)
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
        locationUpdateJob = lifecycleScope.launch {
            LocationService.locationSource.filterNotNull().collect { location ->
                currentLocation = location
                LogHelper.d("Обновление местоположения через LocationService: ${location.latitude}, ${location.longitude}")

                // Центрируем карту на текущем местоположении при первом получении
                if (!pointsRenderer.hasLocationMarker) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                }

                // Обновляем карту
                updateForLocation()
            }
        }
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
        isExtrasensory = UserPrefsHelper.getUserData(this)?.isExtrasensory == true
        LogHelper.d("Проверка MG пользователя: $userName, результат: $isMgUser, экстрасенс: $isExtrasensory")
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
        if (!::pointsRenderer.isInitialized) {
            // Карта ещё грузится асинхронно (onMapReady не вызван) — до этого момента точек
            // ещё нет и искать некого. Раньше pointsOfInterest было полем, инициализированным
            // сразу, поэтому этот случай просто давал пустой список — сохраняем то же поведение.
            Toast.makeText(this, "Игроки не найдены", Toast.LENGTH_SHORT).show()
            return
        }
        val userPoints = pointsRenderer.usersSnapshot()
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

        /** Насколько близко надо подойти, чтобы прочитать ауру места. Как у фамильяров. */
        private const val AURA_READ_MAX_DISTANCE_M = 50.0
    }
}