package bas.app.shift.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Настройка тулбара
        binding.toolbar.title = "Профиль"
        binding.toolbar.setTitleTextColor(getColor(android.R.color.white))
        binding.toolbar.setNavigationIconTint(getColor(android.R.color.white))
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        val userId = UserPrefsHelper.getUserId(this)
        fetchProfile(userId)
    }

    private fun fetchProfile(userId: String) {
        RetrofitClient.userProfileApi.getUserProfile(userId)
            .enqueue(object : Callback<User> {
                override fun onResponse(call: Call<User>, response: Response<User>) {
                    if (response.isSuccessful && response.body() != null) {
                        val user = response.body()!!
                        showProfile(user)
                        // Сохраняем актуальные данные пользователя
                        UserPrefsHelper.saveUserData(this@ProfileActivity, user)
                    } else {
                        showError("Ошибка загрузки профиля: ${response.code()}")
                    }
                }
                override fun onFailure(call: Call<User>, t: Throwable) {
                    showError("Ошибка сети: ${t.localizedMessage}")
                }
            })
    }

    private fun showProfile(user: User) {
        // Имя персонажа
        binding.profileCharacterName.text = user.characterName ?: "Имя персонажа не указано"
        // Имя игрока
        binding.profilePlayerName.text = user.playerName ?: "Имя игрока не указано"
        // Дисциплины
        val disciplinesLayout = binding.profileDisciplinesList
        disciplinesLayout.removeAllViews()
        if (user.disciplines.isNotEmpty()) {
            user.disciplines.forEach {
                val tv = TextView(this)
                tv.text = it.name
                disciplinesLayout.addView(tv)
            }
        } else {
            val tv = TextView(this)
            tv.text = "Нет дисциплин"
            disciplinesLayout.addView(tv)
        }
        // Модули
        val modulesLayout = binding.profileModulesList
        modulesLayout.removeAllViews()
        if (user.modules.isNotEmpty()) {
            user.modules.forEach {
                val tv = TextView(this)
                tv.text = it.name
                modulesLayout.addView(tv)
            }
        } else {
            val tv = TextView(this)
            tv.text = "Нет модулей"
            modulesLayout.addView(tv)
        }
        // Способности
        val abilitiesLayout = binding.profileAbilitiesList
        abilitiesLayout.removeAllViews()
        if (user.abilities.isNotEmpty()) {
            user.abilities.forEach {
                val tv = TextView(this)
                tv.text = it
                abilitiesLayout.addView(tv)
            }
        } else {
            val tv = TextView(this)
            tv.text = "Нет способностей"
            abilitiesLayout.addView(tv)
        }
        // Артефакты (только если есть Артефактология)
        val hasArtifactology = user.disciplines.any { it.name == "Артефактология" || it.id == 1 }
        val artifactsSection = binding.profileArtifactsSection
        if (hasArtifactology) {
            artifactsSection.visibility = View.VISIBLE
            val artifactsLayout = binding.profileArtifactsList
            artifactsLayout.removeAllViews()
            if (user.artifacts.isNotEmpty()) {
                user.artifacts.forEach { artifact ->
                    val tv = TextView(this)
                    tv.text = artifact.name
                    tv.isClickable = true
                    tv.setOnClickListener {
                        val intent = Intent(this, ArtifactActivity::class.java)
                        intent.putExtra("artifact_id", artifact.id)
                        startActivity(intent)
                    }
                    artifactsLayout.addView(tv)
                }
            } else {
                val tv = TextView(this)
                tv.text = "Нет артефактов"
                artifactsLayout.addView(tv)
            }
        } else {
            artifactsSection.visibility = View.GONE
        }
        // Инструмент
        binding.profileInstrument.text = user.instrument ?: "Инструмент не указан"
        // Фамильяр
        binding.profileFamiliar.text = user.familiar ?: "Фамильяр не указан"
        // Прочее
        val miscLayout = binding.profileMiscList
        miscLayout.removeAllViews()
        if (user.misc.isNotEmpty()) {
            user.misc.forEach {
                val tv = TextView(this)
                tv.text = it
                miscLayout.addView(tv)
            }
        } else {
            val tv = TextView(this)
            tv.text = "Нет особенностей"
            miscLayout.addView(tv)
        }
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        LogHelper.e("ProfileActivity $msg")
    }
} 