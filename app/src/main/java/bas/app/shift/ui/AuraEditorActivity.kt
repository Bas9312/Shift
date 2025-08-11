package bas.app.shift.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AppCompatActivity
import bas.app.shift.api.RetrofitClient
import bas.app.shift.databinding.ActivityAuraEditorBinding
import bas.app.shift.helpers.LogHelper
import bas.app.shift.models.User
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AuraEditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAuraEditorBinding
    private var users: List<User> = emptyList()
    private var filteredUsers: List<User> = emptyList() // Добавляем переменную для отфильтрованных пользователей
    private var selectedUser: User? = null
    private lateinit var auraFragment: AuraFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuraEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadUsers()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // Создаем фрагмент ауры
        auraFragment = AuraFragment()
        supportFragmentManager.beginTransaction()
            .replace(binding.auraContainer.id, auraFragment)
            .commit()
    }

    private fun loadUsers() {
        RetrofitClient.userProfileApi.getAllUserProfiles()
            .enqueue(object : Callback<List<User>> {
                override fun onResponse(call: Call<List<User>>, response: Response<List<User>>) {
                    if (response.isSuccessful && response.body() != null) {
                        users = response.body()!!
                        setupUserSpinner()
                    } else {
                        LogHelper.e("AuraEditorActivity: Ошибка загрузки списка пользователей: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<List<User>>, t: Throwable) {
                    LogHelper.e("AuraEditorActivity: Ошибка сети при загрузке пользователей: ${t.localizedMessage}")
                }
            })
    }

    private fun setupUserSpinner() {
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
        val userItems = mutableListOf<String>()
        userItems.add("Выберите пользователя...") // Заголовок
        
        filteredUsers.forEach { user ->
            val displayName = if (user.playerName.isNullOrEmpty()) {
                user.characterName ?: "Без имени"
            } else if (user.characterName.isNullOrEmpty()) {
                user.playerName
            } else {
                "${user.playerName} / ${user.characterName}"
            }
            userItems.add(displayName)
        }

        val userAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, userItems)
        binding.userAutoComplete.setAdapter(userAdapter)

        // Обработчик выбора пользователя
        binding.userAutoComplete.setOnItemClickListener { _, _, position, _ ->
            if (position > 0 && position <= filteredUsers.size) {
                selectedUser = filteredUsers[position - 1]
                loadUserAura(selectedUser!!.userId)
            } else {
                selectedUser = null
                clearAura()
            }
        }
    }

    private fun loadUserAura(userId: String) {
        // Загружаем ауру пользователя через фрагмент
        auraFragment.loadUserAura(userId)
    }

    private fun clearAura() {
        // Очищаем ауру
        auraFragment.clearAura()
    }
}
