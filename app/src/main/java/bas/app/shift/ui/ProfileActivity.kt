package bas.app.shift.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import bas.app.shift.api.RetrofitClient
import bas.app.shift.databinding.ActivityProfileBinding
import bas.app.shift.helpers.LogHelper
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.models.User
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding
    private lateinit var profileFragment: ProfileFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Настройка тулбара
        binding.toolbar.title = "Профиль"
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // Создаем фрагмент профиля
        profileFragment = ProfileFragment()
        supportFragmentManager.beginTransaction()
            .replace(binding.profileContainer.id, profileFragment)
            .commit()

        val userId = UserPrefsHelper.getUserId(this)
        fetchProfile(userId)
    }

    private fun fetchProfile(userId: String) {
        RetrofitClient.userProfileApi.getUserProfile(userId)
            .enqueue(object : Callback<User> {
                override fun onResponse(call: Call<User>, response: Response<User>) {
                    if (response.isSuccessful && response.body() != null) {
                        val user = response.body()!!
                        profileFragment.showProfile(user)
                        // Сохраняем актуальные данные пользователя
                        UserPrefsHelper.saveUserData(this@ProfileActivity, user)
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