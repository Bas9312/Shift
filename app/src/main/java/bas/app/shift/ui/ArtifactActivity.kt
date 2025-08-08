package bas.app.shift.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import bas.app.shift.api.RetrofitClient
import bas.app.shift.databinding.ActivityArtifactBinding
import bas.app.shift.helpers.LogHelper
import bas.app.shift.models.Artifact
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ArtifactActivity : AppCompatActivity() {
    private lateinit var binding: ActivityArtifactBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArtifactBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Настройка тулбара
        binding.toolbar.title = "Артефакт"
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // Получаем ID артефакта из intent
        val artifactId = intent.getIntExtra("artifact_id", -1)
        if (artifactId == -1) {
            showError("Не указан ID артефакта")
            finish()
            return
        }

        fetchArtifact(artifactId)
    }

    private fun fetchArtifact(artifactId: Int) {
        RetrofitClient.artifactApi.getArtifact(artifactId)
            .enqueue(object : Callback<Artifact> {
                override fun onResponse(call: Call<Artifact>, response: Response<Artifact>) {
                    if (response.isSuccessful && response.body() != null) {
                        showArtifact(response.body()!!)
                    } else {
                        showError("Ошибка загрузки артефакта: ${response.code()}")
                    }
                }
                override fun onFailure(call: Call<Artifact>, t: Throwable) {
                    showError("Ошибка сети: ${t.localizedMessage}")
                }
            })
    }

    private fun showArtifact(artifact: Artifact) {
        // Название
        binding.artifactName.text = artifact.name
        // Уровень
        binding.artifactLevel.text = artifact.level
        // Тип
        binding.artifactType.text = artifact.type
        // Создатель
        binding.artifactCreatorName.text = artifact.creatorName
        // Материал
        binding.artifactMaterial.text = artifact.material
        // Свойства
        binding.artifactProperties.text = artifact.properties
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        LogHelper.e("ArtifactActivity $msg")
    }
} 