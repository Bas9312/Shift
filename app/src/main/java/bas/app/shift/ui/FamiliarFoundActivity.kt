package bas.app.shift.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.startActivity
import bas.app.shift.R
import bas.app.shift.databinding.ActivityFamiliarFoundBinding
import bas.app.shift.models.FamiliarData
import bas.app.shift.ui.FamiliarChatActivity

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
        val familiarId = intent.getStringExtra("familiar_id") ?: "familiar_malachite_lizard"
        val familiarName = FamiliarData.getNameById(familiarId)
        binding.tvFamiliarName.text = familiarName

        val imageName = FamiliarData.getImageNameById(familiarId, 1)
        val imageResId = resources.getIdentifier(imageName, "drawable", packageName)
        if (imageResId != 0) {
            binding.familiarImage.setImageResource(imageResId)
        } else {
            binding.familiarImage.setImageResource(R.drawable.familiar_malachite_lizard)
        }
    }

    private fun setupButtons() {
        binding.btnTalk.setOnClickListener {
            val famId = intent.getStringExtra("familiar_id") ?: "familiar_malachite_lizard"
            val chatIntent = Intent(this, FamiliarChatActivity::class.java)
                .putExtra("familiar", famId) // используем тот же ключ!
            startActivity(chatIntent)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
