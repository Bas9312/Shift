package bas.app.shift.ui

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import bas.app.shift.R
import bas.app.shift.api.RetrofitClient
import bas.app.shift.databinding.ActivityArtifactPassportBinding
import bas.app.shift.helpers.LogHelper
import bas.app.shift.models.Artifact
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ArtifactPassportActivity : AppCompatActivity() {
    private lateinit var binding: ActivityArtifactPassportBinding
    private var artifacts: List<Artifact> = emptyList()
    private var selectedArtifact: Artifact? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArtifactPassportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Настройка тулбара
        binding.toolbar.title = getString(R.string.artifact_passport_title)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        setupSpinner()
        fetchAllArtifacts()
    }

    private fun setupSpinner() {
        binding.artifactSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0 && artifacts.isNotEmpty()) {
                    selectedArtifact = artifacts[position - 1]
                    showArtifactDetails(selectedArtifact!!)
                } else {
                    hideArtifactDetails()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                hideArtifactDetails()
            }
        }
    }

    private fun fetchAllArtifacts() {
        binding.progressBar.visibility = View.VISIBLE
        binding.artifactSpinner.visibility = View.GONE

        RetrofitClient.artifactApi.getAllArtifacts()
            .enqueue(object : Callback<List<Artifact>> {
                override fun onResponse(call: Call<List<Artifact>>, response: Response<List<Artifact>>) {
                    binding.progressBar.visibility = View.GONE
                    binding.artifactSpinner.visibility = View.VISIBLE

                    if (response.isSuccessful && response.body() != null) {
                        artifacts = response.body()!!
                        setupArtifactSpinner()
                    } else {
                        showError("Ошибка загрузки артефактов: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<List<Artifact>>, t: Throwable) {
                    binding.progressBar.visibility = View.GONE
                    binding.artifactSpinner.visibility = View.VISIBLE
                    showError("Ошибка сети: ${t.localizedMessage}")
                }
            })
    }

    private fun setupArtifactSpinner() {
        val spinnerItems = mutableListOf(getString(R.string.select_artifact_hint))
        artifacts.forEach { artifact ->
            spinnerItems.add("${artifact.name} / ${artifact.creatorName}")
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, spinnerItems)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.artifactSpinner.adapter = adapter
    }

    private fun showArtifactDetails(artifact: Artifact) {
        // Создаем фрагмент с деталями артефакта
        val fragment = ArtifactDetailsFragment.newInstance(artifact.id)
        
        supportFragmentManager.beginTransaction()
            .replace(R.id.artifact_details_container, fragment)
            .commit()
    }

    private fun hideArtifactDetails() {
        // Убираем фрагмент с деталями
        val fragment = supportFragmentManager.findFragmentById(R.id.artifact_details_container)
        if (fragment != null) {
            supportFragmentManager.beginTransaction()
                .remove(fragment)
                .commit()
        }
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        LogHelper.e("ArtifactPassportActivity: $msg")
    }
}
