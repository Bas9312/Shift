package bas.app.shift.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import bas.app.shift.api.RetrofitClient
import bas.app.shift.databinding.FragmentArtifactDetailsBinding
import bas.app.shift.helpers.LogHelper
import bas.app.shift.models.Artifact
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ArtifactDetailsFragment : Fragment() {
    private var _binding: FragmentArtifactDetailsBinding? = null
    private val binding get() = _binding!!
    private var artifactId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            artifactId = it.getInt(ARG_ARTIFACT_ID, -1)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArtifactDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (artifactId != -1) {
            fetchArtifact(artifactId)
        }
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
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        LogHelper.e("ArtifactDetailsFragment: $msg")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ARTIFACT_ID = "artifact_id"

        fun newInstance(artifactId: Int): ArtifactDetailsFragment {
            return ArtifactDetailsFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_ARTIFACT_ID, artifactId)
                }
            }
        }
    }
}
