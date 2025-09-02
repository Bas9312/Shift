package bas.app.shift.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import bas.app.shift.R
import bas.app.shift.api.AuraApi
import bas.app.shift.api.RetrofitClient
import bas.app.shift.models.AuraMark
import bas.app.shift.models.AuraProblem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuraActivity : AppCompatActivity(), AuraMarkCallback {
    private lateinit var auraApi: AuraApi
    private lateinit var auraCanvas: AuraCanvasView
    private var entityId: String = "user-123" // заменить на реальный id

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aura)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = "Аура"
        toolbar.setNavigationOnClickListener {
            finish()
        }
        auraApi = RetrofitClient.auraApi
        auraCanvas = findViewById(R.id.auraCanvas)
        
        // Устанавливаем callback для обработки long tap
        auraCanvas.markCallback = this
        
        // Получаем ID ауры из intent
        val auraId = intent.getStringExtra("aura_id")
        if (auraId != null) {
            loadAura(auraId)
        } else {
            Toast.makeText(this, "Не указан ID ауры", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadAura(auraId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val response = auraApi.getAura(auraId)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    val aura = response.body()
                    if (aura != null) {
                        auraCanvas.setAura(aura)
                    } else {
                        Toast.makeText(this@AuraActivity, "Ошибка: пустая аура", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@AuraActivity, "Ошибка загрузки ауры", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onMarkLongTap(mark: AuraMark) {
        Toast.makeText(this, "Метка: ${mark.name}", Toast.LENGTH_SHORT).show()
    }
    
    override fun onProblemLongTap(slot: Int, problem: AuraProblem?) {
        val message = if (problem != null) {
            "Проблема: ${problem.name}"
        } else {
            "Пустой слот $slot"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
} 