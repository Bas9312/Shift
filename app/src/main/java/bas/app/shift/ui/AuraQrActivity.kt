package bas.app.shift.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import bas.app.shift.R
import bas.app.shift.databinding.ActivityAuraQrBinding
import bas.app.shift.helpers.UserPrefsHelper
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class AuraQrActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAuraQrBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuraQrBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        generateQrCode()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.aura_qr_title)
    }

    private fun generateQrCode() {
        // Получаем ID пользователя из Intent или используем текущего пользователя
        val userId = intent.getStringExtra("user_id") ?: UserPrefsHelper.getUserId(this)

        // 800×800 setPixel в цикле — ~640k вызовов, на UI-потоке это заметный фриз при
        // открытии экрана. Считаем на Default-диспетчере, применяем результат на Main.
        lifecycleScope.launch {
            val qrBitmap = withContext(Dispatchers.Default) {
                generateQRCode(userId, 800, 800)
            }

            if (qrBitmap != null) {
                binding.qrCodeImage.setImageBitmap(qrBitmap)
            } else {
                // В случае ошибки можно показать Toast или оставить пустым
                Toast.makeText(this@AuraQrActivity, "Ошибка генерации QR кода", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generateQRCode(content: String, width: Int, height: Int): Bitmap? {
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.MARGIN] = 1

            val qrCodeWriter = QRCodeWriter()
            val bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
