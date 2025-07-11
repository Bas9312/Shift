package bas.app.shift.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import bas.app.shift.R
import bas.app.shift.api.AuraApi
import bas.app.shift.api.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuraActivity : AppCompatActivity() {
    private lateinit var auraApi: AuraApi
    private lateinit var auraCanvas: AuraCanvasView
    private var entityId: String = "user-123" // заменить на реальный id

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aura)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Аура"
        findViewById<Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        auraApi = RetrofitClient.auraApi
        auraCanvas = findViewById(R.id.auraCanvas)
        loadAura()
    }

    private fun loadAura() {
        CoroutineScope(Dispatchers.IO).launch {
            val response = auraApi.getAura(entityId)
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
} 