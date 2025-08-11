package bas.app.shift.ui

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import bas.app.shift.api.RetrofitClient
import bas.app.shift.databinding.ActivityMgProfileViewBinding
import bas.app.shift.helpers.LogHelper
import bas.app.shift.models.User
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MgProfileViewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMgProfileViewBinding
    private var users: List<User> = emptyList()
    private var filteredUsers: List<User> = emptyList() // Добавляем переменную для отфильтрованных пользователей
    private lateinit var profileFragment: ProfileFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMgProfileViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadUsers()
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
                    loadUserProfile(selectedUser.userId)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Ничего не делаем
            }
        }

        // Создаем фрагмент профиля
        profileFragment = ProfileFragment()
        supportFragmentManager.beginTransaction()
            .replace(binding.profileContainer.id, profileFragment)
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
                        LogHelper.e("MgProfileViewActivity: Ошибка загрузки списка пользователей: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<List<User>>, t: Throwable) {
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
    }

    private fun loadUserProfile(userId: String) {
        RetrofitClient.userProfileApi.getUserProfile(userId)
            .enqueue(object : Callback<User> {
                override fun onResponse(call: Call<User>, response: Response<User>) {
                    if (response.isSuccessful && response.body() != null) {
                        val user = response.body()!!
                        profileFragment.showProfile(user)
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
