package bas.app.shift.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import bas.app.shift.databinding.ActivityPointManagementBinding
import bas.app.shift.models.Point
import bas.app.shift.models.PointRequest
import bas.app.shift.models.PointType
import bas.app.shift.services.ServerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PointManagementActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPointManagementBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPointManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Управление точками"
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        setupTypeDropdown()
        setupButtons()
    }

    private fun setupTypeDropdown() {
        val types = PointType.values().map { it.serverValue }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, types)
        binding.typeInput.setAdapter(adapter)
    }

    private fun setupButtons() {
        binding.deletePointButton.setOnClickListener {
            val pointId = binding.pointIdInput.text.toString()
            if (pointId.isNotEmpty()) {
                deletePoint(pointId)
            } else {
                Toast.makeText(this, "Введите ID точки", Toast.LENGTH_SHORT).show()
            }
        }

        binding.createPointButton.setOnClickListener {
            if (validateInputs()) {
                createPoint()
            }
        }
    }

    private fun validateInputs(): Boolean {
        val id = binding.createPointIdInput.text.toString()
        val lat = binding.latInput.text.toString()
        val lng = binding.lngInput.text.toString()
        val radius = binding.radiusInput.text.toString()
        val type = binding.typeInput.text.toString()
        val description = binding.pointDescription.text.toString()

        if (id.isEmpty() || lat.isEmpty() || lng.isEmpty() ||
            description.isEmpty() || radius.isEmpty() || type.isEmpty()) {
            Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun deletePoint(pointId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ServerService.deletePoint(pointId)
                if (response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@PointManagementActivity, "Точка удалена", Toast.LENGTH_SHORT).show()
                        binding.pointIdInput.text?.clear()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@PointManagementActivity, "Ошибка при удалении точки", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@PointManagementActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun createPoint() {
        val pointRequest = PointRequest(
            pointId = binding.createPointIdInput.text.toString(),
            lat = binding.latInput.text.toString().toDouble(),
            lng = binding.lngInput.text.toString().toDouble(),
            type = binding.typeInput.text.toString(),
            radius = binding.radiusInput.text.toString().toDouble(),
            description = binding.pointDescription.text.toString()
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ServerService.createPoint(pointRequest)
                if (response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@PointManagementActivity, "Точка создана", Toast.LENGTH_SHORT).show()
                        clearInputs()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@PointManagementActivity, "Ошибка при создании точки", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@PointManagementActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun clearInputs() {
        binding.createPointIdInput.text?.clear()
        binding.latInput.text?.clear()
        binding.lngInput.text?.clear()
        binding.pointDescription.text?.clear()
        binding.radiusInput.text?.clear()
        binding.typeInput.text?.clear()
    }
} 