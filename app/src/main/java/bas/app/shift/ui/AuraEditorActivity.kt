package bas.app.shift.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import bas.app.shift.R
import bas.app.shift.api.RetrofitClient
import bas.app.shift.databinding.ActivityAuraEditorBinding
import bas.app.shift.databinding.DialogAddAuraMarkBinding
import bas.app.shift.databinding.DialogEditAuraMarkBinding
import bas.app.shift.databinding.DialogEditAuraProblemBinding
import bas.app.shift.helpers.LogHelper
import bas.app.shift.helpers.TimePickerHelper
import bas.app.shift.helpers.DateTimeHelper
import bas.app.shift.models.Aura
import bas.app.shift.models.AuraMark
import bas.app.shift.models.AuraMarkRequest
import bas.app.shift.models.AuraProblem
import bas.app.shift.models.AuraProblemRequest
import bas.app.shift.models.AuraMarkType
import bas.app.shift.models.AuraProblemType
import bas.app.shift.models.AuraHiddenRequest
import bas.app.shift.models.ShortUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AuraEditorActivity : AppCompatActivity(), AuraMarkCallback, AuraEditorCallback {
    private lateinit var binding: ActivityAuraEditorBinding
    private var users: List<ShortUser> = emptyList()
    private var filteredUsers: List<ShortUser> = emptyList() // Добавляем переменную для отфильтрованных пользователей
    private var selectedUser: ShortUser? = null
    private lateinit var auraFragment: AuraFragment
    private var isAuraVisible = true // Флаг видимости ауры для редактирования
    private var serverAuraHidden = false // Серверное состояние скрытости ауры

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
                toggleAuraVisibilityVisual()
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
        // Сохраняем серверное состояние
        serverAuraHidden = aura.auraHidden
        
        // Устанавливаем начальное состояние видимости в зависимости от загруженной ауры
        isAuraVisible = !aura.auraHidden
        // Обновляем меню
        invalidateOptionsMenu()
        
        // Передаём состояние в canvas (null = использовать серверное значение)
        auraFragment.setAuraVisibility(null)
        
        // Показываем кнопку управления видимостью ауры
        binding.btnToggleAuraVisibility.visibility = View.VISIBLE
        updateAuraButton()
        
        LogHelper.d("AuraEditorActivity: onAuraLoaded - serverAuraHidden=${serverAuraHidden}, isAuraVisible=$isAuraVisible")
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
            // Устанавливаем режим редактора для AuraCanvasView
            auraFragment.setEditorMode(true)
        }
        
        // Обработчик кнопки управления видимостью ауры (отправляет запрос на сервер)
        binding.btnToggleAuraVisibility.setOnClickListener {
            toggleAuraVisibilityOnServer()
        }
    }

    private fun loadUsers() {
        // Показываем лоадер и скрываем выбор пользователя
        binding.loadingLayout.visibility = View.VISIBLE
        binding.userSelectionLayout.visibility = View.GONE
        
        RetrofitClient.userProfileApi.getAllUserShortProfiles()
            .enqueue(object : Callback<List<ShortUser>> {
                override fun onResponse(call: Call<List<ShortUser>>, response: Response<List<ShortUser>>) {
                    if (response.isSuccessful && response.body() != null) {
                        val userServers = response.body()!!
                        users = userServers
                        setupUserSpinner()
                    } else {
                        LogHelper.e("AuraEditorActivity: Ошибка загрузки списка пользователей: ${response.code()}")
                        showError("Ошибка загрузки пользователей: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<List<ShortUser>>, t: Throwable) {
                    LogHelper.e("AuraEditorActivity: Ошибка сети при загрузке пользователей: ${t.localizedMessage}")
                    showError("Ошибка сети: ${t.localizedMessage}")
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
        
        // Скрываем лоадер и показываем выбор пользователя
        binding.loadingLayout.visibility = View.GONE
        binding.userSelectionLayout.visibility = View.VISIBLE
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
        
        // Скрываем кнопку управления видимостью ауры
        binding.btnToggleAuraVisibility.visibility = View.GONE
    }
    
    private fun showError(message: String) {
        // Скрываем лоадер и показываем выбор пользователя даже при ошибке
        binding.loadingLayout.visibility = View.GONE
        binding.userSelectionLayout.visibility = View.VISIBLE
        
        // Показываем сообщение об ошибке
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showAddMarkDialog() {
        val dialogBinding = DialogAddAuraMarkBinding.inflate(LayoutInflater.from(this))
        
        
        // Настраиваем селектор типов меток (скрываем MAGIC_DISCIPLINE, INSTRUMENT_LINK, FAMILIAR_LINK, ABILITY)
        val hiddenTypes = setOf(
            AuraMarkType.MAGIC_DISCIPLINE,
            AuraMarkType.INSTRUMENT_LINK,
            AuraMarkType.FAMILIAR_LINK,
            AuraMarkType.ABILITY
        )
        val markTypes = AuraMarkType.values().filter { it !in hiddenTypes }
        val markTypeNames = markTypes.map { 
            when (it) {
                AuraMarkType.BLESSING -> getString(R.string.blessing)
                AuraMarkType.CURSE -> getString(R.string.curse)
                AuraMarkType.JUDGE_STATUS -> getString(R.string.judge_status)
                AuraMarkType.CONTRACT_BREACH -> getString(R.string.contract_breach)
                AuraMarkType.SPIRITUAL_BEING_INSIDE -> getString(R.string.spiritual_being_inside)
                AuraMarkType.MARK_OF_CREATION -> getString(R.string.mark_of_creation)
                AuraMarkType.MAGIC_CONTRACT -> getString(R.string.magic_contract)
                AuraMarkType.MAGIC_LINK -> getString(R.string.magic_link)
                AuraMarkType.ARTIFACT_LINK -> getString(R.string.artifact_link)
                AuraMarkType.FOREIGN_PLANE_INFLUENCE -> getString(R.string.foreign_plane_influence)
                else -> it.name // Fallback для любых других типов
            }
        }
        
        val markTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, markTypeNames)
        dialogBinding.markTypeSpinner.setAdapter(markTypeAdapter)
        
        // Настраиваем селектор магических дисциплин
        val disciplines = listOf(
            "Артефактология" to "artifactology",
            "Биомагия и микродозирование" to "biomagick_and_microdosing", 
            "Благословения и проклятия" to "blessing_and_curses",
            "Экстрасенсорика" to "extrasens",
            "Ментальная магия" to "mental_magic",
            "Шумомантия" to "noize_magick",
            "Предсказания" to "prophecy",
            "Ритуалистика" to "ritualistics",
            "Шаманизм" to "shamanizm"
        )
        
        val disciplineNames = disciplines.map { it.first }
        val disciplineAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, disciplineNames)
        dialogBinding.disciplineSpinner.setAdapter(disciplineAdapter)
        
        // Настраиваем селектор типов абилок
        val abilityTypes = listOf(
            "Защита" to "ability_protection_type",
            "Атака" to "ability_attack_type",
            "Усиление" to "ability_buff_type",
            "Ослабление" to "ability_debuff_type",
            "Изменение" to "ability_transform_type",
            "Познание" to "ability_insight_type",
            "Прочее" to "ability_misc"
        )
        
        val abilityTypeNames = abilityTypes.map { it.first }
        val abilityTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, abilityTypeNames)
        dialogBinding.abilityTypeSpinner.setAdapter(abilityTypeAdapter)
        
        // Настраиваем селектор типов проклятий
        val curseTypes = listOf(
            "Проклятие" to "curse",
            "Смертельное проклятие" to "death_curse"
        )
        
        val curseTypeNames = curseTypes.map { it.first }
        val curseTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, curseTypeNames)
        dialogBinding.curseTypeSpinner.setAdapter(curseTypeAdapter)
        
        // Настраиваем селектор типов меток пробуждения
        val creationTypes = listOf(
            "Метка пробуждения классическая" to "mark_of_creation_standart",
            "Метка пробуждения неомагическая" to "mark_of_creation_neo_by_site",
            "Метка пробуждения Местная" to "mark_of_creation_finnougr",
            "Метка пробуждения Хтонью" to "mark_of_creation_magic_creature"
        )
        
        val creationTypeNames = creationTypes.map { it.first }
        val creationTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, creationTypeNames)
        dialogBinding.creationTypeSpinner.setAdapter(creationTypeAdapter)
        
        // Настраиваем селектор типов магических связей
        val magicLinkTypes = listOf(
            "Магическая связь позитивная" to "magic_link_positive",
            "Магическая связь негативная" to "magic_link_negative",
            "Магическая связь нейтральная" to "magic_link_neutral",
            "Ментальная связь" to "mental_link"
        )
        
        val magicLinkTypeNames = magicLinkTypes.map { it.first }
        val magicLinkTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, magicLinkTypeNames)
        dialogBinding.magicLinkTypeSpinner.setAdapter(magicLinkTypeAdapter)
        
        // Настраиваем селектор типов влияния планов
        val planeTypes = listOf(
            "Влияние плана Демонов" to "demon_plane_influence",
            "Влияние плана Ангелов" to "angel_plane_influence",
            "Влияние плана Бездны" to "abuss_plane_influence"
        )
        
        val planeTypeNames = planeTypes.map { it.first }
        val planeTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, planeTypeNames)
        dialogBinding.planeTypeSpinner.setAdapter(planeTypeAdapter)
        
        // Обработчик изменения типа метки
        dialogBinding.markTypeSpinner.setOnItemClickListener { _, _, position, _ ->
            val selectedType = markTypes[position]
            
            // Сначала скрываем все спиннеры
            dialogBinding.disciplineLayout.visibility = View.GONE
            dialogBinding.abilityTypeLayout.visibility = View.GONE
            dialogBinding.curseTypeLayout.visibility = View.GONE
            dialogBinding.creationTypeLayout.visibility = View.GONE
            dialogBinding.magicLinkTypeLayout.visibility = View.GONE
            dialogBinding.planeTypeLayout.visibility = View.GONE
            
            // Устанавливаем галочку "внешний" для определенных типов
            val externalTypes = listOf(
                AuraMarkType.MAGIC_CONTRACT,
                AuraMarkType.MAGIC_LINK,
                AuraMarkType.ARTIFACT_LINK,
                AuraMarkType.FOREIGN_PLANE_INFLUENCE
            )
            
            if (selectedType in externalTypes) {
                dialogBinding.externalCheckBox.isChecked = true
            } else {
                dialogBinding.externalCheckBox.isChecked = false
            }
            
            when (selectedType) {
                AuraMarkType.CURSE -> {
                    dialogBinding.curseTypeLayout.visibility = View.VISIBLE
                    dialogBinding.starsLayout.visibility = View.GONE
                    dialogBinding.imageUrlLayout.visibility = View.GONE
                    dialogBinding.nameLayout.visibility = View.GONE
                }
                AuraMarkType.MARK_OF_CREATION -> {
                    dialogBinding.creationTypeLayout.visibility = View.VISIBLE
                    dialogBinding.starsLayout.visibility = View.GONE
                    dialogBinding.imageUrlLayout.visibility = View.GONE
                    dialogBinding.nameLayout.visibility = View.GONE
                }
                AuraMarkType.MAGIC_LINK -> {
                    dialogBinding.magicLinkTypeLayout.visibility = View.VISIBLE
                    dialogBinding.starsLayout.visibility = View.GONE
                    dialogBinding.imageUrlLayout.visibility = View.GONE
                    dialogBinding.nameLayout.visibility = View.GONE
                }
                AuraMarkType.FOREIGN_PLANE_INFLUENCE -> {
                    dialogBinding.planeTypeLayout.visibility = View.VISIBLE
                    dialogBinding.starsLayout.visibility = View.GONE
                    dialogBinding.imageUrlLayout.visibility = View.GONE
                    dialogBinding.nameLayout.visibility = View.GONE
                }
                AuraMarkType.BLESSING -> {
                    // Для благословения показываем обычные поля
                    dialogBinding.starsLayout.visibility = View.GONE
                    dialogBinding.imageUrlLayout.visibility = View.GONE
                    dialogBinding.nameLayout.visibility = View.GONE
                }
                AuraMarkType.JUDGE_STATUS,
                AuraMarkType.CONTRACT_BREACH,
                AuraMarkType.SPIRITUAL_BEING_INSIDE,
                AuraMarkType.MAGIC_CONTRACT,
                AuraMarkType.ARTIFACT_LINK -> {
                    // Для типов с фиксированными иконками скрываем поля ввода
                    dialogBinding.starsLayout.visibility = View.GONE
                    dialogBinding.imageUrlLayout.visibility = View.GONE
                    dialogBinding.nameLayout.visibility = View.GONE
                }
                else -> {
                    // Для остальных типов показываем поля картинки и названия
                    dialogBinding.starsLayout.visibility = View.GONE
                    dialogBinding.imageUrlLayout.visibility = View.VISIBLE
                    dialogBinding.nameLayout.visibility = View.VISIBLE
                }
            }
        }
        
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
            val description = dialogBinding.descriptionInput.text.toString()
            val external = dialogBinding.externalCheckBox.isChecked
            
            val imageUrl: String
            val name: String
            val numberOfStars: Int
            
            when (markType) {
                AuraMarkType.BLESSING -> {
                    name = "Благословение"
                    imageUrl = "http://shift96.ru/static/images/blessing.png"
                    numberOfStars = 0
                }
                AuraMarkType.MAGIC_DISCIPLINE,
                AuraMarkType.INSTRUMENT_LINK,
                AuraMarkType.FAMILIAR_LINK,
                AuraMarkType.ABILITY -> {
                    // Эти типы скрыты, но на всякий случай добавляем обработку
                    name = "Скрытый тип"
                    imageUrl = ""
                    numberOfStars = 0
                }
                AuraMarkType.CURSE -> {
                    val curseTypePosition = curseTypeNames.indexOf(dialogBinding.curseTypeSpinner.text.toString())
                    if (curseTypePosition == -1) {
                        Toast.makeText(this, "Выберите тип проклятия", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val curseType = curseTypes[curseTypePosition]
                    name = curseType.first
                    imageUrl = "http://shift96.ru/static/images/${curseType.second}.png"
                    numberOfStars = 0
                }
                AuraMarkType.JUDGE_STATUS -> {
                    name = "Статус судьи"
                    imageUrl = "http://shift96.ru/static/images/judge.png"
                    numberOfStars = 0
                }
                AuraMarkType.CONTRACT_BREACH -> {
                    name = "Нарушение контракта"
                    imageUrl = "http://shift96.ru/static/images/breached_contract.png"
                    numberOfStars = 0
                }
                AuraMarkType.SPIRITUAL_BEING_INSIDE -> {
                    name = "Духовное существо внутри"
                    imageUrl = "http://shift96.ru/static/images/spiritual_inside.png"
                    numberOfStars = 0
                }
                AuraMarkType.MARK_OF_CREATION -> {
                    val creationTypePosition = creationTypeNames.indexOf(dialogBinding.creationTypeSpinner.text.toString())
                    if (creationTypePosition == -1) {
                        Toast.makeText(this, "Выберите тип метки пробуждения", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val creationType = creationTypes[creationTypePosition]
                    name = "Метка пробуждения"
                    imageUrl = "http://shift96.ru/static/images/${creationType.second}.png"
                    numberOfStars = 0
                }
                AuraMarkType.MAGIC_CONTRACT -> {
                    name = "Магический контракт"
                    imageUrl = "http://shift96.ru/static/images/magic_contract.png"
                    numberOfStars = 0
                }
                AuraMarkType.MAGIC_LINK -> {
                    val magicLinkTypePosition = magicLinkTypeNames.indexOf(dialogBinding.magicLinkTypeSpinner.text.toString())
                    if (magicLinkTypePosition == -1) {
                        Toast.makeText(this, "Выберите тип магической связи", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val magicLinkType = magicLinkTypes[magicLinkTypePosition]
                    name = magicLinkType.first
                    imageUrl = "http://shift96.ru/static/images/${magicLinkType.second}.png"
                    numberOfStars = 0
                }
                AuraMarkType.ARTIFACT_LINK -> {
                    name = "Связь с артефактом"
                    imageUrl = "http://shift96.ru/static/images/artifact_bond.png"
                    numberOfStars = 0
                }
                AuraMarkType.FOREIGN_PLANE_INFLUENCE -> {
                    val planeTypePosition = planeTypeNames.indexOf(dialogBinding.planeTypeSpinner.text.toString())
                    if (planeTypePosition == -1) {
                        Toast.makeText(this, "Выберите тип влияния плана", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val planeType = planeTypes[planeTypePosition]
                    name = planeType.first
                    imageUrl = "http://shift96.ru/static/images/${planeType.second}.png"
                    numberOfStars = 0
                }
            }
            
            // Создаем запрос
            val markRequest = AuraMarkRequest(
                markType = markType,
                imageUrl = imageUrl,
                name = name,
                description = description.ifBlank { null },
                external = external,
                numberOfStars = numberOfStars
            )
            
            // Отправляем запрос и закрываем диалог
            addAuraMark(markRequest, dialog)
        }
        
        dialog.show()
    }
    
    private fun showEditMarkDialog(mark: AuraMark) {
        val dialogBinding = DialogEditAuraMarkBinding.inflate(LayoutInflater.from(this))
        
        // Проверяем, является ли тип метки скрытым
        val hiddenTypes = setOf(
            AuraMarkType.MAGIC_DISCIPLINE,
            AuraMarkType.INSTRUMENT_LINK,
            AuraMarkType.FAMILIAR_LINK,
            AuraMarkType.ABILITY
        )
        val isHiddenType = mark.markType in hiddenTypes
        
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
        
        // Устанавливаем текущие значения
        val currentTypeIndex = markTypes.indexOf(mark.markType)
        if (currentTypeIndex != -1) {
            dialogBinding.markTypeSpinner.setText(markTypeNames[currentTypeIndex], false)
        }
        dialogBinding.imageUrlInput.setText(mark.imageUrl)
        dialogBinding.nameInput.setText(mark.name)
        dialogBinding.descriptionInput.setText(mark.description ?: "")
        dialogBinding.externalCheckBox.isChecked = mark.external == 1
        dialogBinding.starsInput.setText(mark.numberOfStars?.toString() ?: "0")
        
        // Отображаем время истечения если есть
        LogHelper.d("AuraEditorActivity: Mark expireAt: ${mark.expireAt}")
        if (mark.expireAt != null && mark.expireAt.isNotBlank()) {
            val formattedExpireAt = DateTimeHelper.formatExpireAt(mark.expireAt)
            LogHelper.d("AuraEditorActivity: Formatted expireAt: $formattedExpireAt")
            if (formattedExpireAt != null) {
                dialogBinding.expireAtLayout.visibility = View.VISIBLE
                dialogBinding.expireAtText.text = formattedExpireAt
                LogHelper.d("AuraEditorActivity: ExpireAt layout made visible and text set")
            } else {
                LogHelper.d("AuraEditorActivity: Formatted expireAt is null")
            }
        } else {
            LogHelper.d("AuraEditorActivity: No expireAt or blank")
        }
        
        // Для скрытых типов делаем поля нередактируемыми и скрываем кнопки
        if (isHiddenType) {
            dialogBinding.markTypeSpinner.isEnabled = false
            dialogBinding.imageUrlInput.isEnabled = false
            dialogBinding.nameInput.isEnabled = false
            dialogBinding.descriptionInput.isEnabled = false
            dialogBinding.externalCheckBox.isEnabled = false
            dialogBinding.starsInput.isEnabled = false
            
            // Скрываем кнопки сохранения и удаления
            dialogBinding.saveButton.visibility = View.GONE
            dialogBinding.deleteButton.visibility = View.GONE
        } else {
            // Для обычных типов показываем кнопки
            dialogBinding.saveButton.visibility = View.VISIBLE
            dialogBinding.deleteButton.visibility = View.VISIBLE
        }
        
        // Показываем поле звёздочек только для магических дисциплин
        if (mark.markType == AuraMarkType.MAGIC_DISCIPLINE) {
            dialogBinding.starsLayout.visibility = View.VISIBLE
        } else {
            dialogBinding.starsLayout.visibility = View.GONE
        }
        
        // Создаем и показываем диалог
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()
        
        // Обработчик кнопки сохранения (только для не скрытых типов)
        if (!isHiddenType) {
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
                val numberOfStars = dialogBinding.starsInput.text.toString().toIntOrNull() ?: 0
                
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
                    external = external,
                    numberOfStars = numberOfStars
                )
                
                // Обновляем метку
                updateAuraMark(mark.markId, markRequest, dialog)
            }
            
            // Обработчик кнопки удаления
            dialogBinding.deleteButton.setOnClickListener {
                deleteAuraMark(mark.markId, dialog)
            }
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
    
    // Визуальное переключение (только для редактора, без запроса на сервер)
    private fun toggleAuraVisibilityVisual() {
        isAuraVisible = !isAuraVisible
        
        LogHelper.d("AuraEditorActivity: toggleAuraVisibilityVisual - isAuraVisible=$isAuraVisible")
        
        // Обновляем меню
        invalidateOptionsMenu()
        
        // НЕ обновляем кнопку - она показывает серверное состояние
        
        // Передаём состояние в фрагмент ауры
        // true = принудительно показать, false = принудительно скрыть
        auraFragment.setAuraVisibility(isAuraVisible)
        
        // Показываем уведомление
        val message = if (isAuraVisible) {
            "Аура показана (только для редактора)"
        } else {
            "Аура скрыта (только для редактора)"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    // Переключение с запросом на сервер
    private fun toggleAuraVisibilityOnServer() {
        if (selectedUser == null) return
        
        val newHiddenState = !serverAuraHidden
        val request = AuraHiddenRequest(if (newHiddenState) 1 else 0)
        
        LogHelper.d("AuraEditorActivity: toggleAuraVisibilityOnServer - newHiddenState=$newHiddenState")
        
        // Используем GlobalScope для вызова suspend функции
        GlobalScope.launch(Dispatchers.Main) {
            try {
                val response = RetrofitClient.auraApi.updateAuraHidden(selectedUser!!.userId, request)
                if (response.isSuccessful) {
                    // Обновляем серверное состояние
                    serverAuraHidden = newHiddenState
                    
                    // Обновляем кнопку после успешного запроса
                    updateAuraButton()
                    
                    // Показываем сообщение об успехе
                    val message = if (newHiddenState) {
                        getString(R.string.aura_hidden)
                    } else {
                        getString(R.string.aura_shown)
                    }
                    Toast.makeText(this@AuraEditorActivity, message, Toast.LENGTH_SHORT).show()
                } else {
                    LogHelper.e("AuraEditorActivity: Ошибка обновления ауры: ${response.code()}")
                    Toast.makeText(this@AuraEditorActivity, "Ошибка обновления ауры: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                LogHelper.e("AuraEditorActivity: Ошибка сети при обновлении ауры: ${e.localizedMessage}")
                Toast.makeText(this@AuraEditorActivity, "Ошибка сети: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateAuraButton() {
        // Кнопка показывает серверное состояние ауры
        if (serverAuraHidden) {
            binding.btnToggleAuraVisibility.text = getString(R.string.show_aura)
        } else {
            binding.btnToggleAuraVisibility.text = getString(R.string.hide_aura)
        }
    }
    
}
