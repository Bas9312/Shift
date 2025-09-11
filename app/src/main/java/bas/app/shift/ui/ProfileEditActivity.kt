package bas.app.shift.ui

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import bas.app.shift.api.RetrofitClient
import bas.app.shift.databinding.ActivityProfileEditBinding
import bas.app.shift.helpers.LogHelper
import bas.app.shift.models.User
import bas.app.shift.models.UserServer
import bas.app.shift.models.UserDisplay
import bas.app.shift.models.NamedEntity
import bas.app.shift.models.Ability
import bas.app.shift.models.FamiliarData
import bas.app.shift.models.UserUpdateRequest
import bas.app.shift.models.toUser
import bas.app.shift.models.toUserDisplay
import bas.app.shift.constants.ReferenceData
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ProfileEditActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileEditBinding
    private var currentUser: User? = null
    private var currentUserDisplay: UserDisplay? = null
    private var allAbilities: List<Ability> = emptyList()
    private var allModules: List<NamedEntity> = ReferenceData.MODULES
    private var allDisciplines: List<NamedEntity> = ReferenceData.DISCIPLINES
    private lateinit var profileEditFragment: ProfileEditFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadUserProfile()
        loadAbilities()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Редактирование профиля"

        // Создаем фрагмент редактирования профиля
        profileEditFragment = ProfileEditFragment()
        supportFragmentManager.beginTransaction()
            .replace(binding.profileContainer.id, profileEditFragment)
            .commit()
    }

    private fun loadUserProfile() {
        val userId = intent.getStringExtra("user_id")
        if (userId == null) {
            Toast.makeText(this, "Ошибка: не указан ID пользователя", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        RetrofitClient.userProfileApi.getUserProfile(userId)
            .enqueue(object : Callback<UserServer> {
                override fun onResponse(call: Call<UserServer>, response: Response<UserServer>) {
                    if (response.isSuccessful && response.body() != null) {
                        val userServer = response.body()!!
                        currentUser = userServer.toUser(allDisciplines, allModules, allAbilities)
                        updateUserDisplay()
                    } else {
                        LogHelper.e("ProfileEditActivity: Ошибка загрузки профиля: ${response.code()}")
                        Toast.makeText(this@ProfileEditActivity, "Ошибка загрузки профиля: ${response.code()}", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<UserServer>, t: Throwable) {
                    LogHelper.e("ProfileEditActivity: Ошибка сети при загрузке профиля: ${t.localizedMessage}")
                    Toast.makeText(this@ProfileEditActivity, "Ошибка сети: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            })
    }

    fun loadAbilities() {
        RetrofitClient.userProfileApi.getAllAbilities()
            .enqueue(object : Callback<List<Ability>> {
                override fun onResponse(call: Call<List<Ability>>, response: Response<List<Ability>>) {
                    if (response.isSuccessful && response.body() != null) {
                        allAbilities = response.body()!!
                        profileEditFragment.setAbilities(allAbilities)
                    } else {
                        LogHelper.e("ProfileEditActivity: Ошибка загрузки способностей: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<List<Ability>>, t: Throwable) {
                    LogHelper.e("ProfileEditActivity: Ошибка сети при загрузке способностей: ${t.localizedMessage}")
                }
            })
    }


    fun saveProfile(updateRequest: UserUpdateRequest) {
        if (currentUser == null) {
            Toast.makeText(this, "Ошибка: профиль не загружен", Toast.LENGTH_SHORT).show()
            return
        }

        RetrofitClient.userProfileApi.updateUserProfile(currentUser!!.userId, updateRequest)
            .enqueue(object : Callback<UserServer> {
                override fun onResponse(call: Call<UserServer>, response: Response<UserServer>) {
                    if (response.isSuccessful && response.body() != null) {
                        Toast.makeText(this@ProfileEditActivity, "Профиль успешно обновлен", Toast.LENGTH_SHORT).show()
                        val userServer = response.body()!!
                        currentUser = userServer.toUser(allDisciplines, allModules, allAbilities)
                        updateUserDisplay()
                    } else {
                        LogHelper.e("ProfileEditActivity: Ошибка обновления профиля: ${response.code()}")
                        Toast.makeText(this@ProfileEditActivity, "Ошибка обновления профиля: ${response.code()}", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<UserServer>, t: Throwable) {
                    LogHelper.e("ProfileEditActivity: Ошибка сети при обновлении профиля: ${t.localizedMessage}")
                    Toast.makeText(this@ProfileEditActivity, "Ошибка сети: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            })
    }


    private fun updateUserDisplay() {
        if (currentUser != null) {
            currentUserDisplay = currentUser!!.toUserDisplay()
            profileEditFragment.showProfile(currentUserDisplay!!)
            profileEditFragment.setModules(allModules)
            profileEditFragment.setDisciplines(allDisciplines)
        }
    }

    fun getAbilities(): List<Ability> = allAbilities
    fun getModules(): List<NamedEntity> = allModules
    fun getDisciplines(): List<NamedEntity> = allDisciplines
}
