package bas.app.shift.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import bas.app.shift.R
import bas.app.shift.databinding.ActivityFamiliarBinding
import bas.app.shift.helpers.FamiliarCatalog
import bas.app.shift.helpers.FamiliarImages
import bas.app.shift.helpers.LogHelper
import bas.app.shift.helpers.UserPrefsHelper
import coil3.load
import coil3.request.error
import coil3.request.placeholder

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
        val familiarId = UserPrefsHelper.getUserData(this)?.familiar
        if (familiarId.isNullOrEmpty()) {
            // Раньше здесь подставлялся familiar_malachite_lizard, и игрок без фамильяра
            // видел чужую ящерицу. Экран сюда больше не должен открываться (кнопка скрыта),
            // но если открылся — закрываемся, а не показываем ерунду.
            LogHelper.e("FamiliarActivity: фамильяра нет, закрываем экран")
            finish()
            return
        }

        binding.tvFamiliarName.text = FamiliarCatalog.getName(familiarId)

        val url = FamiliarImages.urlForNow(familiarId)
        if (url == null) {
            // Фамильяр без артов (кастомный) или каталог ещё не доехал.
            binding.familiarImage.setImageResource(R.drawable.ic_image_placeholder)
            LogHelper.d("FamiliarActivity: картинок нет для $familiarId, показан плейсхолдер")
            return
        }

        LogHelper.d("FamiliarActivity: грузим $url")
        binding.familiarImage.load(url) {
            placeholder(R.drawable.ic_image_placeholder)
            error(R.drawable.ic_image_placeholder)
        }
    }

    private fun setupButtons() {
        binding.btnTalk.setOnClickListener {
            val familiarId = UserPrefsHelper.getUserData(this)?.familiar ?: return@setOnClickListener
            val intent = Intent(this, FamiliarChatActivity::class.java).apply {
                putExtra("familiar", familiarId)
            }
            startActivity(intent)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
