package bas.app.shift.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import bas.app.shift.R
import bas.app.shift.databinding.FragmentProfileBinding
import bas.app.shift.helpers.LogHelper
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.models.FamiliarData
import bas.app.shift.models.User
import bas.app.shift.models.AuraType

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private var currentUserId: String? = null
    
    companion object {
        private const val REQUEST_CODE_EDIT_EFFECTS = 1001
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        
        // Настраиваем кнопку показа ауры экстрасенсу
        binding.btnShowAuraToExtrasensory.setOnClickListener {
            val intent = Intent(requireContext(), AuraQrActivity::class.java)
            // Передаем ID пользователя, чью ауру показываем
            intent.putExtra("user_id", currentUserId)
            startActivity(intent)
        }
        
        // Скрываем кнопки редактирования в режиме просмотра
        binding.btnEditInstrument.visibility = View.GONE
        binding.btnEditFamiliar.visibility = View.GONE
        
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun showProfile(user: User) {
        // Сохраняем ID текущего пользователя
        currentUserId = user.userId
        
        // Имя персонажа
        binding.profileCharacterName.text = user.characterName ?: "Имя персонажа не указано"
        
        // Эффекты
        val effectsLayout = binding.profileEffectsList
        effectsLayout.removeAllViews()
        if (user.effects?.isNotEmpty() == true) {
            // Сортируем эффекты по ID в убывающем порядке (новые сверху)
            val sortedEffects = user.effects!!.sortedByDescending { it.id }
            sortedEffects.forEach { effect ->
                val tv = TextView(requireContext())
                tv.text = effect.textToShowPlayers
                tv.textSize = 16f
                tv.setPadding(0, 8, 0, 8)
                tv.setSingleLine(false) // Разрешаем многострочный текст
                tv.maxLines = 15 // Увеличиваем лимит строк для полного отображения эффектов
                tv.ellipsize = android.text.TextUtils.TruncateAt.END // Добавляем многоточие если текст обрезается
                effectsLayout.addView(tv)
            }
        } else {
            val tv = TextView(requireContext())
            tv.text = getString(R.string.no_effects)
            tv.textSize = 16f
            tv.setPadding(0, 8, 0, 8)
            effectsLayout.addView(tv)
        }
        
        // Показываем кнопку редактирования эффектов только для MG пользователей
        val currentUserId = UserPrefsHelper.getUserId(requireContext())
        val isMgUser = currentUserId?.startsWith("MG", ignoreCase = true) == true
        
        if (isMgUser) {
            binding.btnEditEffects.visibility = View.VISIBLE
            binding.btnEditEffects.setOnClickListener {
                val intent = Intent(requireContext(), EffectEditorActivity::class.java)
                intent.putExtra("userId", user.userId)
                startActivityForResult(intent, REQUEST_CODE_EDIT_EFFECTS)
            }
        } else {
            binding.btnEditEffects.visibility = View.GONE
        }
        
        // Имя игрока
        binding.profilePlayerName.text = user.playerName ?: "Имя игрока не указано"
        // Тип
        binding.profileType.text = getAuraTypeDisplayName(user.type)
        // Дисциплины (теперь List<NamedEntity>)
        val disciplinesLayout = binding.profileDisciplinesList
        disciplinesLayout.removeAllViews()
        if (user.disciplines.isNotEmpty()) {
            user.disciplines.forEach { discipline ->
                val tv = TextView(requireContext())
                tv.text = discipline.name
                disciplinesLayout.addView(tv)
            }
        } else {
            val tv = TextView(requireContext())
            tv.text = "Нет дисциплин"
            disciplinesLayout.addView(tv)
        }
        // Модули (теперь List<NamedEntity>)
        val modulesLayout = binding.profileModulesList
        modulesLayout.removeAllViews()
        if (user.modules.isNotEmpty()) {
            user.modules.forEach { module ->
                val tv = TextView(requireContext())
                tv.text = module.name
                modulesLayout.addView(tv)
            }
        } else {
            val tv = TextView(requireContext())
            tv.text = "Нет модулей"
            modulesLayout.addView(tv)
        }
        // Способности (теперь List<Ability>)
        val abilitiesLayout = binding.profileAbilitiesList
        abilitiesLayout.removeAllViews()
        if (user.abilities.isNotEmpty()) {
            user.abilities.forEach { ability ->
                val tv = TextView(requireContext())
                tv.text = "Тип: ${ability.type}\nОписание: ${ability.description}"
                abilitiesLayout.addView(tv)
            }
        } else {
            val tv = TextView(requireContext())
            tv.text = "Нет способностей"
            abilitiesLayout.addView(tv)
        }
        // Артефакты (только если есть Артефактология)
        val hasArtifactology = user.disciplines.any { it.id == 1 } // ID дисциплины Артефактология
        val artifactsSection = binding.profileArtifactsSection
        if (hasArtifactology) {
            artifactsSection.visibility = View.VISIBLE
            val artifactsLayout = binding.profileArtifactsList
            artifactsLayout.removeAllViews()
            if (user.artifacts.isNotEmpty()) {
                user.artifacts.forEach { artifact ->
                    val tv = TextView(requireContext())
                    tv.text = artifact.name
                    tv.isClickable = true
                    tv.setOnClickListener {
                        val intent = Intent(requireContext(), ArtifactActivity::class.java)
                        intent.putExtra("artifact_id", artifact.id)
                        startActivity(intent)
                    }
                    artifactsLayout.addView(tv)
                }
            } else {
                val tv = TextView(requireContext())
                tv.text = "Нет артефактов"
                artifactsLayout.addView(tv)
            }
        } else {
            artifactsSection.visibility = View.GONE
        }
        // Инструмент
        binding.profileInstrument.text = user.instrument ?: "Инструмент не указан"
        // Фамильяр
        val familiarName = if (user.familiar != null) {
            FamiliarData.getNameById(user.familiar!!)
        } else {
            "Фамильяр не указан"
        }
        binding.profileFamiliar.text = familiarName
        // Прочее
        val miscLayout = binding.profileMiscList
        miscLayout.removeAllViews()
        if (user.misc.isNotEmpty()) {
            user.misc.forEach {
                val tv = TextView(requireContext())
                tv.text = it
                miscLayout.addView(tv)
            }
        } else {
            val tv = TextView(requireContext())
            tv.text = "Нет особенностей"
            miscLayout.addView(tv)
        }
    }

    fun showError(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
        LogHelper.e("ProfileFragment $msg")
    }
    
    private fun getAuraTypeDisplayName(type: AuraType): String {
        return when (type) {
            AuraType.HUMAN -> getString(R.string.aura_type_human)
            AuraType.MAGE -> getString(R.string.aura_type_mage)
            AuraType.CREATURE_OF_SPIRIT_WORLD -> getString(R.string.aura_type_creature_of_spirit_world)
            AuraType.CREATURE_OF_ABYSS -> getString(R.string.aura_type_creature_of_abyss)
            AuraType.CREATURE_OF_MYTH -> getString(R.string.aura_type_creature_of_myth)
            AuraType.CREATURE_OF_REALITY -> getString(R.string.aura_type_creature_of_reality)
            AuraType.DEMON -> getString(R.string.aura_type_demon)
            AuraType.ANGEL -> getString(R.string.aura_type_angel)
            AuraType.OTHER -> getString(R.string.aura_type_other)
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_EDIT_EFFECTS && resultCode == android.app.Activity.RESULT_OK) {
            // Обновляем профиль после редактирования эффектов
            // Здесь можно добавить логику для перезагрузки данных пользователя
            // Пока что просто показываем сообщение
            Toast.makeText(requireContext(), "Эффекты обновлены", Toast.LENGTH_SHORT).show()
        }
    }
}
