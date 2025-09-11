package bas.app.shift.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AppCompatActivity
import bas.app.shift.api.RetrofitClient
import bas.app.shift.databinding.ActivityArtifactCreatorBinding
import bas.app.shift.helpers.LogHelper
import bas.app.shift.models.ArtifactRequest
import bas.app.shift.models.User
import bas.app.shift.models.UserServer
import bas.app.shift.models.toUser
import com.google.android.material.snackbar.Snackbar
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ArtifactCreatorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityArtifactCreatorBinding
    private var users: List<User> = emptyList()
    private var filteredUsers: List<User> = emptyList() // Добавляем переменную для отфильтрованных пользователей
    private var selectedUser: User? = null
    private var selectedBindingUser: User? = null // Добавляем переменную для выбранного персонажа для привязки

    // Список типов артефактов
    private val artifactTypes = listOf(
        "защита", "атака", "усиление", "ослабление", 
        "изменение", "стабилизация", "познание", "иллюзия", "другой"
    )

    // Список уровней артефактов
    private val artifactLevels = listOf(
        "простой", "сильный", "великий"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArtifactCreatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadUsers()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // Настройка селектора типа артефакта
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, artifactTypes)
        binding.typeAutoComplete.setAdapter(typeAdapter)

        // Настройка селектора уровня артефакта
        val levelAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, artifactLevels)
        binding.levelAutoComplete.setAdapter(levelAdapter)

        // Настройка кнопки сохранения
        binding.saveButton.setOnClickListener {
            createArtifact()
        }
    }

    private fun loadUsers() {
        RetrofitClient.userProfileApi.getAllUserProfiles()
            .enqueue(object : Callback<List<UserServer>> {
                override fun onResponse(call: Call<List<UserServer>>, response: Response<List<UserServer>>) {
                    if (response.isSuccessful && response.body() != null) {
                        val userServers = response.body()!!
                        users = userServers.map { it.toUser() }
                        setupCreatorSpinner()
                        setupBindingSpinner()
                    } else {
                        LogHelper.e("ArtifactCreatorActivity: Ошибка загрузки списка пользователей: ${response.code()}")
                        showError("Ошибка загрузки списка пользователей")
                    }
                }

                override fun onFailure(call: Call<List<UserServer>>, t: Throwable) {
                    LogHelper.e("ArtifactCreatorActivity: Ошибка сети при загрузке пользователей: ${t.localizedMessage}")
                    showError("Ошибка сети при загрузке пользователей")
                }
            })
    }

    private fun setupCreatorSpinner() {
        // Сортируем пользователей по алфавиту и фильтруем MG пользователей
        filteredUsers = users
            .filter { user -> !user.userId.startsWith("MG") } // Исключаем MG пользователей
            .sortedBy { user ->
                val displayName = if (user.playerName.isNullOrEmpty()) {
                    user.characterName ?: ""
                } else if (user.characterName.isNullOrEmpty()) {
                    user.playerName
                } else {
                    "${user.playerName} / ${user.characterName}"
                }
                displayName.lowercase()
            }

        // Создаем список для селектора
        val creatorItems = mutableListOf<String>()
        creatorItems.add("Выберите создателя...") // Заголовок
        
        filteredUsers.forEach { user ->
            val displayName = if (user.playerName.isNullOrEmpty()) {
                user.characterName ?: "Без имени"
            } else if (user.characterName.isNullOrEmpty()) {
                user.playerName
            } else {
                "${user.playerName} / ${user.characterName}"
            }
            creatorItems.add(displayName)
        }

        val creatorAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, creatorItems)
        binding.creatorAutoComplete.setAdapter(creatorAdapter)

        // Обработчик выбора создателя
        binding.creatorAutoComplete.setOnItemClickListener { _, _, position, _ ->
            if (position > 0 && position <= filteredUsers.size) {
                selectedUser = filteredUsers[position - 1]
            } else {
                selectedUser = null
            }
        }
    }

    private fun setupBindingSpinner() {
        // Создаем список для селектора привязки
        val bindingItems = mutableListOf<String>()
        bindingItems.add("Не привязан") // Дефолтный вариант
        
        filteredUsers.forEach { user ->
            val displayName = if (user.playerName.isNullOrEmpty()) {
                user.characterName ?: "Без имени"
            } else if (user.characterName.isNullOrEmpty()) {
                user.playerName
            } else {
                "${user.playerName} / ${user.characterName}"
            }
            bindingItems.add(displayName)
        }

        val bindingAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, bindingItems)
        binding.bindingToSpinner.setAdapter(bindingAdapter)

        // Обработчик выбора персонажа для привязки
        binding.bindingToSpinner.setOnItemClickListener { _, _, position, _ ->
            if (position > 0 && position <= filteredUsers.size) {
                selectedBindingUser = filteredUsers[position - 1]
            } else {
                selectedBindingUser = null
            }
        }
    }

    private fun createArtifact() {
        // Валидация полей
        val name = binding.nameEditText.text.toString().trim()
        val level = binding.levelAutoComplete.text.toString().trim()
        val type = binding.typeAutoComplete.text.toString().trim()
        val material = binding.materialEditText.text.toString().trim()
        val properties = binding.propertiesEditText.text.toString().trim()
        val bindingToName = selectedBindingUser?.characterName

        if (name.isEmpty()) {
            showError("Введите название артефакта")
            binding.nameEditText.requestFocus()
            return
        }

        if (level.isEmpty() || !artifactLevels.contains(level)) {
            showError("Выберите уровень артефакта")
            binding.levelAutoComplete.requestFocus()
            return
        }

        if (type.isEmpty() || !artifactTypes.contains(type)) {
            showError("Выберите тип артефакта")
            binding.typeAutoComplete.requestFocus()
            return
        }

        if (selectedUser == null) {
            showError("Выберите создателя артефакта")
            binding.creatorAutoComplete.requestFocus()
            return
        }

        if (material.isEmpty()) {
            showError("Введите материалы артефакта")
            binding.materialEditText.requestFocus()
            return
        }

        if (properties.isEmpty()) {
            showError("Введите свойства артефакта")
            binding.propertiesEditText.requestFocus()
            return
        }

        // Создание запроса (без creator_name)
        val artifactRequest = ArtifactRequest(
            name = name,
            level = level,
            type = type,
            creatorUserId = selectedUser!!.userId,
            bindingToName = bindingToName,
            material = material,
            properties = properties
        )

        // Отправка запроса
        binding.saveButton.isEnabled = false
        binding.saveButton.text = "Создание..."

        RetrofitClient.artifactApi.createArtifact(artifactRequest)
            .enqueue(object : Callback<bas.app.shift.models.Artifact> {
                override fun onResponse(
                    call: Call<bas.app.shift.models.Artifact>,
                    response: Response<bas.app.shift.models.Artifact>
                ) {
                    binding.saveButton.isEnabled = true
                    binding.saveButton.text = "Создать артефакт"

                    if (response.isSuccessful && response.body() != null) {
                        showSuccess("Артефакт успешно создан!")
                        clearForm()
                    } else {
                        showError("Ошибка создания артефакта: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<bas.app.shift.models.Artifact>, t: Throwable) {
                    binding.saveButton.isEnabled = true
                    binding.saveButton.text = "Создать артефакт"
                    
                    LogHelper.e("ArtifactCreatorActivity: Ошибка сети при создании артефакта: ${t.localizedMessage}")
                    showError("Ошибка сети при создании артефакта")
                }
            })
    }

    private fun clearForm() {
        binding.nameEditText.text?.clear()
        binding.levelAutoComplete.text?.clear()
        binding.typeAutoComplete.text?.clear()
        binding.creatorAutoComplete.text?.clear()
        binding.bindingToSpinner.text?.clear()
        binding.materialEditText.text?.clear()
        binding.propertiesEditText.text?.clear()
        selectedUser = null
        selectedBindingUser = null
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}
