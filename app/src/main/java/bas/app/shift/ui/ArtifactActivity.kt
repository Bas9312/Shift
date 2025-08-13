package bas.app.shift.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import bas.app.shift.R
import bas.app.shift.helpers.LogHelper

class ArtifactActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_artifact_simple)

        // Настройка тулбара
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.artifact_title)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // Получаем ID артефакта из intent
        val artifactId = intent.getIntExtra("artifact_id", -1)
        if (artifactId == -1) {
            showError("Не указан ID артефакта")
            finish()
            return
        }

        // Создаем и добавляем фрагмент с деталями артефакта
        val fragment = ArtifactDetailsFragment.newInstance(artifactId)
        supportFragmentManager.beginTransaction()
            .replace(R.id.artifact_fragment_container, fragment)
            .commit()
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        LogHelper.e("ArtifactActivity $msg")
    }
} 