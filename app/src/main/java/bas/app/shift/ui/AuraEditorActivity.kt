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
import bas.app.shift.helpers.DisplayNames
import bas.app.shift.helpers.LogHelper
import bas.app.shift.helpers.NetworkErrors
import bas.app.shift.models.Aura
import bas.app.shift.models.AuraMark
import bas.app.shift.ui.AuraMarkCallback
import bas.app.shift.models.AuraMarkRequest
import bas.app.shift.models.AuraProblem
import bas.app.shift.models.AuraProblemRequest
import bas.app.shift.models.AuraMarkResponse
import bas.app.shift.models.AuraMarkType
import bas.app.shift.models.AuraProblemType
import bas.app.shift.models.ShortUser
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AuraEditorActivity : AppCompatActivity(), AuraMarkCallback, AuraEditorCallback {
    private lateinit var binding: ActivityAuraEditorBinding
    private var users: List<ShortUser> = emptyList()
    private var filteredUsers: List<ShortUser> = emptyList()
    private var selectedUser: ShortUser? = null
    private lateinit var auraFragment: AuraFragment
    private var isAuraVisible = true // Флаг видимости ауры для редактирования

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogHelper.d("AuraEditorActivity: onCreate called")
        binding = ActivityAuraEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadUsers()
    }

    override fun onResume() {
        super.onResume()
        LogHelper.d("AuraEditorActivity: onResume called")
        // Callback устанавливается автоматически в setupUI
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        LogHelper.d("AuraEditorActivity: onCreateOptionsMenu called")
        menuInflater.inflate(R.menu.aura_editor_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        LogHelper.d("AuraEditorActivity: onPrepareOptionsMenu called - selectedUser: ${selectedUser?.userId}")
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
        LogHelper.d("AuraEditorActivity: onOptionsItemSelected called - itemId: ${item.itemId}")
        return when (item.itemId) {
            R.id.action_add_mark -> {
                LogHelper.d("AuraEditorActivity: Add mark action selected")
                if (selectedUser != null) {
                    showAddMarkDialog()
                }
                true
            }
            R.id.action_toggle_aura_visibility -> {
                LogHelper.d("AuraEditorActivity: Toggle aura visibility action selected")
                toggleAuraVisibility()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onMarkLongTap(mark: AuraMark) {
        LogHelper.d("AuraEditorActivity: onMarkLongTap called for mark: ${mark.markId}")
        showEditMarkDialog(mark)
    }
    
    override fun onProblemLongTap(slot: Int, problem: AuraProblem?) {
        LogHelper.d("AuraEditorActivity: onProblemLongTap called for slot: $slot, problem: ${problem?.name}")
        showEditProblemDialog(slot, problem)
    }
    
    override fun onAuraLoaded(aura: Aura) {
        LogHelper.d("AuraEditorActivity: onAuraLoaded called - userId: ${aura.userId}, auraHidden: ${aura.auraHidden}")
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

        // Создаем фрагмент ауры. commitNow() синхронно доводит фрагмент до onViewCreated,
        // поэтому callback можно ставить сразу — без post{} и связанной с ним гонки
        // (AU5: до отработки post долгое нажатие по метке могло не сработать).
        auraFragment = AuraFragment()
        supportFragmentManager.beginTransaction()
            .replace(binding.auraContainer.id, auraFragment)
            .commitNow()

        LogHelper.d("AuraEditorActivity: Setting callbacks to auraFragment")
        auraFragment.setMarkCallback(this)
        auraFragment.setAuraEditorCallback(this)
        LogHelper.d("AuraEditorActivity: Callbacks set successfully")
    }

    private fun loadUsers() {
        LogHelper.d("AuraEditorActivity: loadUsers called")
        RetrofitClient.userProfileApi.getAllUserShortProfiles()
            .enqueue(object : Callback<List<ShortUser>> {
                override fun onResponse(call: Call<List<ShortUser>>, response: Response<List<ShortUser>>) {
                    binding.loadingLayout.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        users = response.body()!!
                        LogHelper.d("AuraEditorActivity: Users loaded successfully: ${users.size}")
                        setupUserSpinner()
                        binding.userSelectionLayout.visibility = View.VISIBLE
                    } else {
                        LogHelper.e("AuraEditorActivity: Ошибка загрузки списка пользователей: ${response.code()}")
                        Toast.makeText(this@AuraEditorActivity, NetworkErrors.http(response.code()), Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<List<ShortUser>>, t: Throwable) {
                    LogHelper.e("AuraEditorActivity: Ошибка сети при загрузке пользователей: ${t.localizedMessage}")
                    binding.loadingLayout.visibility = View.GONE
                    Toast.makeText(this@AuraEditorActivity, NetworkErrors.network(t), Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun setupUserSpinner() {
        LogHelper.d("AuraEditorActivity: setupUserSpinner called with ${users.size} users")
        // Сортируем пользователей по алфавиту и фильтруем MG пользователей
        filteredUsers = users
            .filter { user -> !user.userId.startsWith("MG") } // Исключаем MG пользователей
            .sortedBy { user ->
                DisplayNames.combinePlayerFirst(user.playerName, user.characterName, "").lowercase()
            }

        LogHelper.d("AuraEditorActivity: Filtered users: ${filteredUsers.size}")

        // Создаем список для селектора
        val userItems = mutableListOf<String>()
        userItems.add("Выберите пользователя...") // Заголовок

        filteredUsers.forEach { user ->
            val displayName = DisplayNames.combinePlayerFirst(user.playerName, user.characterName, "Без имени")
            userItems.add(displayName)
        }

        val userAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, userItems)
        binding.userAutoComplete.setAdapter(userAdapter)

        // Обработчик выбора пользователя
        binding.userAutoComplete.setOnItemClickListener { _, _, position, _ ->
            if (position > 0 && position <= filteredUsers.size) {
                selectedUser = filteredUsers[position - 1]
                LogHelper.d("AuraEditorActivity: User selected: ${selectedUser!!.userId}")
                loadUserAura(selectedUser!!.userId)
                invalidateOptionsMenu() // Обновляем меню
            } else {
                selectedUser = null
                LogHelper.d("AuraEditorActivity: No user selected")
                clearAura()
                invalidateOptionsMenu() // Обновляем меню
            }
        }
    }

    private fun loadUserAura(userId: String) {
        LogHelper.d("AuraEditorActivity: loadUserAura called for userId: $userId")
        // Загружаем ауру пользователя через фрагмент
        auraFragment.loadUserAura(userId)
        
        // Обновляем состояние видимости ауры в зависимости от загруженной ауры
        // Это будет вызвано после загрузки ауры
    }

    private fun clearAura() {
        LogHelper.d("AuraEditorActivity: clearAura called")
        // Очищаем ауру
        auraFragment.clearAura()
    }

    private fun showAddMarkDialog() {
        LogHelper.d("AuraEditorActivity: showAddMarkDialog called")
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
                AuraMarkType.MARK_OF_CREATION -> getString(R.string.mark_of_creation)
                AuraMarkType.ABILITY -> getString(R.string.ability)
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
        LogHelper.d("AuraEditorActivity: showEditMarkDialog called for mark: ${mark.markId}")
        val dialogBinding = DialogEditAuraMarkBinding.inflate(LayoutInflater.from(this))
        
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
                AuraMarkType.MARK_OF_CREATION -> getString(R.string.mark_of_creation)
                AuraMarkType.ABILITY -> getString(R.string.ability)
                AuraMarkType.MAGIC_CONTRACT -> getString(R.string.magic_contract)
                AuraMarkType.FAMILIAR_LINK -> getString(R.string.familiar_link)
                AuraMarkType.MAGIC_LINK -> getString(R.string.magic_link)
                AuraMarkType.ARTIFACT_LINK -> getString(R.string.artifact_link)
                AuraMarkType.FOREIGN_PLANE_INFLUENCE -> getString(R.string.foreign_plane_influence)
            }
        }
        
        val markTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, markTypeNames)
        dialogBinding.markTypeSpinner.setAdapter(markTypeAdapter)
        
        // Заполняем поля данными метки
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
        LogHelper.d("AuraEditorActivity: addAuraMark called for user: ${selectedUser?.userId}")
        if (selectedUser == null) return
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.auraApi.addAuraMark(selectedUser!!.userId, markRequest)
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val markResponse = response.body()!!
                        if (markResponse.success) {
                            LogHelper.d("AuraEditorActivity: Mark added successfully")
                            Toast.makeText(this@AuraEditorActivity, getString(R.string.mark_added_success), Toast.LENGTH_LONG).show()
                            // Закрываем диалог
                            dialog.dismiss()
                            // Перезагружаем ауру пользователя
                            loadUserAura(selectedUser!!.userId)
                        } else {
                            LogHelper.w("AuraEditorActivity: Mark not added - server returned false")
                            Toast.makeText(this@AuraEditorActivity, getString(R.string.mark_not_added), Toast.LENGTH_LONG).show()
                        }
                    } else {
                        val errorMsg = NetworkErrors.http(response.code())
                        LogHelper.e("AuraEditorActivity: Error adding mark: $errorMsg")
                        Toast.makeText(this@AuraEditorActivity, getString(R.string.mark_add_error, errorMsg), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = NetworkErrors.network(e)
                    LogHelper.e("AuraEditorActivity: Exception adding mark: $errorMsg")
                    Toast.makeText(this@AuraEditorActivity, getString(R.string.mark_add_error, errorMsg), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun updateAuraMark(markId: Int, markRequest: AuraMarkRequest, dialog: AlertDialog) {
        LogHelper.d("AuraEditorActivity: updateAuraMark called for markId: $markId, user: ${selectedUser?.userId}")
        if (selectedUser == null) return
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.auraApi.updateAuraMark(selectedUser!!.userId, markId, markRequest)
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        LogHelper.d("AuraEditorActivity: Mark updated successfully")
                        Toast.makeText(this@AuraEditorActivity, getString(R.string.mark_updated_success), Toast.LENGTH_LONG).show()
                        // Закрываем диалог
                        dialog.dismiss()
                        // Перезагружаем ауру пользователя
                        loadUserAura(selectedUser!!.userId)
                    } else {
                        val errorMsg = NetworkErrors.http(response.code())
                        LogHelper.e("AuraEditorActivity: Error updating mark: $errorMsg")
                        Toast.makeText(this@AuraEditorActivity, getString(R.string.mark_update_error, errorMsg), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = NetworkErrors.network(e)
                    LogHelper.e("AuraEditorActivity: Exception updating mark: $errorMsg")
                    Toast.makeText(this@AuraEditorActivity, getString(R.string.mark_update_error, errorMsg), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun deleteAuraMark(markId: Int, dialog: AlertDialog) {
        LogHelper.d("AuraEditorActivity: deleteAuraMark called for markId: $markId, user: ${selectedUser?.userId}")
        if (selectedUser == null) return
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.auraApi.deleteAuraMark(selectedUser!!.userId, markId)
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        LogHelper.d("AuraEditorActivity: Mark deleted successfully")
                        Toast.makeText(this@AuraEditorActivity, getString(R.string.mark_deleted_success), Toast.LENGTH_LONG).show()
                        // Закрываем диалог
                        dialog.dismiss()
                        // Перезагружаем ауру пользователя
                        loadUserAura(selectedUser!!.userId)
                    } else {
                        val errorMsg = NetworkErrors.http(response.code())
                        LogHelper.e("AuraEditorActivity: Error deleting mark: $errorMsg")
                        Toast.makeText(this@AuraEditorActivity, getString(R.string.mark_delete_error, errorMsg), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = NetworkErrors.network(e)
                    LogHelper.e("AuraEditorActivity: Exception deleting mark: $errorMsg")
                    Toast.makeText(this@AuraEditorActivity, getString(R.string.mark_delete_error, errorMsg), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun showEditProblemDialog(slot: Int, problem: AuraProblem?) {
        LogHelper.d("AuraEditorActivity: showEditProblemDialog called for slot: $slot, problem: ${problem?.name}")
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
        LogHelper.d("AuraEditorActivity: addAuraProblem called for user: ${selectedUser?.userId}, slot: ${problemRequest.slot}")
        if (selectedUser == null) return
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.auraApi.addAuraProblem(selectedUser!!.userId, problemRequest)
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        LogHelper.d("AuraEditorActivity: Problem added successfully")
                        Toast.makeText(this@AuraEditorActivity, getString(R.string.problem_added_success), Toast.LENGTH_LONG).show()
                        // Закрываем диалог
                        dialog.dismiss()
                        // Перезагружаем ауру пользователя
                        loadUserAura(selectedUser!!.userId)
                    } else {
                        val errorMsg = NetworkErrors.http(response.code())
                        LogHelper.e("AuraEditorActivity: Error adding problem: $errorMsg")
                        Toast.makeText(this@AuraEditorActivity, getString(R.string.problem_add_error, errorMsg), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = NetworkErrors.network(e)
                    LogHelper.e("AuraEditorActivity: Exception adding problem: $errorMsg")
                    Toast.makeText(this@AuraEditorActivity, getString(R.string.problem_add_error, errorMsg), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun updateAuraProblem(slot: Int, problemRequest: AuraProblemRequest, dialog: AlertDialog) {
        LogHelper.d("AuraEditorActivity: updateAuraProblem called for user: ${selectedUser?.userId}, slot: $slot")
        if (selectedUser == null) return
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.auraApi.updateAuraProblem(selectedUser!!.userId, slot, problemRequest)
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        LogHelper.d("AuraEditorActivity: Problem updated successfully")
                        Toast.makeText(this@AuraEditorActivity, getString(R.string.problem_updated_success), Toast.LENGTH_LONG).show()
                        // Закрываем диалог
                        dialog.dismiss()
                        // Перезагружаем ауру пользователя
                        loadUserAura(selectedUser!!.userId)
                    } else {
                        val errorMsg = NetworkErrors.http(response.code())
                        LogHelper.e("AuraEditorActivity: Error updating problem: $errorMsg")
                        Toast.makeText(this@AuraEditorActivity, getString(R.string.problem_update_error, errorMsg), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = NetworkErrors.network(e)
                    LogHelper.e("AuraEditorActivity: Exception updating problem: $errorMsg")
                    Toast.makeText(this@AuraEditorActivity, getString(R.string.problem_update_error, errorMsg), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun deleteAuraProblem(slot: Int, dialog: AlertDialog) {
        LogHelper.d("AuraEditorActivity: deleteAuraProblem called for user: ${selectedUser?.userId}, slot: $slot")
        if (selectedUser == null) return
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.auraApi.deleteAuraProblem(selectedUser!!.userId, slot)
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        LogHelper.d("AuraEditorActivity: Problem deleted successfully")
                        Toast.makeText(this@AuraEditorActivity, getString(R.string.problem_deleted_success), Toast.LENGTH_LONG).show()
                        // Закрываем диалог
                        dialog.dismiss()
                        // Перезагружаем ауру пользователя
                        loadUserAura(selectedUser!!.userId)
                    } else {
                        val errorMsg = NetworkErrors.http(response.code())
                        LogHelper.e("AuraEditorActivity: Error deleting problem: $errorMsg")
                        Toast.makeText(this@AuraEditorActivity, getString(R.string.problem_delete_error, errorMsg), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = NetworkErrors.network(e)
                    LogHelper.e("AuraEditorActivity: Exception deleting problem: $errorMsg")
                    Toast.makeText(this@AuraEditorActivity, getString(R.string.problem_delete_error, errorMsg), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun toggleAuraVisibility() {
        LogHelper.d("AuraEditorActivity: toggleAuraVisibility called, current isAuraVisible: $isAuraVisible")
        isAuraVisible = !isAuraVisible
        auraFragment.setAuraVisibility(isAuraVisible)
        invalidateOptionsMenu()
        LogHelper.d("AuraEditorActivity: Aura visibility toggled to: $isAuraVisible")
    }
}
