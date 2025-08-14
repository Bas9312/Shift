package bas.app.shift.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import bas.app.shift.R
import bas.app.shift.api.RetrofitClient
import bas.app.shift.databinding.ActivityAuraEditorBinding
import bas.app.shift.databinding.DialogAddAuraMarkBinding
import bas.app.shift.databinding.DialogEditAuraMarkBinding
import bas.app.shift.databinding.DialogEditAuraProblemBinding
import bas.app.shift.helpers.LogHelper
import bas.app.shift.models.Aura
import bas.app.shift.models.AuraMark
import bas.app.shift.ui.AuraMarkCallback
import bas.app.shift.models.AuraMarkRequest
import bas.app.shift.models.AuraProblem
import bas.app.shift.models.AuraProblemRequest
import bas.app.shift.models.AuraMarkResponse
import bas.app.shift.models.AuraMarkType
import bas.app.shift.models.AuraProblemType
import bas.app.shift.models.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AuraEditorActivity : AppCompatActivity(), AuraMarkCallback, AuraEditorCallback {
    private lateinit var binding: ActivityAuraEditorBinding
    private var users: List<User> = emptyList()
    private var filteredUsers: List<User> = emptyList() // Добавляем переменную для отфильтрованных пользователей
    private var selectedUser: User? = null
    private lateinit var auraFragment: AuraFragment
    private var isAuraVisible = true // Флаг видимости ауры для редактирования

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuraEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadUsers()
    }

    override fun onResume() {
        super.onResume()
        // Callback устанавливается автоматически в setupUI
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.aura_editor_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val addMarkItem = menu.findItem(R.id.action_add_mark)
        addMarkItem.isEnabled = selectedUser != null
        
        // Обновляем иконку кнопки показа/скрытия ауры
        val toggleVisibilityItem = menu.findItem(R.id.action_toggle_aura_visibility)
        if (toggleVisibilityItem != null) {
            if (isAuraVisible) {
                toggleVisibilityItem.setIcon(R.drawable.ic_eye_off)
                toggleVisibilityItem.title = getString(R.string.hide_aura)
            } else {
                toggleVisibilityItem.setIcon(R.drawable.ic_eye)
                toggleVisibilityItem.title = getString(R.string.show_aura)
            }
        }
        
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add_mark -> {
                if (selectedUser != null) {
                    showAddMarkDialog()
                }
                true
            }
            R.id.action_toggle_aura_visibility -> {
                toggleAuraVisibility()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onMarkLongTap(mark: AuraMark) {
        showEditMarkDialog(mark)
    }
    
    override fun onProblemLongTap(slot: Int, problem: AuraProblem?) {
        showEditProblemDialog(slot, problem)
    }
    
    override fun onAuraLoaded(aura: Aura) {
        // Устанавливаем начальное состояние видимости в зависимости от загруженной ауры
        isAuraVisible = !aura.auraHidden
        // Обновляем меню
        invalidateOptionsMenu()
        
        // Передаём состояние в canvas (null = использовать серверное значение)
        auraFragment.setAuraVisibility(null)
        
        LogHelper.d("AuraEditorActivity: onAuraLoaded - auraHidden=${aura.auraHidden}, isAuraVisible=$isAuraVisible")
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // Создаем фрагмент ауры
        auraFragment = AuraFragment()
        supportFragmentManager.beginTransaction()
            .replace(binding.auraContainer.id, auraFragment)
            .commit()
        
        // Устанавливаем callback с небольшой задержкой, чтобы фрагмент успел создаться
        binding.auraContainer.post {
            auraFragment.setMarkCallback(this)
            auraFragment.setAuraEditorCallback(this)
        }
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
                invalidateOptionsMenu() // Обновляем меню
            } else {
                selectedUser = null
                clearAura()
                invalidateOptionsMenu() // Обновляем меню
            }
        }
    }

    private fun loadUserAura(userId: String) {
        // Загружаем ауру пользователя через фрагмент
        auraFragment.loadUserAura(userId)
        
        // Обновляем состояние видимости ауры в зависимости от загруженной ауры
        // Это будет вызвано после загрузки ауры
    }

    private fun clearAura() {
        // Очищаем ауру
        auraFragment.clearAura()
    }

    private fun showAddMarkDialog() {
        val dialogBinding = DialogAddAuraMarkBinding.inflate(LayoutInflater.from(this))
        
        // Настраиваем селектор типов меток
        val markTypes = AuraMarkType.values()
        val markTypeNames = markTypes.map { 
            when (it) {
                AuraMarkType.MAGIC_DISCIPLINE -> getString(R.string.magic_discipline)
                AuraMarkType.BLESSING -> getString(R.string.blessing)
                AuraMarkType.CURSE -> getString(R.string.curse)
                AuraMarkType.JUDGE_STATUS -> getString(R.string.judge_status)
                AuraMarkType.CONTRACT_BREACH -> getString(R.string.contract_breach)
                AuraMarkType.INSTRUMENT_LINK -> getString(R.string.instrument_link)
                AuraMarkType.SPIRITUAL_BEING_INSIDE -> getString(R.string.spiritual_being_inside)
                AuraMarkType.MAGIC_CONTRACT -> getString(R.string.magic_contract)
                AuraMarkType.FAMILIAR_LINK -> getString(R.string.familiar_link)
                AuraMarkType.MAGIC_LINK -> getString(R.string.magic_link)
                AuraMarkType.ARTIFACT_LINK -> getString(R.string.artifact_link)
                AuraMarkType.FOREIGN_PLANE_INFLUENCE -> getString(R.string.foreign_plane_influence)
            }
        }
        
        val markTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, markTypeNames)
        dialogBinding.markTypeSpinner.setAdapter(markTypeAdapter)
        
        // Создаем и показываем диалог
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()
        
        // Обработчик кнопки сохранения
        dialogBinding.saveButton.setOnClickListener {
            val selectedPosition = markTypeNames.indexOf(dialogBinding.markTypeSpinner.text.toString())
            if (selectedPosition == -1) {
                Toast.makeText(this, getString(R.string.select_mark_type), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val markType = markTypes[selectedPosition]
            val imageUrl = dialogBinding.imageUrlInput.text.toString()
            val name = dialogBinding.nameInput.text.toString()
            val description = dialogBinding.descriptionInput.text.toString()
            val external = dialogBinding.externalCheckBox.isChecked
            
            if (name.isBlank()) {
                Toast.makeText(this, getString(R.string.enter_mark_name), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Создаем запрос
            val markRequest = AuraMarkRequest(
                markType = markType,
                imageUrl = imageUrl.ifBlank { "" },
                name = name,
                description = description.ifBlank { null },
                external = external
            )
            
            // Отправляем запрос и закрываем диалог
            addAuraMark(markRequest, dialog)
        }
        
        dialog.show()
    }
    
    private fun showEditMarkDialog(mark: AuraMark) {
        val dialogBinding = DialogEditAuraMarkBinding.inflate(LayoutInflater.from(this))
        
        // Заполняем поля текущими данными метки
        val markTypes = AuraMarkType.values()
        val markTypeNames = markTypes.map { 
            when (it) {
                AuraMarkType.MAGIC_DISCIPLINE -> getString(R.string.magic_discipline)
                AuraMarkType.BLESSING -> getString(R.string.blessing)
                AuraMarkType.CURSE -> getString(R.string.curse)
                AuraMarkType.JUDGE_STATUS -> getString(R.string.judge_status)
                AuraMarkType.CONTRACT_BREACH -> getString(R.string.contract_breach)
                AuraMarkType.INSTRUMENT_LINK -> getString(R.string.instrument_link)
                AuraMarkType.SPIRITUAL_BEING_INSIDE -> getString(R.string.spiritual_being_inside)
                AuraMarkType.MAGIC_CONTRACT -> getString(R.string.magic_contract)
                AuraMarkType.FAMILIAR_LINK -> getString(R.string.familiar_link)
                AuraMarkType.MAGIC_LINK -> getString(R.string.magic_link)
                AuraMarkType.ARTIFACT_LINK -> getString(R.string.artifact_link)
                AuraMarkType.FOREIGN_PLANE_INFLUENCE -> getString(R.string.foreign_plane_influence)
            }
        }
        
        val markTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, markTypeNames)
        dialogBinding.markTypeSpinner.setAdapter(markTypeAdapter)
        
        // Устанавливаем текущие значения
        val currentTypeIndex = markTypes.indexOf(mark.markType)
        if (currentTypeIndex != -1) {
            dialogBinding.markTypeSpinner.setText(markTypeNames[currentTypeIndex], false)
        }
        dialogBinding.imageUrlInput.setText(mark.imageUrl)
        dialogBinding.nameInput.setText(mark.name)
        dialogBinding.descriptionInput.setText(mark.description ?: "")
        dialogBinding.externalCheckBox.isChecked = mark.external == 1
        
        // Создаем и показываем диалог
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()
        
        // Обработчик кнопки сохранения
        dialogBinding.saveButton.setOnClickListener {
            val selectedPosition = markTypeNames.indexOf(dialogBinding.markTypeSpinner.text.toString())
            if (selectedPosition == -1) {
                Toast.makeText(this, getString(R.string.select_mark_type), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val markType = markTypes[selectedPosition]
            val imageUrl = dialogBinding.imageUrlInput.text.toString()
            val name = dialogBinding.nameInput.text.toString()
            val description = dialogBinding.descriptionInput.text.toString()
            val external = dialogBinding.externalCheckBox.isChecked
            
            if (name.isBlank()) {
                Toast.makeText(this, getString(R.string.enter_mark_name), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Создаем запрос для обновления
            val markRequest = AuraMarkRequest(
                markType = markType,
                imageUrl = imageUrl.ifBlank { "" },
                name = name,
                description = description.ifBlank { null },
                external = external
            )
            
            // Обновляем метку
            updateAuraMark(mark.markId, markRequest, dialog)
        }
        
        // Обработчик кнопки удаления
        dialogBinding.deleteButton.setOnClickListener {
            deleteAuraMark(mark.markId, dialog)
        }
        
        dialog.show()
    }
    
    private fun addAuraMark(markRequest: AuraMarkRequest, dialog: AlertDialog) {
        if (selectedUser == null) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.auraApi.addAuraMark(selectedUser!!.userId, markRequest)
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val markResponse = response.body()!!
                        if (markResponse.success) {
                            Toast.makeText(this@AuraEditorActivity, getString(R.string.mark_added_success), Toast.LENGTH_LONG).show()
                            // Закрываем диалог
                            dialog.dismiss()
                            // Перезагружаем ауру пользователя
                            loadUserAura(selectedUser!!.userId)
                        } else {
                            Toast.makeText(this@AuraEditorActivity, getString(R.string.mark_not_added), Toast.LENGTH_LONG).show()
                        }
                    } else {
                        val errorMsg = "HTTP ${response.code()}"
                        Toast.makeText(this@AuraEditorActivity, getString(R.string.mark_add_error, errorMsg), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = e.localizedMessage ?: "Неизвестная ошибка"
                    Toast.makeText(this@AuraEditorActivity, getString(R.string.mark_add_error, errorMsg), Toast.LENGTH_LONG).show()
                    LogHelper.e("AuraEditorActivity: Ошибка при добавлении метки: $errorMsg")
                }
            }
        }
    }
    
    private fun updateAuraMark(markId: Int, markRequest: AuraMarkRequest, dialog: AlertDialog) {
        if (selectedUser == null) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.auraApi.updateAuraMark(selectedUser!!.userId, markId, markRequest)
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@AuraEditorActivity, getString(R.string.mark_updated_success), Toast.LENGTH_LONG).show()
                        // Закрываем диалог
                        dialog.dismiss()
                        // Перезагружаем ауру пользователя
                        loadUserAura(selectedUser!!.userId)
                    } else {
                        val errorMsg = "HTTP ${response.code()}"
                        Toast.makeText(this@AuraEditorActivity, getString(R.string.mark_update_error, errorMsg), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = e.localizedMessage ?: "Неизвестная ошибка"
                    Toast.makeText(this@AuraEditorActivity, getString(R.string.mark_update_error, errorMsg), Toast.LENGTH_LONG).show()
                    LogHelper.e("AuraEditorActivity: Ошибка при обновлении метки: $errorMsg")
                }
            }
        }
    }
    
    private fun deleteAuraMark(markId: Int, dialog: AlertDialog) {
        if (selectedUser == null) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.auraApi.deleteAuraMark(selectedUser!!.userId, markId)
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@AuraEditorActivity, getString(R.string.mark_deleted_success), Toast.LENGTH_LONG).show()
                        // Закрываем диалог
                        dialog.dismiss()
                        // Перезагружаем ауру пользователя
                        loadUserAura(selectedUser!!.userId)
                    } else {
                        val errorMsg = "HTTP ${response.code()}"
                        Toast.makeText(this@AuraEditorActivity, getString(R.string.mark_delete_error, errorMsg), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = e.localizedMessage ?: "Неизвестная ошибка"
                    Toast.makeText(this@AuraEditorActivity, getString(R.string.mark_delete_error, errorMsg), Toast.LENGTH_LONG).show()
                    LogHelper.e("AuraEditorActivity: Ошибка при удалении метки: $errorMsg")
                }
            }
        }
    }
    
    private fun showEditProblemDialog(slot: Int, problem: AuraProblem?) {
        val dialogBinding = DialogEditAuraProblemBinding.inflate(LayoutInflater.from(this))
        
        // Настраиваем селектор типов проблем
        val problemTypes = AuraProblemType.values()
        val problemTypeNames = problemTypes.map { 
            when (it) {
                AuraProblemType.HOLE -> getString(R.string.hole)
                AuraProblemType.TEAR -> getString(R.string.tear)
                AuraProblemType.SCAR -> getString(R.string.scar)
                AuraProblemType.PARASITE -> getString(R.string.parasite)
                AuraProblemType.OTHER -> getString(R.string.other)
            }
        }
        
        val problemTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, problemTypeNames)
        dialogBinding.problemTypeSpinner.setAdapter(problemTypeAdapter)
        
        // Если проблема уже существует, заполняем поля её данными
        if (problem != null) {
            dialogBinding.dialogTitle.text = getString(R.string.edit_aura_problem_title)
            val currentTypeIndex = problemTypes.indexOf(problem.problemType)
            if (currentTypeIndex != -1) {
                dialogBinding.problemTypeSpinner.setText(problemTypeNames[currentTypeIndex], false)
            }
            dialogBinding.nameInput.setText(problem.name)
            dialogBinding.descriptionInput.setText(problem.description ?: "")
            dialogBinding.deleteButton.visibility = View.VISIBLE
        } else {
            dialogBinding.dialogTitle.text = getString(R.string.add_aura_problem_title)
            dialogBinding.deleteButton.visibility = View.GONE
        }
        
        // Создаем и показываем диалог
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()
        
        // Обработчик кнопки сохранения
        dialogBinding.saveButton.setOnClickListener {
            val selectedPosition = problemTypeNames.indexOf(dialogBinding.problemTypeSpinner.text.toString())
            if (selectedPosition == -1) {
                Toast.makeText(this, getString(R.string.select_problem_type), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val problemType = problemTypes[selectedPosition]
            val name = dialogBinding.nameInput.text.toString()
            val description = dialogBinding.descriptionInput.text.toString()
            
            if (name.isBlank()) {
                Toast.makeText(this, getString(R.string.enter_problem_name), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Создаем запрос
            val problemRequest = AuraProblemRequest(
                slot = slot,
                problemType = problemType,
                name = name,
                description = description.ifBlank { null }
            )
            
            // Если проблема уже существует, обновляем её, иначе создаём новую
            if (problem != null) {
                updateAuraProblem(slot, problemRequest, dialog)
            } else {
                addAuraProblem(problemRequest, dialog)
            }
        }
        
        // Обработчик кнопки удаления (только если проблема уже существует)
        dialogBinding.deleteButton.setOnClickListener {
            if (problem != null) {
                deleteAuraProblem(slot, dialog)
            }
        }
        
        dialog.show()
    }
    
    private fun addAuraProblem(problemRequest: AuraProblemRequest, dialog: AlertDialog) {
        if (selectedUser == null) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.auraApi.addAuraProblem(selectedUser!!.userId, problemRequest)
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@AuraEditorActivity, getString(R.string.problem_added_success), Toast.LENGTH_LONG).show()
                        // Закрываем диалог
                        dialog.dismiss()
                        // Перезагружаем ауру пользователя
                        loadUserAura(selectedUser!!.userId)
                    } else {
                        val errorMsg = "HTTP ${response.code()}"
                        Toast.makeText(this@AuraEditorActivity, getString(R.string.problem_add_error, errorMsg), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = e.localizedMessage ?: "Неизвестная ошибка"
                    Toast.makeText(this@AuraEditorActivity, getString(R.string.problem_add_error, errorMsg), Toast.LENGTH_LONG).show()
                    LogHelper.e("AuraEditorActivity: Ошибка при добавлении проблемы: $errorMsg")
                }
            }
        }
    }
    
    private fun updateAuraProblem(slot: Int, problemRequest: AuraProblemRequest, dialog: AlertDialog) {
        if (selectedUser == null) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.auraApi.updateAuraProblem(selectedUser!!.userId, slot, problemRequest)
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@AuraEditorActivity, getString(R.string.problem_updated_success), Toast.LENGTH_LONG).show()
                        // Закрываем диалог
                        dialog.dismiss()
                        // Перезагружаем ауру пользователя
                        loadUserAura(selectedUser!!.userId)
                    } else {
                        val errorMsg = "HTTP ${response.code()}"
                        Toast.makeText(this@AuraEditorActivity, getString(R.string.problem_update_error, errorMsg), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = e.localizedMessage ?: "Неизвестная ошибка"
                    Toast.makeText(this@AuraEditorActivity, getString(R.string.problem_update_error, errorMsg), Toast.LENGTH_LONG).show()
                    LogHelper.e("AuraEditorActivity: Ошибка при изменении проблемы: $errorMsg")
                }
            }
        }
    }
    
    private fun deleteAuraProblem(slot: Int, dialog: AlertDialog) {
        if (selectedUser == null) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.auraApi.deleteAuraProblem(selectedUser!!.userId, slot)
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@AuraEditorActivity, getString(R.string.problem_deleted_success), Toast.LENGTH_LONG).show()
                        // Закрываем диалог
                        dialog.dismiss()
                        // Перезагружаем ауру пользователя
                        loadUserAura(selectedUser!!.userId)
                    } else {
                        val errorMsg = "HTTP ${response.code()}"
                        Toast.makeText(this@AuraEditorActivity, getString(R.string.problem_delete_error, errorMsg), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = e.localizedMessage ?: "Неизвестная ошибка"
                    Toast.makeText(this@AuraEditorActivity, getString(R.string.problem_delete_error, errorMsg), Toast.LENGTH_LONG).show()
                    LogHelper.e("AuraEditorActivity: Ошибка при удалении проблемы: $errorMsg")
                }
            }
        }
    }
    
    private fun toggleAuraVisibility() {
        isAuraVisible = !isAuraVisible
        
        LogHelper.d("AuraEditorActivity: toggleAuraVisibility - isAuraVisible=$isAuraVisible")
        
        // Обновляем меню
        invalidateOptionsMenu()
        
        // Передаём состояние в фрагмент ауры
        // true = принудительно показать, false = принудительно скрыть
        auraFragment.setAuraVisibility(isAuraVisible)
        
        // Показываем уведомление
        val message = if (isAuraVisible) {
            getString(R.string.aura_shown)
        } else {
            getString(R.string.aura_hidden)
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
