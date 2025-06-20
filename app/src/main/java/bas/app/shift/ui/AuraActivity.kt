package bas.app.shift.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import bas.app.shift.R
import bas.app.shift.api.AuraApi
import bas.app.shift.models.*
import com.example.shift.data.api.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuraActivity : AppCompatActivity() {
    private lateinit var auraApi: AuraApi
    private lateinit var auraCanvas: AuraCanvasView
    private var entityId: String = "test_entity" // заменить на реальный id

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aura)
        auraApi = RetrofitClient.auraApi
        auraCanvas = findViewById(R.id.auraCanvas)
        loadAura()
    }

    private fun loadAura() {
        CoroutineScope(Dispatchers.IO).launch {
            val response = auraApi.getAura(entityId)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    auraCanvas.setAura(response.body())
                } else {
                    Toast.makeText(this@AuraActivity, "Ошибка загрузки ауры", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
} 