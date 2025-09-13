package bas.app.shift.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import bas.app.shift.R
import bas.app.shift.api.RetrofitClient
import bas.app.shift.databinding.ActivityEffectEditorBinding
import bas.app.shift.databinding.DialogAddEffectBinding
import bas.app.shift.helpers.DateTimeHelper
import bas.app.shift.helpers.LogHelper
import bas.app.shift.helpers.TimePickerHelper
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.models.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class EffectEditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEffectEditorBinding
    private var effects: List<Effect> = emptyList()
    private var userId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEffectEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getStringExtra("userId") ?: ""
        if (userId.isEmpty()) {
            finish()
            return
        }



        setupToolbar()
        setupUI()
        loadEffects()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Редактор эффектов"
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupUI() {
        binding.toolbar.title = "Редактор эффектов"
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.addEffectButton.setOnClickListener {
            showAddEffectDialog()
        }
    }

    private fun loadEffects() {
        RetrofitClient.userProfileApi.getUserProfile(userId)
            .enqueue(object : Callback<User> {
                override fun onResponse(call: Call<User>, response: Response<User>) {
                    if (response.isSuccessful && response.body() != null) {
                        val userServer = response.body()!!
                        if (userServer != null) {
                            effects = userServer.effects
                            displayEffects()
                        }
                    } else {
                        LogHelper.e("EffectEditorActivity: Error loading effects, empty")
                        Toast.makeText(this@EffectEditorActivity, "Ошибка загрузки эффектов", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<User>, t: Throwable) {
                    LogHelper.e("EffectEditorActivity: Error loading effects: $t")
                    Toast.makeText(this@EffectEditorActivity, "Ошибка загрузки эффектов", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun displayEffects() {
        binding.effectsList.removeAllViews()

        if (effects.isEmpty()) {
            val noEffectsText = TextView(this)
            noEffectsText.text = "Нет эффектов"
            noEffectsText.textSize = 16f
            noEffectsText.setPadding(0, 16, 0, 16)
            binding.effectsList.addView(noEffectsText)
            return
        }

        effects.forEach { effect ->
            val effectView = createEffectView(effect)
            binding.effectsList.addView(effectView)
        }
    }

    private fun createEffectView(effect: Effect): View {
        val effectView = LayoutInflater.from(this).inflate(R.layout.item_effect, null)
        
        val textView = effectView.findViewById<TextView>(R.id.effectText)
        val expireAtView = effectView.findViewById<TextView>(R.id.effectExpireAt)
        val deleteButton = effectView.findViewById<Button>(R.id.deleteButton)

        textView.text = effect.textToShowPlayers

        if (effect.expireAt != null && effect.expireAt.isNotBlank()) {
            val formattedExpireAt = DateTimeHelper.formatExpireAt(effect.expireAt)
            expireAtView.text = "Истекает: $formattedExpireAt"
            expireAtView.visibility = View.VISIBLE
        } else {
            expireAtView.visibility = View.GONE
        }

        deleteButton.setOnClickListener {
            showDeleteConfirmation(effect)
        }

        return effectView
    }

    private fun showDeleteConfirmation(effect: Effect) {
        AlertDialog.Builder(this)
            .setTitle("Удалить эффект")
            .setMessage("Вы уверены, что хотите удалить этот эффект?")
            .setPositiveButton("Удалить") { _, _ ->
                deleteEffect(effect.id)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteEffect(effectId: Int) {
        lifecycleScope.launch {
            try {
                val response = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    RetrofitClient.effectApi.deleteEffect(userId, effectId)
                }
                
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@EffectEditorActivity, "Эффект удален", Toast.LENGTH_SHORT).show()
                        loadEffects() // Перезагружаем список
                        setResult(android.app.Activity.RESULT_OK)
                    } else {
                        val errorMsg = "HTTP ${response.code()}"
                        val errorBody = response.errorBody()?.string()
                        val fullErrorMsg = if (errorBody != null) {
                            "$errorMsg: $errorBody"
                        } else {
                            errorMsg
                        }
                        Toast.makeText(this@EffectEditorActivity, "Ошибка удаления эффекта: $fullErrorMsg", Toast.LENGTH_LONG).show()
                        LogHelper.e("EffectEditorActivity: Error deleting effect: $fullErrorMsg")
                    }
                }
            } catch (e: Exception) {
                LogHelper.e("EffectEditorActivity: Error deleting effect: $e")
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val errorMsg = e.localizedMessage ?: "Неизвестная ошибка"
                    Toast.makeText(this@EffectEditorActivity, "Ошибка удаления эффекта: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showAddEffectDialog() {
        val dialogBinding = DialogAddEffectBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        // Обработчик клика на поле времени
        dialogBinding.timeToLiveInput.setOnClickListener {
            val currentTime = dialogBinding.timeToLiveInput.text.toString()
            TimePickerHelper.showTimePicker(this, currentTime) { hours, minutes ->
                val timeString = TimePickerHelper.formatTime(hours, minutes)
                dialogBinding.timeToLiveInput.setText(timeString)
            }
        }

        // Обработчик клика на тип метки
        dialogBinding.markTypeInput.setOnClickListener {
            showMarkTypeSelector(dialogBinding)
        }

        // Обработчик изменения типа метки
        dialogBinding.markTypeInput.setOnClickListener {
            showMarkTypeSelector(dialogBinding)
        }

        // Инициализируем поле типа метки значением по умолчанию
        dialogBinding.markTypeInput.setText("Без метки")
        dialogBinding.markFieldsLayout.visibility = View.GONE

        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.saveButton.setOnClickListener {
            saveEffect(dialogBinding, dialog)
        }

        dialog.show()
    }

    private fun showMarkTypeSelector(dialogBinding: DialogAddEffectBinding) {
        val markTypes = listOf(
            "Благословение" to AuraMarkType.BLESSING,
            "Проклятие" to AuraMarkType.CURSE,
            "Смертельное проклятие" to AuraMarkType.DEATH_CURSE,
            "Без метки" to null
        )

        val typeNames = markTypes.map { it.first }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Выберите тип метки")
            .setItems(typeNames) { _, which ->
                val selectedType = markTypes[which]
                dialogBinding.markTypeInput.setText(selectedType.first)
                
                if (selectedType.second != null) {
                    dialogBinding.markFieldsLayout.visibility = View.VISIBLE
                    // Устанавливаем значения по умолчанию для метки
                    when (selectedType.second) {
                        AuraMarkType.BLESSING -> {
                            dialogBinding.markNameInput.setText("Благословение")
                            dialogBinding.markDescriptionInput.setText("")
                        }
                        AuraMarkType.CURSE -> {
                            dialogBinding.markNameInput.setText("Проклятие")
                            dialogBinding.markDescriptionInput.setText("")
                        }
                        AuraMarkType.DEATH_CURSE -> {
                            dialogBinding.markNameInput.setText("Смертельное проклятие")
                            dialogBinding.markDescriptionInput.setText("")
                        }
                        else -> {}
                    }
                } else {
                    dialogBinding.markFieldsLayout.visibility = View.GONE
                }
            }
            .show()
    }

    private fun saveEffect(dialogBinding: DialogAddEffectBinding, dialog: AlertDialog) {
        val textToShowPlayers = dialogBinding.textToShowPlayersInput.text.toString()
        if (textToShowPlayers.isBlank()) {
            Toast.makeText(this, "Введите игротехнику", Toast.LENGTH_SHORT).show()
            return
        }

        val timeToLiveText = dialogBinding.timeToLiveInput.text.toString()
        val timeToLiveInMinutes = TimePickerHelper.parseTimeToMinutes(timeToLiveText)

        // Создаем метку если нужно
        val markRequest = if (dialogBinding.markFieldsLayout.visibility == View.VISIBLE) {
            val markTypeText = dialogBinding.markTypeInput.text.toString()
            val markType = when (markTypeText) {
                "Благословение" -> AuraMarkType.BLESSING
                "Проклятие" -> AuraMarkType.CURSE
                "Смертельное проклятие" -> AuraMarkType.DEATH_CURSE
                else -> null
            }

            if (markType != null) {
                AuraMarkRequest(
                    markType = markType,
                    imageUrl = "http://shift96.ru/static/images/${markType.name.lowercase()}.png",
                    name = dialogBinding.markNameInput.text.toString(),
                    description = dialogBinding.markDescriptionInput.text.toString(),
                    external = dialogBinding.markExternalCheckBox.isChecked,
                    numberOfStars = 0
                )
            } else null
        } else null

        val effectRequest = EffectRequest(
            textToShowPlayers = textToShowPlayers,
            timeToLiveInMinutes = timeToLiveInMinutes,
            mark = markRequest
        )

        lifecycleScope.launch {
            try {
                val response = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    RetrofitClient.effectApi.createEffect(userId, effectRequest)
                }
                
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        Toast.makeText(this@EffectEditorActivity, "Эффект создан", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        loadEffects() // Перезагружаем список
                        setResult(android.app.Activity.RESULT_OK)
                    } else {
                        val errorMsg = "HTTP ${response.code()}"
                        val errorBody = response.errorBody()?.string()
                        val fullErrorMsg = if (errorBody != null) {
                            "$errorMsg: $errorBody"
                        } else {
                            errorMsg
                        }
                        Toast.makeText(this@EffectEditorActivity, "Ошибка создания эффекта: $fullErrorMsg", Toast.LENGTH_LONG).show()
                        LogHelper.e("EffectEditorActivity: Error creating effect: $fullErrorMsg")
                    }
                }
            } catch (e: Exception) {
                LogHelper.e("EffectEditorActivity: Error creating effect: $e")
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val errorMsg = e.localizedMessage ?: "Неизвестная ошибка"
                    Toast.makeText(this@EffectEditorActivity, "Ошибка создания эффекта: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
