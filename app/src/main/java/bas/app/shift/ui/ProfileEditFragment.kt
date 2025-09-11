package bas.app.shift.ui

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import bas.app.shift.databinding.FragmentProfileEditBinding
import bas.app.shift.helpers.LogHelper
import bas.app.shift.models.User
import bas.app.shift.models.NamedEntity
import bas.app.shift.models.Ability
import bas.app.shift.models.FamiliarData
import bas.app.shift.models.UserUpdateRequest

class ProfileEditFragment : Fragment() {
    private var _binding: FragmentProfileEditBinding? = null
    private val binding get() = _binding!!
    private var currentUserDisplay: User? = null
    private var allAbilities: List<Ability> = emptyList()
    private var allModules: List<NamedEntity> = emptyList()
    private var allDisciplines: List<NamedEntity> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileEditBinding.inflate(inflater, container, false)
        
        setupUI()
        
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupUI() {
        // Настройка кнопки сохранения
        binding.btnSaveProfile.setOnClickListener {
            saveProfile()
        }

        // Настройка кнопок для способностей
        binding.btnAddAbility.setOnClickListener {
            if (allAbilities.isEmpty()) {
                Toast.makeText(requireContext(), "Загрузка способностей...", Toast.LENGTH_SHORT).show()
                val activity = requireActivity() as ProfileEditActivity
                activity.loadAbilities()
            } else {
                showAddAbilityDialog()
            }
        }

        // Настройка кнопок для модулей
        binding.btnAddModule.setOnClickListener {
            showAddModuleDialog()
        }

        // Настройка кнопки для дисциплин
        binding.btnAddDiscipline.setOnClickListener {
            showAddDisciplineDialog()
        }

        // Настройка кнопки для инструмента
        binding.btnEditInstrument.setOnClickListener {
            showEditInstrumentDialog()
        }

        // Настройка кнопки для фамильяра
        binding.btnEditFamiliar.setOnClickListener {
            showEditFamiliarDialog()
        }

        // Настройка кнопок для особенностей
        binding.btnAddMisc.setOnClickListener {
            showAddMiscDialog()
        }
    }

    fun setModules(modules: List<NamedEntity>) {
        allModules = modules
    }

    fun setDisciplines(disciplines: List<NamedEntity>) {
        allDisciplines = disciplines
    }

    fun showProfile(user: User) {
        currentUserDisplay = user
        
        // Имя персонажа (только для чтения)
        binding.profileCharacterName.text = user.characterName ?: "Имя персонажа не указано"
        
        // Имя игрока (только для чтения)
        binding.profilePlayerName.text = user.playerName ?: "Имя игрока не указано"
        
        // Дисциплины (только для чтения) - теперь это List<NamedEntity>
        val disciplinesLayout = binding.profileDisciplinesList
        disciplinesLayout.removeAllViews()
        if (user.disciplines.isNotEmpty()) {
            user.disciplines.forEach { discipline ->
                val tv = TextView(requireContext())
                tv.text = discipline.name
                tv.textSize = 16f
                tv.setPadding(0, 8, 0, 8)
                disciplinesLayout.addView(tv)
            }
        } else {
            val tv = TextView(requireContext())
            tv.text = "Нет дисциплин"
            tv.textSize = 16f
            tv.setPadding(0, 8, 0, 8)
            disciplinesLayout.addView(tv)
        }

        // Дисциплины (редактируемые)
        updateDisciplinesDisplay()

        // Модули (редактируемые)
        updateModulesDisplay()

        // Способности (редактируемые)
        updateAbilitiesDisplay()

        // Инструмент (редактируемый)
        binding.profileInstrument.text = user.instrument ?: "Инструмент не указан"

        // Фамильяр (редактируемый)
        val familiarName = if (user.familiar != null) {
            FamiliarData.getNameById(user.familiar!!)
        } else {
            "Фамильяр не указан"
        }
        binding.profileFamiliar.text = familiarName

        // Особенности (редактируемые)
        updateMiscDisplay()
    }

