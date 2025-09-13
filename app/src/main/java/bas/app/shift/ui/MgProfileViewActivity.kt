package bas.app.shift.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import bas.app.shift.api.RetrofitClient
import bas.app.shift.databinding.ActivityMgProfileViewBinding
import bas.app.shift.helpers.LogHelper
import bas.app.shift.models.ShortUser
import bas.app.shift.models.User
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MgProfileViewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMgProfileViewBinding
    private var users: List<ShortUser> = emptyList()
    private var filteredUsers: List<ShortUser> = emptyList() // Добавляем переменную для отфильтрованных пользователей
    private lateinit var profileFragment: ProfileFragment
    private var selectedUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMgProfileViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    override fun onResume() {
        super.onResume()
        // Загружаем пользователей только если список пустой
        if (users.isEmpty()) {
            loadUsers()
        } else {
            // Если пользователи уже загружены, просто обновляем спиннер
            setupUserSpinner()
        }
        selectedUserId?.let {
            loadUserProfile(it)
        }
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // Настройка спиннера
        binding.userSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0 && position <= filteredUsers.size) { // Используем отфильтрованный список
                    val selectedUser = filteredUsers[position - 1]
                    selectedUserId = selectedUser.userId
                    loadUserProfile(selectedUser.userId)
                } else {
                    selectedUserId = null
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedUserId = null
            }
        }

        // Настройка кнопки редактирования
        binding.btnEditProfile.setOnClickListener {
            if (selectedUserId != null) {
                val intent = Intent(this, ProfileEditActivity::class.java)
                intent.putExtra("user_id", selectedUserId)
                startActivity(intent)
            }
        }

        // Создаем фрагмент профиля
        profileFragment = ProfileFragment()
        supportFragmentManager.beginTransaction()
            .replace(binding.profileContainer.id, profileFragment)
            .commit()
    }

    private fun loadUsers() {
        RetrofitClient.userProfileApi.getAllUserShortProfiles()
            .enqueue(object : Callback<List<ShortUser>> {
                override fun onResponse(call: Call<List<ShortUser>>, response: Response<List<ShortUser>>) {
                    if (response.isSuccessful && response.body() != null) {
                        val userServers = response.body()!!
                        users = userServers
                        setupUserSpinner()
                    } else {
                        LogHelper.e("MgProfileViewActivity: Ошибка загрузки списка пользователей: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<List<ShortUser>>, t: Throwable) {
                    LogHelper.e("MgProfileViewActivity: Ошибка сети при загрузке пользователей: ${t.localizedMessage}")
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

        // Создаем список для спиннера
        val spinnerItems = mutableListOf<String>()
        spinnerItems.add("Выберите пользователя...") // Заголовок
        
        filteredUsers.forEach { user ->
            val displayName = if (user.playerName.isNullOrEmpty()) {
                user.characterName ?: "Без имени"
            } else if (user.characterName.isNullOrEmpty()) {
                user.playerName
            } else {
                "${user.playerName} / ${user.characterName}"
            }
            spinnerItems.add(displayName)
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, spinnerItems)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.userSpinner.adapter = adapter
        
        // Восстанавливаем выбранного пользователя если он был
        selectedUserId?.let { userId ->
            val userIndex = filteredUsers.indexOfFirst { it.userId == userId }
            if (userIndex != -1) {
                binding.userSpinner.setSelection(userIndex + 1) // +1 потому что первый элемент - заголовок
            }
        }
    }

    private fun loadUserProfile(userId: String) {
        RetrofitClient.userProfileApi.getUserProfile(userId)
            .enqueue(object : Callback<User> {
                override fun onResponse(call: Call<User>, response: Response<User>) {
                    if (response.isSuccessful && response.body() != null) {
                        val userServer = response.body()!!
                        profileFragment.showProfile(userServer)
                    } else {
                        profileFragment.showError("Ошибка загрузки профиля: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<User>, t: Throwable) {
                    profileFragment.showError("Ошибка сети: ${t.localizedMessage}")
                }
            })
    }
}
