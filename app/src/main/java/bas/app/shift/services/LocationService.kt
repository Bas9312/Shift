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
import bas.app.shift.models.Point
import bas.app.shift.models.FamiliarData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import android.os.Handler
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.api.RetrofitClient
import bas.app.shift.models.User
import bas.app.shift.models.Message
import bas.app.shift.models.Chat
import bas.app.shift.ui.FamiliarFoundActivity
import bas.app.shift.ui.ProfileActivity
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
            startForeground(NOTIFICATION_ID, createNotification())

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
                showNotification(
                    "⚠️ Неприятный эффект!",
                    point.textToShowOnEnter ?: "Почему-то тут нет текста, обратитесь к МГ!",
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
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val familiarId = point.description ?: "familiar_malachite_lizard"
        val familiarName = FamiliarData.getNameById(familiarId)

        // Делаем Intent уникальным для сравнения PendingIntent'ов:
        val intent = Intent(this, FamiliarFoundActivity::class.java).apply {
            putExtra("familiar_id", familiarId)
            // Любой уникальный признак: action И/ИЛИ data
            action = "bas.app.shift.ACTION_OPEN_FAMILIAR.$familiarId"
            data = Uri.parse("shift://familiar/$familiarId")
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            /* уникальный requestCode */ familiarId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationText = "Вы чувствуете здесь присутствие $familiarName. Это существо готово к общению. Нажмите на уведомление, чтобы пообщаться с ним!"

        val notification = NotificationCompat.Builder(this, POINTS_CHANNEL_ID)
            .setContentTitle("🐉 Фамильяр рядом!")
            .setContentText("Вы чувствуете здесь $familiarName. Нажмите для общения!")
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText).setBigContentTitle("🐉 Фамильяр рядом!"))
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 500, 200, 500))
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
        
        val intent = Intent(this, bas.app.shift.ui.NotificationDetailActivity::class.java)
        intent.putExtra(bas.app.shift.ui.NotificationDetailActivity.EXTRA_TITLE, title)
        intent.putExtra(bas.app.shift.ui.NotificationDetailActivity.EXTRA_TEXT, text)
        val pendingIntent = PendingIntent.getActivity(
            this, notificationId, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Создаем стиль для большого текста
        val bigTextStyle = NotificationCompat.BigTextStyle()
            .bigText(text)
            .setBigContentTitle(title)
        
        val notification = NotificationCompat.Builder(this, POINTS_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text.take(80)) // Краткий текст для свернутого состояния
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
                            val changes = compareProfiles(oldProfile, userServer)
                            if (changes.isNotEmpty()) {
                                LogHelper.d("LocationService: Обнаружены изменения в профиле: ${changes.size}")
                                showProfileChangeNotifications(changes)
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
    
    private fun compareProfiles(oldProfile: User, newProfile: User): List<ProfileChange> {
        val changes = mutableListOf<ProfileChange>()
        
        // Сравниваем дисциплины (теперь List<NamedEntity>)
        if (oldProfile.disciplines != newProfile.disciplines) {
            val oldDisciplines = oldProfile.disciplines.map { it.name }.sorted()
            val newDisciplines = newProfile.disciplines.map { it.name }.sorted()
            
            if (oldDisciplines != newDisciplines) {
                val added = newDisciplines - oldDisciplines
                val removed = oldDisciplines - newDisciplines
                
                added.forEach { disciplineName ->
                    changes.add(ProfileChange(
                        fieldName = "Дисциплина",
                        oldValue = null,
                        newValue = disciplineName,
                        changeType = ChangeType.ADDED
                    ))
                }
                
                removed.forEach { disciplineName ->
                    changes.add(ProfileChange(
                        fieldName = "Дисциплина",
                        oldValue = disciplineName,
                        newValue = null,
                        changeType = ChangeType.REMOVED
                    ))
                }
            }
        }
        
        // Сравниваем модули (теперь List<NamedEntity>)
        if (oldProfile.modules != newProfile.modules) {
            val oldModules = oldProfile.modules.map { it.name }.sorted()
            val newModules = newProfile.modules.map { it.name }.sorted()
            
            if (oldModules != newModules) {
                val added = newModules - oldModules
                val removed = oldModules - newModules
                
                added.forEach { moduleName ->
                    changes.add(ProfileChange(
                        fieldName = "Модуль",
                        oldValue = null,
                        newValue = moduleName,
                        changeType = ChangeType.ADDED
                    ))
                }
                
                removed.forEach { moduleName ->
                    changes.add(ProfileChange(
                        fieldName = "Модуль",
                        oldValue = moduleName,
                        newValue = null,
                        changeType = ChangeType.REMOVED
                    ))
                }
            }
        }
        
        // Сравниваем способности (теперь List<Ability>)
        if (oldProfile.abilities != newProfile.abilities) {
            val oldAbilities = oldProfile.abilities.map { "${it.type}: ${it.description}" }.sorted()
            val newAbilities = newProfile.abilities.map { "${it.type}: ${it.description}" }.sorted()
            
            if (oldAbilities != newAbilities) {
                val added = newAbilities - oldAbilities
                val removed = oldAbilities - newAbilities
                
                added.forEach { ability ->
                    changes.add(ProfileChange(
                        fieldName = "Способность",
                        oldValue = null,
                        newValue = ability,
                        changeType = ChangeType.ADDED
                    ))
                }
                
                removed.forEach { ability ->
                    changes.add(ProfileChange(
                        fieldName = "Способность",
                        oldValue = ability,
                        newValue = null,
                        changeType = ChangeType.REMOVED
                    ))
                }
            }
        }
        
        // Сравниваем артефакты
        if (oldProfile.artifacts != newProfile.artifacts) {
            val oldArtifacts = oldProfile.artifacts.map { it.name }.sorted()
            val newArtifacts = newProfile.artifacts.map { it.name }.sorted()
            
            if (oldArtifacts != newArtifacts) {
                val added = newArtifacts - oldArtifacts
                val removed = oldArtifacts - newArtifacts
                
                added.forEach { artifact ->
                    changes.add(ProfileChange(
                        fieldName = "Артефакт",
                        oldValue = null,
                        newValue = artifact,
                        changeType = ChangeType.ADDED
                    ))
                }
                
                removed.forEach { artifact ->
                    changes.add(ProfileChange(
                        fieldName = "Артефакт",
                        oldValue = artifact,
                        newValue = null,
                        changeType = ChangeType.REMOVED
                    ))
                }
            }
        }
        
        // Сравниваем инструмент
        if (oldProfile.instrument != newProfile.instrument) {
            changes.add(ProfileChange(
                fieldName = "Инструмент",
                oldValue = oldProfile.instrument,
                newValue = newProfile.instrument,
                changeType = ChangeType.CHANGED
            ))
        }
        
        // Сравниваем фамильяра
        if (oldProfile.familiar != newProfile.familiar) {
            changes.add(ProfileChange(
                fieldName = "Фамильяр",
                oldValue = oldProfile.familiar,
                newValue = newProfile.familiar,
                changeType = ChangeType.CHANGED
            ))
        }
        
        // Сравниваем misc (дополнительные параметры)
        if (oldProfile.misc != newProfile.misc) {
            val oldMisc = oldProfile.misc.sorted()
            val newMisc = newProfile.misc.sorted()
            
            if (oldMisc != newMisc) {
                val added = newMisc - oldMisc
                val removed = oldMisc - newMisc
                
                added.forEach { miscItem ->
                    changes.add(ProfileChange(
                        fieldName = "Доп. параметр",
                        oldValue = null,
                        newValue = miscItem,
                        changeType = ChangeType.ADDED
                    ))
                }
                
                removed.forEach { miscItem ->
                    changes.add(ProfileChange(
                        fieldName = "Доп. параметр",
                        oldValue = miscItem,
                        newValue = null,
                        changeType = ChangeType.REMOVED
                    ))
                }
            }
        }
        
        // Сравниваем имя игрока
        if (oldProfile.playerName != newProfile.playerName) {
            changes.add(ProfileChange(
                fieldName = "Имя игрока",
                oldValue = oldProfile.playerName,
                newValue = newProfile.characterName,
                changeType = ChangeType.CHANGED
            ))
        }
        
        // Сравниваем имя персонажа
        if (oldProfile.characterName != newProfile.characterName) {
            changes.add(ProfileChange(
                fieldName = "Имя персонажа",
                oldValue = oldProfile.characterName,
                newValue = newProfile.characterName,
                changeType = ChangeType.CHANGED
            ))
        }
        
        // Сравниваем эффекты
        if (oldProfile.effects != newProfile.effects) {
            val oldEffects = oldProfile.effects?.map { it.textToShowPlayers }?.sorted() ?: emptyList()
            val newEffects = newProfile.effects?.map { it.textToShowPlayers }?.sorted() ?: emptyList()
            
            if (oldEffects != newEffects) {
                val added = newEffects - oldEffects
                val removed = oldEffects - newEffects
                
                added.forEach { effectText ->
                    changes.add(ProfileChange(
                        fieldName = "Эффект",
                        oldValue = null,
                        newValue = effectText,
                        changeType = ChangeType.ADDED
                    ))
                }
                
                removed.forEach { effectText ->
                    changes.add(ProfileChange(
                        fieldName = "Эффект",
                        oldValue = effectText,
                        newValue = null,
                        changeType = ChangeType.REMOVED
                    ))
                }
            }
        }
        
        return changes
    }
    
    private fun showProfileChangeNotifications(changes: List<ProfileChange>) {
        changes.forEachIndexed { index, change ->
            val notificationId = 2000 + index // Уникальный ID для каждого уведомления об изменении профиля
            
            val intent = Intent(this, ProfileActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Специальная обработка для эффектов
            val (title, priority, vibration) = if (change.fieldName == "Эффект") {
                when (change.changeType) {
                    ChangeType.ADDED -> Triple("✨ Новый эффект!", NotificationCompat.PRIORITY_HIGH, longArrayOf(0, 500, 200, 500))
                    ChangeType.REMOVED -> Triple("❌ Эффект исчез", NotificationCompat.PRIORITY_HIGH, longArrayOf(0, 500, 200, 500))
                    else -> Triple("Профиль обновлен", NotificationCompat.PRIORITY_DEFAULT, null)
                }
            } else {
                Triple("Профиль обновлен", NotificationCompat.PRIORITY_DEFAULT, null)
            }
            
            val notification = NotificationCompat.Builder(this, POINTS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_icon)
                .setContentTitle(title)
                .setContentText(formatChangeMessage(change))
                .setPriority(priority)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .apply {
                    if (vibration != null) {
                        setVibrate(vibration)
                    }
                    if (change.fieldName == "Эффект") {
                        setDefaults(NotificationCompat.DEFAULT_ALL)
                    }
                }
                .build()
            
            try {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(notificationId, notification)
                LogHelper.d("LocationService: Показано уведомление об изменении профиля: ${change.fieldName}")
            } catch (e: Exception) {
                LogHelper.e("LocationService: Ошибка показа уведомления об изменении профиля: ${e.message}")
            }
        }
    }
    
    private fun formatChangeMessage(change: ProfileChange): String {
        return when (change.changeType) {
            ChangeType.ADDED -> {
                if (change.fieldName == "Эффект") {
                    "Получен новый эффект: ${change.newValue}"
                } else {
                    "${change.fieldName} добавлен: ${change.newValue}"
                }
            }
            ChangeType.REMOVED -> {
                if (change.fieldName == "Эффект") {
                    "Эффект исчез: ${change.oldValue}"
                } else {
                    "${change.fieldName} удален: ${change.oldValue}"
                }
            }
            ChangeType.CHANGED -> {
                val newValue = change.newValue ?: "не указан"
                if (newValue.length <= 30) {
                    "${change.fieldName} изменен: $newValue"
                } else {
                    "${change.fieldName} изменен"
                }
            }
        }
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

    data class ProfileChange(
        val fieldName: String,
        val oldValue: String?,
        val newValue: String?,
        val changeType: ChangeType
    )

    enum class ChangeType {
        ADDED, REMOVED, CHANGED
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
        
        try {
            if (userId.startsWith("MG_")) {
                // Для MG пользователей используем getChats для получения списка чатов
                val chatsResponse = RetrofitClient.messagesApi.getChats(userId)
                chatsResponse.enqueue(object : retrofit2.Callback<List<Chat>> {
                    override fun onResponse(call: retrofit2.Call<List<Chat>>, response: retrofit2.Response<List<Chat>>) {
                        if (response.isSuccessful) {
                            val chats = response.body() ?: emptyList()
                            checkForNewMessagesInChats(userId, chats)
                        }
                    }
                    
                    override fun onFailure(call: retrofit2.Call<List<Chat>>, t: Throwable) {
                        LogHelper.e("LocationService: Ошибка при получении чатов: ${t.message}")
                    }
                })
            } else {
                // Для обычных пользователей используем getMessages
                val messagesResponse = RetrofitClient.messagesApi.getMessages(
                    userId = userId,
                    limit = 10,
                    offset = 0,
                    type = "private"
                )
                messagesResponse.enqueue(object : retrofit2.Callback<List<Message>> {
                    override fun onResponse(call: retrofit2.Call<List<Message>>, response: retrofit2.Response<List<Message>>) {
                        if (response.isSuccessful) {
                            val messages = response.body() ?: emptyList()
                            checkForNewMessagesInList(userId, messages)
                        }
                    }
                    
                    override fun onFailure(call: retrofit2.Call<List<Message>>, t: Throwable) {
                        LogHelper.e("LocationService: Ошибка при получении сообщений: ${t.message}")
                    }
                })
            }
        } catch (e: Exception) {
            LogHelper.e("LocationService: Ошибка при получении сообщений: ${e.message}")
        }
    }

    private fun checkForNewMessagesInChats(userId: String, chats: List<Chat>) {
        val prefs = getSharedPreferences("messages_cache", MODE_PRIVATE)
        val lastKnownMessageIds = prefs.getStringSet("last_known_message_ids_$userId", emptySet()) ?: emptySet()
        
        val newMessageIds = mutableSetOf<String>()
        var hasNewMessages = false
        val newMessageIdsToNotify = mutableSetOf<String>()
        
        // Собираем ID всех сообщений из чатов
        chats.forEach { chat ->
            chat.lastMessage?.let { message ->
                val messageId = message.id.toString()
                newMessageIds.add(messageId)
                
                // Проверяем, новое ли это сообщение и не от MG
                if (!lastKnownMessageIds.contains(messageId) && !message.senderId.startsWith("MG_")) {
                    hasNewMessages = true
                    newMessageIdsToNotify.add(messageId)
                    LogHelper.d("LocationService: Найдено новое сообщение от ${message.senderId} (id=${message.id})")
                }
            }
        }
        
        // Сохраняем новые ID сообщений
        prefs.edit().putStringSet("last_known_message_ids_$userId", newMessageIds).apply()
        
        if (hasNewMessages) {
            // Дополнительная проверка: не показываем уведомление, если уже показывали для этих сообщений
            val notifiedMessageIds = prefs.getStringSet("notified_message_ids_$userId", emptySet()) ?: emptySet()
            val shouldNotify = newMessageIdsToNotify.any { !notifiedMessageIds.contains(it) }
            
            if (shouldNotify) {
                // Сохраняем ID уведомленных сообщений
                val updatedNotifiedIds = notifiedMessageIds + newMessageIdsToNotify
                prefs.edit().putStringSet("notified_message_ids_$userId", updatedNotifiedIds).apply()
                
                showMessagesNotification(1, true) // true = открыть список чатов
            }
        }
    }

    private fun checkForNewMessagesInList(userId: String, messages: List<Message>) {
        val prefs = getSharedPreferences("messages_cache", MODE_PRIVATE)
        val lastKnownMessageIds = prefs.getStringSet("last_known_message_ids_$userId", emptySet()) ?: emptySet()
        
        val newMessageIds = mutableSetOf<String>()
        var hasNewMessages = false
        val newMessageIdsToNotify = mutableSetOf<String>()
        
        // Проверяем каждое сообщение
        messages.forEach { message ->
            val messageId = message.id.toString()
            newMessageIds.add(messageId)
            
            // Проверяем, новое ли это сообщение и не наше
            if (!lastKnownMessageIds.contains(messageId) && message.senderId != userId) {
                hasNewMessages = true
                newMessageIdsToNotify.add(messageId)
                LogHelper.d("LocationService: Найдено новое сообщение от ${message.senderId}: ${message.content}")
            }
        }
        
        // Сохраняем новые ID сообщений
        prefs.edit().putStringSet("last_known_message_ids_$userId", newMessageIds).apply()
        
        if (hasNewMessages) {
            // Дополнительная проверка: не показываем уведомление, если уже показывали для этих сообщений
            val notifiedMessageIds = prefs.getStringSet("notified_message_ids_$userId", emptySet()) ?: emptySet()
            val shouldNotify = newMessageIdsToNotify.any { !notifiedMessageIds.contains(it) }
            
            if (shouldNotify) {
                // Сохраняем ID уведомленных сообщений
                val updatedNotifiedIds = notifiedMessageIds + newMessageIdsToNotify
                prefs.edit().putStringSet("notified_message_ids_$userId", updatedNotifiedIds).apply()
                
                showMessagesNotification(1, false) // false = открыть чат
            }
        }
    }

    private fun showMessagesNotification(unreadCount: Int, isMG: Boolean = false) {
        val message = if (unreadCount == 1) {
            "У вас 1 новое сообщение"
        } else {
            "У вас $unreadCount новых сообщений"
        }
        
        // Создаем канал уведомлений для Android 8.0+
        createMessagesNotificationChannel()
        
        // Создаем Intent для открытия нужного экрана
        val intent = if (isMG) {
            // Для MG пользователей открываем список чатов
            Intent(this, bas.app.shift.ui.ChatsListActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        } else {
            // Для обычных пользователей открываем чат с MG
            Intent(this, bas.app.shift.ui.MessagesChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Создаем уведомление
        val notification = NotificationCompat.Builder(this, "messages_channel")
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle("Новые сообщения")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        // Показываем уведомление
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)
        
        LogHelper.d("LocationService: Показано уведомление о $unreadCount новых сообщениях (isMG: $isMG)")
    }
    
    private fun createMessagesNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "messages_channel",
                "Сообщения",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Уведомления о новых сообщениях"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Метод для очистки кэша сообщений (вызывается при смене пользователя)
    fun clearMessagesCache(userId: String) {
        val prefs = getSharedPreferences("messages_cache", MODE_PRIVATE)
        prefs.edit()
            .remove("last_known_message_ids_$userId")
            .remove("notified_message_ids_$userId")
            .apply()
        LogHelper.d("LocationService: Кэш сообщений очищен для пользователя $userId")
    }

    companion object {
        const val ACTION_START = "bas.app.shift.ACTION_START_LOCATION"
        const val ACTION_STOP = "bas.app.shift.ACTION_STOP_LOCATION"
        private const val PREFS_NAME = "game_state"
        private const val KEY_IN_GAME = "is_in_game"
        private const val CHANNEL_ID = "location_service_channel"
        private const val POINTS_CHANNEL_ID = "points_notifications_channel"
        private const val NOTIFICATION_ID = 1001
        private val _locationSource = MutableStateFlow<Location?>(null)
        val locationSource: StateFlow<Location?> = _locationSource.asStateFlow()
        
        private var instance: LocationService? = null
        
        fun getCurrentLocation(): Location? {
            return instance?.currentLocation
        }
    }
} 