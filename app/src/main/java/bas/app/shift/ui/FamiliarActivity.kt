package bas.app.shift.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import bas.app.shift.R
import bas.app.shift.databinding.ActivityFamiliarBinding
import bas.app.shift.helpers.LogHelper
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.models.FamiliarData
import bas.app.shift.models.User
import java.util.Calendar

class FamiliarActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFamiliarBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFamiliarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupFamiliarImage()
        setupButtons()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.familiar_button)
    }

    private fun setupFamiliarImage() {
        // Получаем ID фамильяра из профиля пользователя
        val user = UserPrefsHelper.getUserData(this)
        val familiarId = user?.familiar ?: "familiar_malachite_lizard"
        
        // Показываем название фамильяра
        val familiarName = FamiliarData.getNameById(familiarId)
        binding.tvFamiliarName.text = familiarName
        
        // Определяем текущий час для выбора изображения
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val imageIndex = (currentHour % 3) + 1
        val isNight = FamiliarData.isNightTime(currentHour)
        
        val imageName = FamiliarData.getImageNameByIdWithTime(familiarId, imageIndex, isNight)
        LogHelper.d("FamiliarActivity: Текущий час: $currentHour, индекс изображения: $imageIndex, ночное время: $isNight, имя: $imageName")
        
        // Получаем ресурс изображения
        val imageResId = resources.getIdentifier(
            imageName, 
            "drawable", 
            packageName
        )
        
        if (imageResId != 0) {
            binding.familiarImage.setImageResource(imageResId)
            LogHelper.d("FamiliarActivity: Изображение фамильяра установлено: $imageName")
        } else {
            LogHelper.e("FamiliarActivity: Не удалось найти изображение: $imageName")
            // Показываем изображение по умолчанию
            val defaultImageName = FamiliarData.getImageNameByIdWithTime(familiarId, 1, isNight)
            val fallbackImageResId = resources.getIdentifier(defaultImageName, "drawable", packageName)
            
            if (fallbackImageResId != 0) {
                binding.familiarImage.setImageResource(fallbackImageResId)
                LogHelper.d("FamiliarActivity: Установлено fallback изображение: $defaultImageName")
            } else {
                // Если и fallback не найден, используем базовое изображение без _night
                val baseImageName = FamiliarData.getImageNameById(familiarId, 1)
                binding.familiarImage.setImageResource(
                    resources.getIdentifier(baseImageName, "drawable", packageName)
                )
                LogHelper.d("FamiliarActivity: Установлено базовое изображение: $baseImageName")
            }
        }
    }

    private fun setupButtons() {
        binding.btnTalk.setOnClickListener {
            // Пока что просто показываем сообщение
            binding.tvStatus.text = getString(R.string.familiar_listening)
            binding.btnTalk.isEnabled = false
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
