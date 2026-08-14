package bas.app.shift.ui

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import bas.app.shift.api.RetrofitClient
import bas.app.shift.helpers.LogHelper
import bas.app.shift.helpers.NetworkErrors
import bas.app.shift.models.Aura
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuraScannerActivity : AppCompatActivity() {

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        val scannedContent = result.contents
        if (scannedContent == null) {
            // Сканирование отменено
            Toast.makeText(this, "Сканирование отменено", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            LogHelper.d("AuraScannerActivity: Отсканирован QR-код: $scannedContent")

            // Валидируем содержимое QR-кода (userId не может быть пустым)
            val auraId = scannedContent.trim()
            if (auraId.isEmpty()) {
                Toast.makeText(this, "Неверный формат QR-кода", Toast.LENGTH_LONG).show()
                LogHelper.e("AuraScannerActivity: Пустое содержимое QR-кода")
                finish()
            } else {
                fetchAura(auraId)
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startScanner()
        } else {
            Toast.makeText(this, "Для сканирования QR-кода требуется разрешение на камеру", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Принудительно устанавливаем вертикальную ориентацию
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // Проверяем разрешение на камеру
        if (hasCameraPermission()) {
            startScanner()
        } else {
            requestCameraPermission()
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startScanner() {
        // Настраиваем сканер с улучшенными параметрами
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt("Наведите камеру на QR-код ауры\n\n💡 Совет: Тапните по экрану для фокусировки")
        options.setCameraId(0) // Используем заднюю камеру
        options.setBeepEnabled(false) // Отключаем звук
        options.setBarcodeImageEnabled(false) // Не сохраняем изображение
        options.setOrientationLocked(true) // Блокируем поворот экрана
        options.setCaptureActivity(CustomScannerActivity::class.java)

        // Дополнительные настройки для лучшего качества сканирования
        options.setTimeout(30000) // 30 секунд таймаут
        options.setTorchEnabled(false) // Отключаем фонарик

        barcodeLauncher.launch(options)
    }

    private fun fetchAura(userId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.auraApi.getAura(userId)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        // Успешно получили ауру, открываем экран с информацией
                        val intent = Intent(this@AuraScannerActivity, AuraActivity::class.java)
                        intent.putExtra("aura_id", userId)
                        startActivity(intent)
                        finish()
                    } else {
                        val errorMessage = when (response.code()) {
                            404 -> "Аура не найдена"
                            else -> NetworkErrors.http(response.code())
                        }
                        Toast.makeText(this@AuraScannerActivity, errorMessage, Toast.LENGTH_LONG).show()
                        LogHelper.e("AuraScannerActivity: Ошибка получения ауры: ${response.code()}")
                        finish()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AuraScannerActivity, NetworkErrors.network(e), Toast.LENGTH_LONG).show()
                    LogHelper.e("AuraScannerActivity: Ошибка сети: ${e.localizedMessage}")
                    finish()
                }
            }
        }
    }
} 