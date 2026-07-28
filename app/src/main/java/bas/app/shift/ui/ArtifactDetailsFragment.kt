package bas.app.shift.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import bas.app.shift.R
import bas.app.shift.api.RetrofitClient
import bas.app.shift.databinding.FragmentArtifactDetailsBinding
import bas.app.shift.helpers.DisplayNames
import bas.app.shift.helpers.LogHelper
import bas.app.shift.helpers.NetworkErrors
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.models.Artifact
import bas.app.shift.models.ArtifactUpdateRequest
import bas.app.shift.models.ShortUser
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ArtifactDetailsFragment : Fragment() {
    private var _binding: FragmentArtifactDetailsBinding? = null
    private val binding get() = _binding!!
    private var artifactId: Int = -1
    private var currentArtifact: Artifact? = null
    private var isMgUser: Boolean = false
    private var users: List<ShortUser> = emptyList()
    private var selectedBindingUser: ShortUser? = null

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
        
        // Проверяем, является ли пользователь MG
        val userId = UserPrefsHelper.getUserId(requireContext())
        isMgUser = userId.startsWith("MG", ignoreCase = true)
        
        // Настраиваем кнопку редактирования
        binding.editBindingButton.setOnClickListener {
            showBindingEditDialog()
        }
        
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
                        showError(NetworkErrors.http(response.code()))
                    }
                }
                override fun onFailure(call: Call<Artifact>, t: Throwable) {
                    showError(NetworkErrors.network(t))
                }
            })
    }

    private fun showArtifact(artifact: Artifact) {
        currentArtifact = artifact
        
        // Проверяем, что binding не null и view еще не уничтожена
        if (_binding == null || !isAdded) return
        
        // Название
        binding.artifactName.text = artifact.name
        // Уровень
        binding.artifactLevel.text = artifact.level
        // Тип
        binding.artifactType.text = artifact.type
        // Создатель
        binding.artifactCreatorName.text = artifact.creatorName
        // Привязка
        binding.artifactBindingToName.text = if (artifact.bindingToName.isNullOrBlank()) "не привязан" else artifact.bindingToName
        // Материал
        binding.artifactMaterial.text = artifact.material
        // Свойства
        binding.artifactProperties.text = artifact.properties
        
        // Показываем кнопку редактирования только для MG пользователей
        binding.editBindingButton.visibility = if (isMgUser) View.VISIBLE else View.GONE
    }

    private fun showError(msg: String) {
        LogHelper.e("ArtifactDetailsFragment: $msg")
        val ctx = context ?: return
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    }

    private fun showBindingEditDialog() {
        if (users.isEmpty()) {
            loadUsersForDialog()
            return
        }
        showBindingDialog()
    }

    private fun loadUsersForDialog() {
        RetrofitClient.userProfileApi.getAllUserShortProfiles()
            .enqueue(object : Callback<List<ShortUser>> {
                override fun onResponse(call: Call<List<ShortUser>>, response: Response<List<ShortUser>>) {
                    if (!isAdded) return
                    if (response.isSuccessful && response.body() != null) {
                        val userServers = response.body()!!
                        users = userServers
                        showBindingDialog()
                    } else {
                        showError(NetworkErrors.http(response.code()))
                    }
                }

                override fun onFailure(call: Call<List<ShortUser>>, t: Throwable) {
                    showError(NetworkErrors.network(t))
                }
            })
    }

    private fun showBindingDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_artifact_binding, null)
        val bindingSpinner = dialogView.findViewById<AutoCompleteTextView>(R.id.binding_spinner)
        
        // Создаем список для селектора привязки
        val bindingItems = mutableListOf<String>()
        bindingItems.add("Не привязан") // Дефолтный вариант
        
        val filteredUsers = users.filter { user -> !user.userId.startsWith("MG") }
        filteredUsers.forEach { user ->
            val displayName = DisplayNames.combinePlayerFirst(user.playerName, user.characterName, "Без имени")
            bindingItems.add(displayName)
        }

        val bindingAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, bindingItems)
        bindingSpinner.setAdapter(bindingAdapter)

        // Устанавливаем текущее значение
        val currentBinding = currentArtifact?.bindingToName
        if (!currentBinding.isNullOrBlank()) {
            val currentIndex = filteredUsers.indexOfFirst { it.characterName == currentBinding }
            if (currentIndex >= 0) {
                bindingSpinner.setText(bindingItems[currentIndex + 1], false)
                selectedBindingUser = filteredUsers[currentIndex]
            }
        } else {
            bindingSpinner.setText(bindingItems[0], false)
            selectedBindingUser = null
        }

        // Обработчик выбора персонажа для привязки
        bindingSpinner.setOnItemClickListener { _, _, position, _ ->
            if (position > 0 && position <= filteredUsers.size) {
                selectedBindingUser = filteredUsers[position - 1]
            } else {
                selectedBindingUser = null
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Изменить привязку артефакта")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                updateArtifactBinding()
            }
            .setNegativeButton("Отмена", null)
            .create()

        dialog.show()
    }

    private fun updateArtifactBinding() {
        val bindingToName = selectedBindingUser?.characterName ?: ""
        
        val updateRequest = ArtifactUpdateRequest(bindingToName)
        
        RetrofitClient.artifactApi.updateArtifact(artifactId, updateRequest)
            .enqueue(object : Callback<Artifact> {
                override fun onResponse(call: Call<Artifact>, response: Response<Artifact>) {
                    if (response.isSuccessful && response.body() != null) {
                        val updatedArtifact = response.body()!!
                        currentArtifact = updatedArtifact
                        if (_binding != null && isAdded) {
                            binding.artifactBindingToName.text = if (updatedArtifact.bindingToName.isNullOrBlank()) "не привязан" else updatedArtifact.bindingToName
                        }
                        context?.let { Toast.makeText(it, "Привязка обновлена", Toast.LENGTH_SHORT).show() }
                    } else {
                        showError(NetworkErrors.http(response.code()))
                    }
                }

                override fun onFailure(call: Call<Artifact>, t: Throwable) {
                    showError(NetworkErrors.network(t))
                }
            })
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
