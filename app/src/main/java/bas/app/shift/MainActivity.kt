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
import bas.app.shift.ui.terminal.TerminalActivity
import bas.app.shift.ui.PointManagementActivity
import bas.app.shift.ui.AuraEditorActivity
import bas.app.shift.ui.ArtifactCreatorActivity
import bas.app.shift.ui.MgProfileViewActivity
import bas.app.shift.ui.ArtifactPassportActivity
import bas.app.shift.ui.AuthActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var isMgUser = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupButtons()
        checkUserDisciplines()
        checkIfMgUser()
        updateUI()
        checkNotificationPermission()
        checkLocationPermission()
        
        // Проверяем состояние игры и запускаем сервис если нужно
        checkAndStartLocationService()
    }

    override fun onStart() {
        super.onStart()
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
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = "Shift"
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

        binding.openPointManagementButton.setOnClickListener {
            startActivity(Intent(this, PointManagementActivity::class.java))
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
        } else {
            ShiftApplication.instance.setIsInGame(false)
            updateUI()
            ShiftApplication.instance.stopLocationService()
            LogHelper.d("MainActivity: LocationService остановлен через переключатель")
        }
    }

    private fun updateUI() {
        binding.btnOpenMap.isEnabled = ShiftApplication.instance.isInGame() && hasNotificationPermission()
        
        // Для МГ пользователей скрываем терминал и профиль
        if (isMgUser) {
            binding.openTerminalButton.visibility = View.GONE
            binding.btnOpenProfile.visibility = View.GONE
            binding.openAuraButton.visibility = View.GONE
            binding.btnScanArtifact.visibility = View.GONE
            
            // Показываем кнопки для МГ
            binding.btnAuraEditor.visibility = View.VISIBLE
            binding.btnCreateArtifact.visibility = View.VISIBLE
            binding.btnMgProfileView.visibility = View.VISIBLE
            binding.btnArtifactPassport.visibility = View.VISIBLE
        } else {
            // Для обычных пользователей показываем стандартные кнопки в зависимости от дисциплин
            val user = UserPrefsHelper.getUserData(this)
            if (user != null) {
                // Проверяем модуль "познание артефактов"
                val hasArtifactKnowledge = user.modules.any { 
                    it.name.equals("познание артефактов", ignoreCase = true) 
                }
                binding.btnScanArtifact.visibility = if (hasArtifactKnowledge) View.VISIBLE else View.GONE
                
                // Проверяем дисциплину "Экстрасенсорика"
                val hasExtrasensory = user.disciplines.any { 
                    it.name.equals("Экстрасенсорика", ignoreCase = true) 
                }
                binding.openAuraButton.visibility = if (hasExtrasensory) View.VISIBLE else View.GONE
                
                // Проверяем дисциплину "Шумомантия"
                val hasNoisemancy = user.disciplines.any {
                    it.id == 9
                }
                binding.openTerminalButton.visibility = if (hasNoisemancy) View.VISIBLE else View.GONE
            } else {
                // Если профиль не загружен, скрываем все кнопки
                binding.openTerminalButton.visibility = View.GONE
                binding.openAuraButton.visibility = View.GONE
                binding.btnScanArtifact.visibility = View.GONE
            }
            
            binding.btnOpenProfile.visibility = View.VISIBLE
            
            // Скрываем кнопки для МГ
            binding.btnAuraEditor.visibility = View.GONE
            binding.btnCreateArtifact.visibility = View.GONE
            binding.btnMgProfileView.visibility = View.GONE
            binding.btnArtifactPassport.visibility = View.GONE
            
            // Включаем/выключаем кнопки в зависимости от состояния игры
            binding.openTerminalButton.isEnabled = ShiftApplication.instance.isInGame()
            binding.openAuraButton.isEnabled = ShiftApplication.instance.isInGame()
            binding.btnOpenProfile.isEnabled = ShiftApplication.instance.isInGame()
            binding.btnScanArtifact.isEnabled = ShiftApplication.instance.isInGame()
        }
        
        // Кнопка управления точками скрыта для всех
        binding.openPointManagementButton.visibility = View.GONE
    }

    private fun checkUserDisciplines() {
        val userId = UserPrefsHelper.getUserId(this)
        RetrofitClient.userProfileApi.getUserProfile(userId)
            .enqueue(object : Callback<User> {
                override fun onResponse(call: Call<User>, response: Response<User>) {
                    if (response.isSuccessful && response.body() != null) {
                        val user = response.body()!!
                        
                        // Проверяем модуль "познание артефактов"
                        val hasArtifactKnowledge = user.modules.any { 
                            it.name.equals("познание артефактов", ignoreCase = true) 
                        }
                        
                        // Проверяем дисциплину "Экстрасенсорика"
                        val hasExtrasensory = user.disciplines.any { 
                            it.name.equals("Экстрасенсорика", ignoreCase = true) 
                        }
                        
                        // Проверяем дисциплину "Шумомантия"
                        val hasNoisemancy = user.disciplines.any {
                            it.id == 9
                        }
                        
                        LogHelper.d("MainActivity: Проверка дисциплин - Артефакты: $hasArtifactKnowledge, Экстрасенсорика: $hasExtrasensory, Шумомантия: $hasNoisemancy")
                        
                        // Сохраняем актуальные данные пользователя
                        UserPrefsHelper.saveUserData(this@MainActivity, user)
                        
                        // Обновляем UI после загрузки профиля
                        updateUI()
                    } else {
                        LogHelper.e("MainActivity: Ошибка загрузки профиля: ${response.code()}")
                    }
                }
                
                override fun onFailure(call: Call<User>, t: Throwable) {
                    binding.btnScanArtifact.visibility = View.GONE
                    binding.openAuraButton.visibility = View.GONE
                    binding.openTerminalButton.visibility = View.GONE
                    LogHelper.e("MainActivity: Ошибка сети при загрузке профиля: ${t.localizedMessage}")
                }
            })
    }

    private fun checkIfMgUser() {
        val userName = UserPrefsHelper.getUserId(this)
        isMgUser = userName.startsWith("MG", ignoreCase = true)
        LogHelper.d("MainActivity: Пользователь ${if (isMgUser) "является" else "не является"} МГ")
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
        
        // Сбрасываем состояние игры
        ShiftApplication.instance.setIsInGame(false)
        
        // Возвращаемся на экран авторизации
        val intent = Intent(this, AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun checkNotificationPermission() {
        if (!hasNotificationPermission()) {
            requestNotificationPermission()
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotificationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATION_PERMISSION
        )
    }

    private fun checkLocationPermission() {
        if (!hasLocationPermission()) {
            requestLocationPermission()
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
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateUI()
                Toast.makeText(this, "Разрешение на уведомления получено", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Для полноценной работы приложения требуется разрешение на уведомления", Toast.LENGTH_LONG).show()
            }
        } else if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateUI()
                Toast.makeText(this, "Разрешение на геолокацию получено", Toast.LENGTH_SHORT).show()
                // Проверяем, нужно ли запустить LocationService
                checkAndStartLocationService()
            } else {
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
                } else {
                    LogHelper.w("MainActivity: Activity не активна, LocationService не запускается")
                }
            } else {
                LogHelper.w("MainActivity: Нет разрешений на геолокацию для запуска LocationService")
            }
        }
    }

    companion object {
        const val PREFS_NAME = "game_state"
        const val KEY_IN_GAME = "is_in_game"
        private const val REQUEST_NOTIFICATION_PERMISSION = 100
        private const val REQUEST_LOCATION_PERMISSION = 101
    }
} 