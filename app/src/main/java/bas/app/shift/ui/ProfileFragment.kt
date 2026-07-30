package bas.app.shift.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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
        
        // Проверяем, что binding создан успешно
        if (_binding != null) {
            // Настраиваем кнопку показа ауры экстрасенсу
            binding.btnShowAuraToExtrasensory.setOnClickListener {
                if (isAdded && context != null) {
                    val intent = Intent(context, AuraQrActivity::class.java)
                    // Передаем ID пользователя, чью ауру показываем
                    intent.putExtra("user_id", currentUserId)
                    startActivity(intent)
                }
            }
            
            // Скрываем кнопки редактирования в режиме просмотра
            binding.btnEditInstrument.visibility = View.GONE
            binding.btnEditFamiliar.visibility = View.GONE
        }
        
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun showProfile(user: User) {
        // Проверяем, что binding не null и view еще не уничтожена
        if (_binding == null) {
            LogHelper.w("ProfileFragment: showProfile вызван, но binding еще не создан")
            return
        }
        if (!isAdded) {
            LogHelper.w("ProfileFragment: showProfile вызван, но фрагмент уже не добавлен")
            return
        }
        
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
                if (isAdded && context != null) {
                    val tv = TextView(context)
                    tv.text = effect.textToShowPlayers
                    tv.textSize = 16f
                    tv.setPadding(0, 8, 0, 8)
                    tv.setSingleLine(false) // Разрешаем многострочный текст
                    tv.maxLines = 15 // Увеличиваем лимит строк для полного отображения эффектов
                    tv.ellipsize = android.text.TextUtils.TruncateAt.END // Добавляем многоточие если текст обрезается
                    effectsLayout.addView(tv)
                }
            }
        } else {
            if (isAdded && context != null) {
                val tv = TextView(context)
                tv.text = getString(R.string.no_effects)
                tv.textSize = 16f
                tv.setPadding(0, 8, 0, 8)
                effectsLayout.addView(tv)
            }
        }
        
        // Показываем кнопку редактирования эффектов только для MG пользователей
        val currentUserId = if (isAdded && context != null) UserPrefsHelper.getUserId(requireContext()) else null
        val isMgUser = currentUserId?.startsWith("MG", ignoreCase = true) == true
        
        if (isMgUser) {
            binding.btnEditEffects.visibility = View.VISIBLE
            binding.btnEditEffects.setOnClickListener {
                if (isAdded && context != null) {
                    val intent = Intent(context, EffectEditorActivity::class.java)
                    intent.putExtra("userId", user.userId)
                    startActivityForResult(intent, REQUEST_CODE_EDIT_EFFECTS)
                }
            }
        } else {
            binding.btnEditEffects.visibility = View.GONE
        }
        
        // Имя игрока
        binding.profilePlayerName.text = user.playerName ?: "Имя игрока не указано"
        // Тип
        binding.profileType.text = getAuraTypeDisplayName(user.type)
        // Дисциплины (теперь List<NamedEntity>)
        renderTextList(binding.profileDisciplinesList, user.disciplines, "Нет дисциплин") { it.name }
        // Модули (теперь List<NamedEntity>)
        renderTextList(binding.profileModulesList, user.modules, "Нет модулей") { it.name }
        // Способности (теперь List<Ability>)
        renderTextList(binding.profileAbilitiesList, user.abilities, "Нет способностей") {
            "Тип: ${it.type}\nОписание: ${it.description}"
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
                    if (isAdded && context != null) {
                        val tv = TextView(context)
                        tv.text = artifact.name
                        tv.isClickable = true
                        tv.setOnClickListener {
                            if (isAdded && context != null) {
                                val intent = Intent(context, ArtifactActivity::class.java)
                                intent.putExtra("artifact_id", artifact.id)
                                startActivity(intent)
                            }
                        }
                        artifactsLayout.addView(tv)
                    }
                }
            } else {
                if (isAdded && context != null) {
                    val tv = TextView(context)
                    tv.text = "Нет артефактов"
                    artifactsLayout.addView(tv)
                }
            }
        } else {
            artifactsSection.visibility = View.GONE
        }
        // Инструмент
        binding.profileInstrument.text = user.instrument ?: "Инструмент не указан"
        // Фамильяр
        val familiarName = if (!user.familiar.isNullOrEmpty()) {
            FamiliarData.getNameById(user.familiar)
        } else {
            "Нет фамильяра"
        }
        binding.profileFamiliar.text = familiarName
        // Прочее
        renderTextList(binding.profileMiscList, user.misc, "Нет особенностей") { it }
    }

    private fun <T> renderTextList(layout: LinearLayout, items: List<T>, emptyText: String, itemText: (T) -> String) {
        layout.removeAllViews()
        if (items.isNotEmpty()) {
            items.forEach { item ->
                if (isAdded && context != null) {
                    val tv = TextView(context)
                    tv.text = itemText(item)
                    layout.addView(tv)
                }
            }
        } else {
            if (isAdded && context != null) {
                val tv = TextView(context)
                tv.text = emptyText
                layout.addView(tv)
            }
        }
    }

    fun showError(msg: String) {
        LogHelper.e("ProfileFragment $msg")
        if (isAdded && context != null) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
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
            if (isAdded && context != null) {
                Toast.makeText(context, "Эффекты обновлены", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
