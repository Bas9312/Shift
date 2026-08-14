package bas.app.shift.ui

import android.os.Bundle
import android.text.method.LinkMovementMethod
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.util.LinkifyCompat
import android.text.util.Linkify
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

        LinkifyCompat.addLinks(binding.titleText, LINKIFY_MASK)
        binding.titleText.movementMethod = LinkMovementMethod.getInstance()

        LinkifyCompat.addLinks(binding.bodyText, LINKIFY_MASK)
        binding.bodyText.movementMethod = LinkMovementMethod.getInstance()
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TEXT = "extra_text"

        // Linkify.ALL is deprecated because it includes MAP_ADDRESSES (unreliable geocoding-based
        // matching); everything else it covers is still current, so list those flags explicitly.
        private const val LINKIFY_MASK = Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES or Linkify.PHONE_NUMBERS
    }
}