    fun setAbilities(abilities: List<Ability>) {
        allAbilities = abilities
    }

    private fun updateAbilitiesDisplay() {
        val abilitiesLayout = binding.profileAbilitiesList
        abilitiesLayout.removeAllViews()
        
        if (currentUserDisplay?.abilities?.isNotEmpty() == true) {
            currentUserDisplay!!.abilities.forEachIndexed { index, ability ->
                // Создаем основной контейнер для всей способности
                val mainContainer = LinearLayout(requireContext())
                mainContainer.orientation = LinearLayout.VERTICAL
                mainContainer.setPadding(0, 8, 0, 8)

                // Контейнер для текста и кнопки
                val textButtonContainer = LinearLayout(requireContext())
                textButtonContainer.orientation = LinearLayout.HORIZONTAL

                val tv = TextView(requireContext())
                tv.text = "Тип: ${ability.type}\nОписание: ${ability.description}"
                tv.textSize = 16f
                tv.setTextColor(android.graphics.Color.BLACK)
                tv.setSingleLine(false)
                tv.maxLines = 10 // Достаточно большое количество строк
                tv.ellipsize = null // Отключить многоточие
                tv.setPadding(0, 4, 8, 4) // Добавляем отступы
                tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                textButtonContainer.addView(tv)

                val btnRemove = Button(requireContext())
                btnRemove.text = "Удалить"
                btnRemove.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, 
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                btnRemove.setOnClickListener {
                    removeAbility(index)
                }
                textButtonContainer.addView(btnRemove)

                mainContainer.addView(textButtonContainer)
                abilitiesLayout.addView(mainContainer)
            }
        } else {
            val tv = TextView(requireContext())
            tv.text = "Нет способностей"
            tv.textSize = 16f
            tv.setPadding(0, 8, 0, 8)
            abilitiesLayout.addView(tv)
        }
    }

    private fun updateModulesDisplay() {
        val modulesLayout = binding.profileModulesList
        modulesLayout.removeAllViews()
        
        if (currentUserDisplay?.modules?.isNotEmpty() == true) {
            currentUserDisplay!!.modules.forEachIndexed { index, module ->
                val container = LinearLayout(requireContext())
                container.orientation = LinearLayout.HORIZONTAL
                container.setPadding(0, 8, 0, 8)

                val tv = TextView(requireContext())
                tv.text = module.name
                tv.textSize = 16f
                tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                container.addView(tv)

                val btnRemove = Button(requireContext())
                btnRemove.text = "Удалить"
                btnRemove.setOnClickListener {
                    removeModule(index)
                }
                container.addView(btnRemove)

                modulesLayout.addView(container)
            }
        } else {
            val tv = TextView(requireContext())
            tv.text = "Нет модулей"
            tv.textSize = 16f
            tv.setPadding(0, 8, 0, 8)
            modulesLayout.addView(tv)
        }
    }

    private fun updateDisciplinesDisplay() {
        val disciplinesLayout = binding.profileDisciplinesList
        disciplinesLayout.removeAllViews()
        
        if (currentUserDisplay?.disciplines?.isNotEmpty() == true) {
            currentUserDisplay!!.disciplines.forEachIndexed { index, discipline ->
                val container = LinearLayout(requireContext())
                container.orientation = LinearLayout.HORIZONTAL
                container.setPadding(0, 8, 0, 8)

                val tv = TextView(requireContext())
                tv.text = discipline.name
                tv.textSize = 16f
                tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                container.addView(tv)

                val btnRemove = Button(requireContext())
                btnRemove.text = "Удалить"
                btnRemove.setOnClickListener {
                    removeDiscipline(index)
                }
                container.addView(btnRemove)

                disciplinesLayout.addView(container)
            }
        } else {
            val tv = TextView(requireContext())
            tv.text = "Нет дисциплин"
            tv.textSize = 16f
            tv.setPadding(0, 8, 0, 8)
            disciplinesLayout.addView(tv)
        }
    }

