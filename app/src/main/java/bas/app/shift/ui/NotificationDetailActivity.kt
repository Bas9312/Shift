package bas.app.shift.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import bas.app.shift.databinding.ActivityNotificationDetailBinding

class NotificationDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNotificationDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityNotificationDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Уведомление"
        val text = intent.getStringExtra(EXTRA_TEXT) ?: ""

        // Toolbar setup similar to other screens
        binding.toolbar.title = title
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.titleText.text = title
        binding.bodyText.text = text
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TEXT = "extra_text"
    }
}


