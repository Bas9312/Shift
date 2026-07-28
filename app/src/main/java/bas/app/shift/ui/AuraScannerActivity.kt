package bas.app.shift.ui

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import bas.app.shift.api.RetrofitClient
import bas.app.shift.helpers.LogHelper
import bas.app.shift.helpers.NetworkErrors
import bas.app.shift.models.Aura
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuraScannerActivity : AppCompatActivity() {

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
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            REQUEST_CAMERA_PERMISSION
        )
    }

    private fun startScanner() {
        // Настраиваем сканер с улучшенными параметрами
        val integrator = IntentIntegrator(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        integrator.setPrompt("Наведите камеру на QR-код ауры\n\n💡 Совет: Тапните по экрану для фокусировки")
        integrator.setCameraId(0) // Используем заднюю камеру
        integrator.setBeepEnabled(false) // Отключаем звук
        integrator.setBarcodeImageEnabled(false) // Не сохраняем изображение
        integrator.setOrientationLocked(true) // Блокируем поворот экрана
        integrator.setCaptureActivity(CustomScannerActivity::class.java)
        
        // Дополнительные настройки для лучшего качества сканирования
        integrator.setTimeout(30000) // 30 секунд таймаут
        integrator.setTorchEnabled(false) // Отключаем фонарик
        
        integrator.initiateScan()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanner()
            } else {
                Toast.makeText(this, "Для сканирования QR-кода требуется разрешение на камеру", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 102
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result: IntentResult? = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        
        if (result != null) {
            if (result.contents == null) {
                // Сканирование отменено
                Toast.makeText(this, "Сканирование отменено", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                // Получили результат сканирования
                val scannedContent = result.contents
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
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
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