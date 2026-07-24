package bas.app.shift.ui

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import bas.app.shift.databinding.ActivityImageViewerBinding
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder

class ImageViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Настройка toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Просмотр изображения"

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // Получаем данные из Intent
        val imageUrl = intent.getStringExtra("image_url")
        val fileName = intent.getStringExtra("file_name") ?: "image.jpg"

        // Устанавливаем название файла
        binding.tvFileName.text = fileName

        // Загружаем изображение
        if (!imageUrl.isNullOrEmpty()) {
            val imageSource = if (imageUrl.startsWith("content://") || imageUrl.startsWith("file://") || imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                // Локальный файл или уже полный URL
                if (imageUrl.startsWith("content://") || imageUrl.startsWith("file://")) {
                    Uri.parse(imageUrl)
                } else {
                    imageUrl
                }
            } else {
                // Относительный путь с сервера - добавляем базовый URL
                "https://shift96.ru/messages_api/$imageUrl"
            }

            binding.photoView.load(imageSource) {
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_report_image)
                crossfade(true)
            }
        }
    }
}
