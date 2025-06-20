package bas.app.shift

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import bas.app.shift.databinding.ActivityMainBinding
import bas.app.shift.ui.terminal.TerminalActivity
import bas.app.shift.ui.PointManagementActivity


class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
        updateUI()
        checkNotificationPermission()
    }

    private fun setupButtons() {
        binding.btnInGame.setOnClickListener {
            ShiftApplication.instance.setIsInGame(true)
            updateUI()
            ShiftApplication.instance.startLocationService()
        }

        binding.btnOutOfGame.setOnClickListener {
            ShiftApplication.instance.setIsInGame(false)
            updateUI()
            ShiftApplication.instance.stopLocationService()
        }

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
    }

    private fun updateUI() {
        binding.btnInGame.isSelected = ShiftApplication.instance.isInGame()
        binding.btnOutOfGame.isSelected = !ShiftApplication.instance.isInGame()
        binding.btnOpenMap.isEnabled = ShiftApplication.instance.isInGame() && hasNotificationPermission()
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