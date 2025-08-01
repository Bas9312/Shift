package bas.app.shift

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import bas.app.shift.api.RetrofitClient
import bas.app.shift.databinding.ActivityMainBinding
import bas.app.shift.helpers.LogHelper
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.models.User
import bas.app.shift.ui.terminal.TerminalActivity
import bas.app.shift.ui.PointManagementActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
        checkArtifactKnowledgeModule()
        updateUI()
        checkNotificationPermission()
    }

    private fun setupButtons() {
        binding.inGameSelector.addOnButtonCheckedListener{ _, checkedId, isChecked -> if (isChecked) onCheckChanged(checkedId) }
        binding.inGameSelector.check( if (ShiftApplication.instance.isInGame()) R.id.inGame else R.id.outGame)

        binding.btnOpenMap.setOnClickListener {
            if (ShiftApplication.instance.isInGame() && hasNotificationPermission()) {
                startActivity(Intent(this, EkatMaps::class.java))
            }
        }

        binding.btnNeoHacking.setOnClickListener{
            startActivity(Intent(this, TerminalActivity::class.java))
        }

        binding.openTerminalButton.setOnClickListener {
            startActivity(Intent(this, TerminalActivity::class.java))
        }

        binding.openPointManagementButton.setOnClickListener {
            startActivity(Intent(this, PointManagementActivity::class.java))
        }

        binding.openAuraButton.setOnClickListener {
            startActivity(Intent(this, bas.app.shift.ui.AuraActivity::class.java))
        }

        binding.btnOpenProfile.setOnClickListener {
            startActivity(Intent(this, bas.app.shift.ui.ProfileActivity::class.java))
        }

        binding.btnScanArtifact.setOnClickListener {
            startActivity(Intent(this, bas.app.shift.ui.ArtifactScannerActivity::class.java))
        }
    }

    private fun onCheckChanged(checkedId: Int) {
        if (checkedId == R.id.inGame) {
            ShiftApplication.instance.setIsInGame(true)
            updateUI()
            ShiftApplication.instance.startLocationService()
        } else {

            ShiftApplication.instance.setIsInGame(false)
            updateUI()
            ShiftApplication.instance.stopLocationService()
        }
    }

    private fun updateUI() {
        binding.btnOpenMap.isEnabled = ShiftApplication.instance.isInGame() && hasNotificationPermission()
        binding.btnNeoHacking.isEnabled = ShiftApplication.instance.isInGame()
        binding.openTerminalButton.isEnabled = ShiftApplication.instance.isInGame()
        binding.openPointManagementButton.isEnabled = ShiftApplication.instance.isInGame()
        binding.openAuraButton.isEnabled = ShiftApplication.instance.isInGame()
        binding.btnOpenProfile.isEnabled = ShiftApplication.instance.isInGame()
        binding.btnScanArtifact.isEnabled = ShiftApplication.instance.isInGame()
    }

    private fun checkArtifactKnowledgeModule() {
        val userId = UserPrefsHelper.getUserId(this)
        RetrofitClient.userProfileApi.getUserProfile(userId)
            .enqueue(object : Callback<User> {
                override fun onResponse(call: Call<User>, response: Response<User>) {
                    if (response.isSuccessful && response.body() != null) {
                        val user = response.body()!!
                        val hasArtifactKnowledge = user.modules.any { 
                            it.name.equals("познание артефактов", ignoreCase = true) 
                        }
                        
                        binding.btnScanArtifact.visibility = if (hasArtifactKnowledge) View.VISIBLE else View.GONE
                        LogHelper.d("MainActivity: Модуль 'познание артефактов' ${if (hasArtifactKnowledge) "найден" else "не найден"}")
                        
                        // Сохраняем актуальные данные пользователя
                        UserPrefsHelper.saveUserData(this@MainActivity, user)
                    } else {
                        binding.btnScanArtifact.visibility = View.GONE
                        LogHelper.e("MainActivity: Ошибка загрузки профиля: ${response.code()}")
                    }
                }
                
                override fun onFailure(call: Call<User>, t: Throwable) {
                    binding.btnScanArtifact.visibility = View.GONE
                    LogHelper.e("MainActivity: Ошибка сети при загрузке профиля: ${t.localizedMessage}")
                }
            })
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
        }
    }

    companion object {
        const val PREFS_NAME = "game_state"
        const val KEY_IN_GAME = "is_in_game"
        private const val REQUEST_NOTIFICATION_PERMISSION = 100
    }
} 