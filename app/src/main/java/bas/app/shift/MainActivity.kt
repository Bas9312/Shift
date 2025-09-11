package bas.app.shift

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import bas.app.shift.api.RetrofitClient
import bas.app.shift.databinding.ActivityMainBinding
import bas.app.shift.helpers.LogHelper
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.models.User
import bas.app.shift.models.Aura
import bas.app.shift.models.AuraHiddenRequest
import bas.app.shift.ui.terminal.TerminalActivity
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

import bas.app.shift.ui.AuraEditorActivity
import bas.app.shift.ui.ArtifactCreatorActivity
import bas.app.shift.ui.MgProfileViewActivity
import bas.app.shift.ui.ArtifactPassportActivity
import bas.app.shift.ui.AuthActivity
import bas.app.shift.ui.FamiliarActivity
import bas.app.shift.services.UpdateService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var isMgUser = false
    private var isExtrasensory = false
    private var currentAura: Aura? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupButtons()
        
        // Запрашиваем разрешения последовательно
        checkPermissionsSequentially()
        
        // Проверяем состояние игры и запускаем сервис если нужно
        checkAndStartLocationService()

    }

    override fun onStart() {
        super.onStart()

        checkIfMgUser()

        // Проверяем, есть ли уже сохраненный профиль
        val savedUser = UserPrefsHelper.getUserData(this)
        if (savedUser != null) {
            // Профиль уже загружен, показываем UI
            LogHelper.d("MainActivity: Профиль уже загружен, показываем UI")
            showContent()
            updateUI()
        } else {
            // Профиль не загружен, показываем лоадер
            LogHelper.d("MainActivity: Профиль не загружен, показываем лоадер")
            showLoading()
        }
        checkUserDisciplines()

        // Проверяем обновления
        checkForUpdates()

        // Проверяем состояние сервиса при запуске активности
        if (!isFinishing && !isDestroyed) {
            checkAndStartLocationService()
            // Логируем текущее состояние
            if (ShiftApplication.instance.isInGame() && ShiftApplication.instance.isLocationServiceRunning()) {
                LogHelper.d("MainActivity: Activity запущена, LocationService активен")
            } else if (ShiftApplication.instance.isInGame() && !ShiftApplication.instance.isLocationServiceRunning()) {
                LogHelper.w("MainActivity: Activity запущена, но LocationService не запущен")
            } else {
                LogHelper.d("MainActivity: Activity запущена, режим 'в игре' выключен")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Проверяем состояние сервиса при возвращении в приложение
        if (!isFinishing && !isDestroyed) {
            checkAndStartLocationService()
            // Логируем текущее состояние
            if (ShiftApplication.instance.isInGame() && ShiftApplication.instance.isLocationServiceRunning()) {
                LogHelper.d("MainActivity: Приложение вернулось из фона, LocationService активен")
            } else if (ShiftApplication.instance.isInGame() && !ShiftApplication.instance.isLocationServiceRunning()) {
                LogHelper.w("MainActivity: Приложение вернулось из фона, но LocationService не запущен")
            } else {
                LogHelper.d("MainActivity: Приложение вернулось из фона, режим 'в игре' выключен")
            }
            
            // Проверяем отложенное обновление
            if (::updateService.isInitialized) {
                LogHelper.d("MainActivity: Проверяем отложенное обновление в onResume")
                updateService.checkPendingUpdate()
            } else {
                LogHelper.d("MainActivity: UpdateService не инициализирован")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (ShiftApplication.instance.isInGame() && ShiftApplication.instance.isLocationServiceRunning()) {
            LogHelper.d("MainActivity: Приложение уходит в фон, LocationService продолжает работать")
        } else if (ShiftApplication.instance.isInGame() && !ShiftApplication.instance.isLocationServiceRunning()) {
            LogHelper.w("MainActivity: Приложение уходит в фон, но LocationService не запущен")
        } else {
            LogHelper.d("MainActivity: Приложение уходит в фон, режим 'в игре' выключен")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (ShiftApplication.instance.isInGame() && ShiftApplication.instance.isLocationServiceRunning()) {
            LogHelper.d("MainActivity: Activity уничтожается, LocationService продолжает работать в фоне")
        } else if (ShiftApplication.instance.isInGame() && !ShiftApplication.instance.isLocationServiceRunning()) {
            LogHelper.w("MainActivity: Activity уничтожается, но LocationService не запущен")
        } else {
            LogHelper.d("MainActivity: Activity уничтожается, режим 'в игре' выключен")
        }
        
        // Останавливаем сервис обновления профиля при уничтожении активности
        // ProfileUpdateService.stopService(this) // Удалено
        LogHelper.d("MainActivity: ProfileUpdateService остановлен при уничтожении")
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = "Shift"
    }
    
    private fun showLoading() {
        binding.loadingLayout.visibility = View.VISIBLE
        binding.contentLayout.visibility = View.GONE
    }
    
    private fun showContent() {
        binding.loadingLayout.visibility = View.GONE
        binding.contentLayout.visibility = View.VISIBLE
    }

    private fun setupButtons() {
        binding.inGameSelector.addOnButtonCheckedListener{ _, checkedId, isChecked -> if (isChecked) onCheckChanged(checkedId) }
        binding.inGameSelector.check( if (ShiftApplication.instance.isInGame()) R.id.inGame else R.id.outGame)

        binding.btnOpenMap.setOnClickListener {
            if (ShiftApplication.instance.isInGame() && hasNotificationPermission()) {
                startActivity(Intent(this, EkatMaps::class.java))
            }
        }

        binding.openTerminalButton.setOnClickListener {
            startActivity(Intent(this, TerminalActivity::class.java))
        }



        binding.openAuraButton.setOnClickListener {
            startActivity(Intent(this, bas.app.shift.ui.AuraScannerActivity::class.java))
        }

        binding.btnOpenProfile.setOnClickListener {
            startActivity(Intent(this, bas.app.shift.ui.ProfileActivity::class.java))
        }

        binding.btnScanArtifact.setOnClickListener {
            startActivity(Intent(this, bas.app.shift.ui.ArtifactScannerActivity::class.java))
        }

        binding.btnFamiliar.setOnClickListener {
            startActivity(Intent(this, FamiliarActivity::class.java))
        }

        // Кнопки для МГ пользователей
        binding.btnAuraEditor.setOnClickListener {
            startActivity(Intent(this, AuraEditorActivity::class.java))
        }

        binding.btnCreateArtifact.setOnClickListener {
            startActivity(Intent(this, ArtifactCreatorActivity::class.java))
        }

        binding.btnMgProfileView.setOnClickListener {
            startActivity(Intent(this, MgProfileViewActivity::class.java))
        }

        binding.btnArtifactPassport.setOnClickListener {
            startActivity(Intent(this, bas.app.shift.ui.ArtifactPassportActivity::class.java))
        }

        binding.btnToggleAuraHidden.setOnClickListener {
            toggleAuraHidden()
        }
    }

    private fun onCheckChanged(checkedId: Int) {
        if (checkedId == R.id.inGame) {
            ShiftApplication.instance.setIsInGame(true)
            updateUI()
            // Запускаем сервис с небольшой задержкой, чтобы UI обновился
            binding.root.post {
                if (hasLocationPermission()) {
                    ShiftApplication.instance.startLocationService()
                    LogHelper.d("MainActivity: LocationService запущен через переключатель")
                } else {
                    LogHelper.w("MainActivity: Нет разрешений на геолокацию для запуска LocationService")
                    // Запрашиваем разрешения
                    requestLocationPermission()
                }
            }
            
            // Запускаем сервис обновления профиля
            // ProfileUpdateService.startService(this) // Удалено
            LogHelper.d("MainActivity: ProfileUpdateService запущен")
        } else {
            ShiftApplication.instance.setIsInGame(false)
            updateUI()
            ShiftApplication.instance.stopLocationService()
            LogHelper.d("MainActivity: LocationService остановлен через переключатель")
            
            // Останавливаем сервис обновления профиля
            // ProfileUpdateService.stopService(this) // Удалено
            LogHelper.d("MainActivity: ProfileUpdateService остановлен")
        }
    }

    private fun updateUI() {
        LogHelper.d("MainActivity: updateUI - isMgUser: $isMgUser, isInGame: ${ShiftApplication.instance.isInGame()}")
        binding.btnOpenMap.isEnabled = ShiftApplication.instance.isInGame() && hasNotificationPermission()
        
        // Для МГ пользователей скрываем терминал и профиль
        if (isMgUser) {
            LogHelper.d("MainActivity: Настройка UI для МГ пользователя")
            binding.openTerminalButton.visibility = View.GONE
            binding.btnOpenProfile.visibility = View.GONE
            binding.openAuraButton.visibility = View.GONE
            binding.btnScanArtifact.visibility = View.GONE
            binding.btnToggleAuraHidden.visibility = View.GONE
            
            // Показываем кнопки для МГ
            binding.btnAuraEditor.visibility = View.VISIBLE
            binding.btnCreateArtifact.visibility = View.VISIBLE
            binding.btnMgProfileView.visibility = View.VISIBLE
            binding.btnArtifactPassport.visibility = View.VISIBLE
            
            // МГ пользователи всегда имеют доступ к кнопкам, независимо от состояния игры
            binding.btnAuraEditor.isEnabled = true
            binding.btnCreateArtifact.isEnabled = true
            binding.btnMgProfileView.isEnabled = true
            binding.btnArtifactPassport.isEnabled = true
            binding.btnOpenMap.isEnabled = true

            LogHelper.d("MainActivity: Кнопки МГ установлены как активные")
            
            // Проверяем состояние кнопок после установки
            LogHelper.d("MainActivity: Состояние кнопок МГ после установки - btnAuraEditor: ${binding.btnAuraEditor.isEnabled}, btnCreateArtifact: ${binding.btnCreateArtifact.isEnabled}, btnMgProfileView: ${binding.btnMgProfileView.isEnabled}, btnArtifactPassport: ${binding.btnArtifactPassport.isEnabled}")
            
            // Проверяем дополнительные свойства кнопок
            LogHelper.d("MainActivity: Дополнительные свойства кнопок МГ - btnAuraEditor: enabled=${binding.btnAuraEditor.isEnabled}, clickable=${binding.btnAuraEditor.isClickable}, focusable=${binding.btnAuraEditor.isFocusable}, visibility=${binding.btnAuraEditor.visibility}")
        } else {
            // Для обычных пользователей показываем стандартные кнопки в зависимости от дисциплин
            val user = UserPrefsHelper.getUserData(this)
            if (user != null) {
                // Проверяем модуль "познание артефактов"
                val hasArtifactKnowledge = user.modules.any { it.name.equals("Познание артефактов", ignoreCase = true) }
                binding.btnScanArtifact.visibility = if (hasArtifactKnowledge) View.VISIBLE else View.GONE
                
                // Проверяем дисциплину "Экстрасенсорика"
                val hasExtrasensory = user.disciplines.any { it.name.equals("Экстрасенсорика", ignoreCase = true) }
                binding.openAuraButton.visibility = if (hasExtrasensory) View.VISIBLE else View.GONE
                
                // Проверяем дисциплину "Шумомантия"
                val hasNoisemancy = user.disciplines.any { it.name.equals("Шумомантия", ignoreCase = true) }
                binding.openTerminalButton.visibility = if (hasNoisemancy) View.VISIBLE else View.GONE
                
                // Проверяем наличие фамильяра
                val hasFamiliar = !user.familiar.isNullOrEmpty()
                binding.btnFamiliar.visibility = if (hasFamiliar) View.VISIBLE else View.GONE
            } else {
                // Если профиль не загружен, скрываем все кнопки
                binding.openTerminalButton.visibility = View.GONE
                binding.openAuraButton.visibility = View.GONE
                binding.btnScanArtifact.visibility = View.GONE
                binding.btnFamiliar.visibility = View.GONE
            }
            
            binding.btnOpenProfile.visibility = View.VISIBLE
            
            // Скрываем кнопки для МГ
            LogHelper.d("MainActivity: Скрываем кнопки МГ для обычного пользователя")
            binding.btnAuraEditor.visibility = View.GONE
            binding.btnCreateArtifact.visibility = View.GONE
            binding.btnMgProfileView.visibility = View.GONE
            binding.btnArtifactPassport.visibility = View.GONE

            // Включаем/выключаем кнопки в зависимости от состояния игры
        }

        binding.openTerminalButton.isEnabled = ShiftApplication.instance.isInGame()
        binding.openAuraButton.isEnabled = ShiftApplication.instance.isInGame()
        binding.btnOpenProfile.isEnabled = ShiftApplication.instance.isInGame()
        binding.btnScanArtifact.isEnabled = ShiftApplication.instance.isInGame()
        binding.btnFamiliar.isEnabled = ShiftApplication.instance.isInGame()
        binding.btnToggleAuraHidden.isEnabled = ShiftApplication.instance.isInGame()
        
        // Обновляем кнопку управления аурой для экстрасенсов
        if (isExtrasensory) {
            updateAuraButton()
        }
        
        // Логируем финальное состояние кнопок МГ
        if (isMgUser) {
            LogHelper.d("MainActivity: Финальное состояние кнопок МГ - btnAuraEditor: ${binding.btnAuraEditor.isEnabled}, btnCreateArtifact: ${binding.btnCreateArtifact.isEnabled}, btnMgProfileView: ${binding.btnMgProfileView.isEnabled}, btnArtifactPassport: ${binding.btnArtifactPassport.isEnabled}")
        }
        
        // Кнопка управления точками скрыта для всех
        
    }

    private fun loadUserAura(userId: String) {
        // Используем GlobalScope для вызова suspend функции
        GlobalScope.launch(Dispatchers.Main) {
            try {
                val response = RetrofitClient.auraApi.getAura(userId)
                if (response.isSuccessful && response.body() != null) {
                    currentAura = response.body()!!
                    LogHelper.d("MainActivity: Аура пользователя загружена, скрыта: ${currentAura!!.auraHidden}")
                    updateAuraButton()
                } else {
                    LogHelper.e("MainActivity: Ошибка загрузки ауры: ${response.code()}")
                }
            } catch (e: Exception) {
                LogHelper.e("MainActivity: Ошибка сети при загрузке ауры: ${e.localizedMessage}")
            }
        }
    }

    private fun updateAuraButton() {
        if (isExtrasensory && currentAura != null) {
            binding.btnToggleAuraHidden.visibility = View.VISIBLE
            if (currentAura!!.auraHidden) {
                binding.btnToggleAuraHidden.text = getString(R.string.show_aura)
            } else {
                binding.btnToggleAuraHidden.text = getString(R.string.hide_aura)
            }
        } else {
            binding.btnToggleAuraHidden.visibility = View.GONE
        }
    }

    private fun toggleAuraHidden() {
        if (currentAura == null) return
        
        val userId = UserPrefsHelper.getUserId(this)
        val newHiddenState = !currentAura!!.auraHidden
        val request = AuraHiddenRequest(if (newHiddenState) 1 else 0)
        
        // Используем GlobalScope для вызова suspend функции
        GlobalScope.launch(Dispatchers.Main) {
            try {
                val response = RetrofitClient.auraApi.updateAuraHidden(userId, request)
                if (response.isSuccessful) {
                    // Обновляем локальное состояние
                    currentAura = currentAura!!.copy(auraHidden = newHiddenState)
                    updateAuraButton()
                    
                    // Показываем сообщение об успехе
                    val message = if (newHiddenState) {
                        getString(R.string.aura_hidden)
                    } else {
                        getString(R.string.aura_shown)
                    }
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                } else {
                    LogHelper.e("MainActivity: Ошибка обновления ауры: ${response.code()}")
                    Toast.makeText(this@MainActivity, "Ошибка обновления ауры: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                LogHelper.e("MainActivity: Ошибка сети при обновлении ауры: ${e.localizedMessage}")
                Toast.makeText(this@MainActivity, "Ошибка сети: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkUserDisciplines() {
        val userId = UserPrefsHelper.getUserId(this)
        RetrofitClient.userProfileApi.getUserProfile(userId)
            .enqueue(object : Callback<User> {
                override fun onResponse(call: Call<User>, response: Response<User>) {
                    if (response.isSuccessful && response.body() != null) {
                        val userServer = response.body()!!
                        val user = userServer
                        
                        // Проверяем модуль "познание артефактов"
                        val hasArtifactKnowledge = user.modules.any { it.name.equals("Познание артефактов", ignoreCase = true) }
                        
                        // Проверяем дисциплину "Экстрасенсорика"
                        val hasExtrasensory = user.disciplines.any { it.name.equals("Экстрасенсорика", ignoreCase = true) }
                        
                        // Проверяем дисциплину "Шумомантия"
                        val hasNoisemancy = user.disciplines.any { it.name.equals("Шумомантия", ignoreCase = true) }
                        
                        LogHelper.d("MainActivity: Проверка дисциплин - Артефакты: $hasArtifactKnowledge, Экстрасенсорика: $hasExtrasensory, Шумомантия: $hasNoisemancy")
                        
                        // Сохраняем актуальные данные пользователя
                        UserPrefsHelper.saveUserData(this@MainActivity, user)
                        
                        // Если пользователь экстрасенс, загружаем его ауру
                        if (hasExtrasensory) {
                            this@MainActivity.isExtrasensory = true
                            loadUserAura(userId)
                        } else {
                            this@MainActivity.isExtrasensory = false
                        }
                        
                        // Показываем контент и обновляем UI после загрузки профиля
                        showContent()
                        updateUI()
                        LogHelper.d("MainActivity: Профиль загружен, UI обновлен")
                    } else {
                        LogHelper.e("MainActivity: Ошибка загрузки профиля: ${response.code()}")
                        
                        // Показываем контент даже при ошибке HTTP, но с ограниченными возможностями
                        showContent()
                        binding.btnScanArtifact.visibility = View.GONE
                        binding.openAuraButton.visibility = View.GONE
                        binding.openTerminalButton.visibility = View.GONE
                        binding.btnFamiliar.visibility = View.GONE
                        
                        // Показываем уведомление об ошибке
                        val errorMessage = when (response.code()) {
                            401 -> "Ошибка авторизации. Попробуйте войти заново."
                            403 -> "Доступ запрещен. Обратитесь к администратору."
                            404 -> "Профиль не найден. Обратитесь к администратору."
                            500 -> "Ошибка сервера. Попробуйте позже."
                            else -> "Ошибка загрузки профиля: ${response.code()}"
                        }
                        Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
                
                override fun onFailure(call: Call<User>, t: Throwable) {
                    LogHelper.e("MainActivity: Ошибка сети при загрузке профиля: ${t.localizedMessage}")
                    
                    // Даже при ошибке показываем контент, но с ограниченными возможностями
                    showContent()
                    binding.btnScanArtifact.visibility = View.GONE
                    binding.openAuraButton.visibility = View.GONE
                    binding.openTerminalButton.visibility = View.GONE
                    binding.btnFamiliar.visibility = View.GONE
                    
                    // Показываем уведомление об ошибке
                    Toast.makeText(this@MainActivity, 
                        "Ошибка загрузки профиля: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            })
    }

    private fun checkIfMgUser() {
        val userName = UserPrefsHelper.getUserId(this)
        isMgUser = userName.startsWith("MG", ignoreCase = true)
        LogHelper.d("MainActivity: checkIfMgUser - userName: $userName, isMgUser: $isMgUser")
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                performLogout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun performLogout() {
        // Очищаем все данные пользователя
        UserPrefsHelper.clearUserData(this)
        
        // Останавливаем сервис локации
        ShiftApplication.instance.stopLocationService()
        
        // Останавливаем сервис обновления профиля
        // ProfileUpdateService.stopService(this) // Удалено
        
        // Сбрасываем состояние игры
        ShiftApplication.instance.setIsInGame(false)
        
        // Возвращаемся на экран авторизации
        val intent = Intent(this, AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun checkPermissionsSequentially() {
        LogHelper.d("MainActivity: Начинаем последовательную проверку разрешений")
        // Сначала проверяем разрешение на уведомления
        if (!hasNotificationPermission()) {
            LogHelper.d("MainActivity: Запрашиваем разрешение на уведомления")
            requestNotificationPermission()
        } else {
            LogHelper.d("MainActivity: Разрешение на уведомления уже есть, проверяем местоположение")
            // Если разрешение на уведомления уже есть, проверяем местоположение
            checkLocationPermission()
        }
    }

    private fun checkNotificationPermission() {
        if (!hasNotificationPermission()) {
            LogHelper.d("MainActivity: Запрашиваем разрешение на уведомления")
            requestNotificationPermission()
        } else {
            LogHelper.d("MainActivity: Разрешение на уведомления уже есть, проверяем местоположение")
            // Если разрешение на уведомления уже есть, проверяем местоположение
            checkLocationPermission()
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotificationPermission() {
        LogHelper.d("MainActivity: Отправляем запрос на разрешение уведомлений")
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATION_PERMISSION
        )
    }

    private fun checkLocationPermission() {
        if (!hasLocationPermission()) {
            LogHelper.d("MainActivity: Запрашиваем разрешение на местоположение")
            requestLocationPermission()
        } else {
            LogHelper.d("MainActivity: Разрешение на местоположение уже есть")
        }
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

    private fun requestLocationPermission() {
        LogHelper.d("MainActivity: Отправляем запрос на разрешение местоположения")
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            REQUEST_LOCATION_PERMISSION
        )
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            LogHelper.d("MainActivity: Получен результат запроса разрешения на уведомления")
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                LogHelper.d("MainActivity: Разрешение на уведомления получено")
                updateUI()
                Toast.makeText(this, "Разрешение на уведомления получено", Toast.LENGTH_SHORT).show()
                // После получения разрешения на уведомления, запрашиваем разрешение на геолокацию с небольшой задержкой
                LogHelper.d("MainActivity: Планируем запрос разрешения на местоположение через 1 секунду")
                binding.root.postDelayed({
                    checkLocationPermission()
                }, 1000) // 1 секунда задержки
            } else {
                LogHelper.w("MainActivity: Пользователь отказал в разрешении на уведомления")
                Toast.makeText(this, "Для полноценной работы приложения требуется разрешение на уведомления", Toast.LENGTH_LONG).show()
                // Даже если пользователь отказал в уведомлениях, все равно запрашиваем местоположение с задержкой
                LogHelper.d("MainActivity: Планируем запрос разрешения на местоположение через 1 секунду (после отказа в уведомлениях)")
                binding.root.postDelayed({
                    checkLocationPermission()
                }, 1000) // 1 секунда задержки
            }
        } else if (requestCode == REQUEST_LOCATION_PERMISSION) {
            LogHelper.d("MainActivity: Получен результат запроса разрешения на местоположение")
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                LogHelper.d("MainActivity: Разрешение на местоположение получено")
                updateUI()
                Toast.makeText(this, "Разрешение на геолокацию получено", Toast.LENGTH_SHORT).show()
                // Проверяем, нужно ли запустить LocationService
                checkAndStartLocationService()
            } else {
                LogHelper.w("MainActivity: Пользователь отказал в разрешении на местоположение")
                Toast.makeText(this, "Для полноценной работы приложения требуется разрешение на геолокацию", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkAndStartLocationService() {
        if (ShiftApplication.instance.isInGame() && !ShiftApplication.instance.isLocationServiceRunning()) {
            // Проверяем разрешения на геолокацию
            if (hasLocationPermission()) {
                // Проверяем, что приложение активно
                if (!isFinishing && !isDestroyed) {
                    ShiftApplication.instance.startLocationService()
                    LogHelper.d("MainActivity: Запуск LocationService")
                    
                    // Запускаем сервис обновления профиля
                    // ProfileUpdateService.startService(this) // Удалено
                    LogHelper.d("MainActivity: ProfileUpdateService запущен")
                } else {
                    LogHelper.w("MainActivity: Activity не активна, LocationService не запускается")
                }
            } else {
                LogHelper.w("MainActivity: Нет разрешений на геолокацию для запуска LocationService")
            }
        }
    }

    private lateinit var updateService: UpdateService
    
    private fun checkForUpdates() {
        LogHelper.d("MainActivity: Начинаем проверку обновлений")
        updateService = UpdateService(this, this)
        updateService.checkForUpdates { updateInfo ->
            LogHelper.i("MainActivity: Получен callback о доступном обновлении")
            updateService.showUpdateDialog(updateInfo)
        }
    }

    companion object {
        const val PREFS_NAME = "game_state"
        const val KEY_IN_GAME = "is_in_game"
        private const val REQUEST_NOTIFICATION_PERMISSION = 100
        private const val REQUEST_LOCATION_PERMISSION = 101
    }
} 