    private fun updateMiscDisplay() {
        val miscLayout = binding.profileMiscList
        miscLayout.removeAllViews()
        
        if (currentUserDisplay?.misc?.isNotEmpty() == true) {
            currentUserDisplay!!.misc.forEachIndexed { index, misc ->
                val container = LinearLayout(requireContext())
                container.orientation = LinearLayout.HORIZONTAL
                container.setPadding(0, 8, 0, 8)

                val tv = TextView(requireContext())
                tv.text = misc
                tv.textSize = 16f
                tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                container.addView(tv)

                val btnRemove = Button(requireContext())
                btnRemove.text = "Удалить"
                btnRemove.setOnClickListener {
                    removeMisc(index)
                }
                container.addView(btnRemove)

                miscLayout.addView(container)
            }
        } else {
            val tv = TextView(requireContext())
            tv.text = "Нет особенностей"
            tv.textSize = 16f
            tv.setPadding(0, 8, 0, 8)
            miscLayout.addView(tv)
        }
    }

    private fun showAddAbilityDialog() {
        if (allAbilities.isEmpty()) {
            Toast.makeText(requireContext(), "Загрузка списка способностей...", Toast.LENGTH_SHORT).show()
            // Попробуем загрузить способности снова
            val activity = requireActivity() as ProfileEditActivity
            activity.loadAbilities()
            return
        }

        val abilityNames = allAbilities.map { "${it.type}: ${it.description}" }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, abilityNames)
        
