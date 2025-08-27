package bas.app.shift.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import bas.app.shift.R
import bas.app.shift.databinding.ActivityFamiliarFoundBinding
import bas.app.shift.models.FamiliarData

class FamiliarFoundActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFamiliarFoundBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFamiliarFoundBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupFamiliarImage()
        setupButtons()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.familiar_notification_title)
    }

    private fun setupFamiliarImage() {
        // Получаем ID фамильяра из Intent (передается из уведомления)
        val familiarId = intent.getStringExtra("familiar_id") ?: "familiar_malachite_lizard"
        
        // Показываем название фамильяра
        val familiarName = FamiliarData.getNameById(familiarId)
        binding.tvFamiliarName.text = familiarName
        
        // Всегда показываем первую картинку для найденного фамильяра
        val imageName = FamiliarData.getImageNameById(familiarId, 1)
        val imageResId = resources.getIdentifier(
            imageName, 
            "drawable", 
            packageName
        )
        
        if (imageResId != 0) {
            binding.familiarImage.setImageResource(imageResId)
        } else {
            // Fallback если изображение не найдено
            binding.familiarImage.setImageResource(R.drawable.familiar_malachite_lizard)
        }
    }

    private fun setupButtons() {
        binding.btnTalk.setOnClickListener {
            binding.tvStatus.text = getString(R.string.familiar_listening)
            binding.btnTalk.isEnabled = false
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
