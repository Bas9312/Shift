package bas.app.shift.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.startActivity
import bas.app.shift.R
import bas.app.shift.databinding.ActivityFamiliarFoundBinding
import bas.app.shift.models.AuraType
import bas.app.shift.helpers.FamiliarCatalog
import bas.app.shift.helpers.FamiliarImages
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.ui.FamiliarChatActivity
import coil3.load
import coil3.request.error
import coil3.request.placeholder

class FamiliarFoundActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFamiliarFoundBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFamiliarFoundBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupButtons()
        setupFamiliarImage()
    }

    // Если активити уже открыта и приходит новый интент из уведомления:
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)          // обновляем this.intent
        setupFamiliarImage()       // перерисовываем контент под новый familiar_id
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.familiar_notification_title)
    }

    private fun setupFamiliarImage() {
        val familiarId = intent.getStringExtra("familiar_id").orEmpty()
        binding.tvFamiliarName.text = FamiliarCatalog.getName(familiarId)

        // Чужой фамильяр заранее не предзагружался — грузим по факту показа,
        // дальше он останется в кеше.
        val url = FamiliarImages.urlFor(familiarId, FamiliarImages.currentVariant())
        if (url == null) {
            binding.familiarImage.setImageResource(R.drawable.ic_image_placeholder)
            return
        }
        binding.familiarImage.load(url) {
            placeholder(R.drawable.ic_image_placeholder)
            error(R.drawable.ic_image_placeholder)
        }
    }

    private fun setupButtons() {
        val user = UserPrefsHelper.getUserData(this)
        
        // Проверяем тип пользователя
        if (user?.type != AuraType.MAGE) {
            // Если пользователь не маг или не загружен, скрываем верхний текст и показываем сообщение о невозможности стать фамильяром
            binding.tvRitualInfo.visibility = View.GONE
            binding.tvStatus.text = getString(R.string.familiar_not_mage_message)
            binding.btnTalk.visibility = View.GONE
        } else {
            // Если пользователь маг, показываем обычное сообщение и кнопку
            binding.tvRitualInfo.visibility = View.VISIBLE
            binding.tvStatus.text = getString(R.string.familiar_ready_to_talk)
            binding.btnTalk.visibility = View.VISIBLE
            
            binding.btnTalk.setOnClickListener {
                val famId = intent.getStringExtra("familiar_id") ?: "familiar_malachite_lizard"
                val chatIntent = Intent(this, FamiliarChatActivity::class.java)
                    .putExtra("familiar", famId) // используем тот же ключ!
                    // Может отсутствовать: из уведомления и с экрана «свой фамильяр» точки нет,
                    // тогда чат просто не продлевает привязку — занимать нечего.
                    .putExtra("point_id", intent.getStringExtra("point_id"))
                startActivity(chatIntent)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