        AlertDialog.Builder(requireContext())
            .setTitle("Добавить способность")
            .setAdapter(adapter) { _, which ->
                val selectedAbility = allAbilities[which]
                addAbility(selectedAbility.id)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showAddModuleDialog() {
        val moduleNames = allModules.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, moduleNames)
        
        AlertDialog.Builder(requireContext())
            .setTitle("Добавить модуль")
            .setAdapter(adapter) { _, which ->
                val selectedModule = allModules[which]
                addModule(selectedModule.id)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showAddDisciplineDialog() {
        val disciplineNames = allDisciplines.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, disciplineNames)
        
        AlertDialog.Builder(requireContext())
            .setTitle("Добавить дисциплину")
            .setAdapter(adapter) { _, which ->
                val selectedDiscipline = allDisciplines[which]
                addDiscipline(selectedDiscipline.id)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showEditInstrumentDialog() {
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.setText(currentUserDisplay?.instrument ?: "")
        
        AlertDialog.Builder(requireContext())
            .setTitle("Редактировать инструмент")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val newInstrument = input.text.toString().trim()
                updateInstrument(newInstrument)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showEditFamiliarDialog() {
        val familiarNames = FamiliarData.familiars.values.toList()
        val familiarIds = FamiliarData.familiars.keys.toList()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, familiarNames)
        
        AlertDialog.Builder(requireContext())
            .setTitle("Выбрать фамильяра")
            .setAdapter(adapter) { _, which ->
                val selectedFamiliarId = familiarIds[which]
                updateFamiliar(selectedFamiliarId)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showAddMiscDialog() {
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.hint = "Введите особенность"
        
        AlertDialog.Builder(requireContext())
            .setTitle("Добавить особенность")
            .setView(input)
            .setPositiveButton("Добавить") { _, _ ->
                val newMisc = input.text.toString().trim()
                if (newMisc.isNotEmpty()) {
                    addMisc(newMisc)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun addAbility(abilityId: Int) {
        if (currentUserDisplay == null) return
        
        val ability = allAbilities.find { it.id == abilityId }
        if (ability != null) {
            val newAbilities = currentUserDisplay!!.abilities.toMutableList()
            if (!newAbilities.any { it.id == abilityId }) {
                newAbilities.add(ability)
                currentUserDisplay = currentUserDisplay!!.copy(abilities = newAbilities)
                updateAbilitiesDisplay()
            }
        }
    }

    private fun removeAbility(index: Int) {
        if (currentUserDisplay == null) return
        
        val newAbilities = currentUserDisplay!!.abilities.toMutableList()
        if (index in newAbilities.indices) {
            newAbilities.removeAt(index)
            currentUserDisplay = currentUserDisplay!!.copy(abilities = newAbilities)
            updateAbilitiesDisplay()
        }
    }

    private fun addModule(moduleId: Int) {
        if (currentUserDisplay == null) return
        
        val module = allModules.find { it.id == moduleId }
        if (module != null) {
            val newModules = currentUserDisplay!!.modules.toMutableList()
            if (!newModules.any { it.id == moduleId }) {
                newModules.add(module)
                currentUserDisplay = currentUserDisplay!!.copy(modules = newModules)
                updateModulesDisplay()
            }
        }
    }

    private fun removeModule(index: Int) {
        if (currentUserDisplay == null) return
        
        val newModules = currentUserDisplay!!.modules.toMutableList()
        if (index in newModules.indices) {
            newModules.removeAt(index)
            currentUserDisplay = currentUserDisplay!!.copy(modules = newModules)
            updateModulesDisplay()
        }
    }

    private fun addDiscipline(disciplineId: Int) {
        if (currentUserDisplay == null) return
        
        val discipline = allDisciplines.find { it.id == disciplineId }
        if (discipline != null) {
            val newDisciplines = currentUserDisplay!!.disciplines.toMutableList()
            if (!newDisciplines.any { it.id == disciplineId }) {
                newDisciplines.add(discipline)
                currentUserDisplay = currentUserDisplay!!.copy(disciplines = newDisciplines)
                updateDisciplinesDisplay()
            }
        }
    }

    private fun removeDiscipline(index: Int) {
        if (currentUserDisplay == null) return
        
        val newDisciplines = currentUserDisplay!!.disciplines.toMutableList()
        if (index in newDisciplines.indices) {
            newDisciplines.removeAt(index)
            currentUserDisplay = currentUserDisplay!!.copy(disciplines = newDisciplines)
            updateDisciplinesDisplay()
        }
    }

    private fun updateInstrument(instrument: String) {
        if (currentUserDisplay == null) return
        
        currentUserDisplay = currentUserDisplay!!.copy(instrument = instrument)
        binding.profileInstrument.text = instrument.ifEmpty { "Инструмент не указан" }
    }

    private fun updateFamiliar(familiarId: String) {
        if (currentUserDisplay == null) return
        
        currentUserDisplay = currentUserDisplay!!.copy(familiar = familiarId)
        val familiarName = FamiliarData.getNameById(familiarId)
        binding.profileFamiliar.text = familiarName
    }

    private fun addMisc(misc: String) {
        if (currentUserDisplay == null) return
        
        val newMisc = currentUserDisplay!!.misc.toMutableList()
        newMisc.add(misc)
        currentUserDisplay = currentUserDisplay!!.copy(misc = newMisc)
        updateMiscDisplay()
    }

    private fun removeMisc(index: Int) {
        if (currentUserDisplay == null) return
        
        val newMisc = currentUserDisplay!!.misc.toMutableList()
        if (index in newMisc.indices) {
            newMisc.removeAt(index)
            currentUserDisplay = currentUserDisplay!!.copy(misc = newMisc)
            updateMiscDisplay()
        }
    }

    private fun saveProfile() {
        if (currentUserDisplay == null) {
            Toast.makeText(requireContext(), "Ошибка: профиль не загружен", Toast.LENGTH_SHORT).show()
            return
        }

        val updateRequest = UserUpdateRequest(
            disciplines = currentUserDisplay!!.disciplines.map { it.id },
            modules = currentUserDisplay!!.modules.map { it.id },
            abilities = currentUserDisplay!!.abilities.map { it.id },
            instrument = currentUserDisplay!!.instrument,
            familiar = currentUserDisplay!!.familiar,
            misc = currentUserDisplay!!.misc
        )

        val activity = requireActivity() as ProfileEditActivity
        activity.saveProfile(updateRequest)
    }
}
