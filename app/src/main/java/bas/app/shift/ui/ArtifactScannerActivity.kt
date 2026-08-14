package bas.app.shift.ui

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import bas.app.shift.R
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import bas.app.shift.api.RetrofitClient
import bas.app.shift.helpers.LogHelper
import bas.app.shift.helpers.NetworkErrors
import bas.app.shift.models.Artifact
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ArtifactScannerActivity : AppCompatActivity() {

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        val scannedContent = result.contents
        if (scannedContent == null) {
            // Сканирование отменено
            Toast.makeText(this, "Сканирование отменено", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            LogHelper.d("ArtifactScannerActivity: Отсканирован штрих-код: $scannedContent")

            // Пытаемся извлечь ID артефакта
            try {
                val artifactId = scannedContent.toInt()
                fetchArtifact(artifactId)
            } catch (e: NumberFormatException) {
                Toast.makeText(this, "Неверный формат штрих-кода", Toast.LENGTH_LONG).show()
                LogHelper.e("ArtifactScannerActivity: Неверный формат штрих-кода: $scannedContent")
                finish()
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startScanner()
        } else {
            Toast.makeText(this, "Для сканирования штрих-кода требуется разрешение на камеру", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_artifact_scanner)

        // Принудительно устанавливаем вертикальную ориентацию
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // Ручной ввод ID
        val manualIdEditText = findViewById<TextInputEditText>(R.id.manualArtifactIdEditText)
        val confirmManualIdButton = findViewById<MaterialButton>(R.id.confirmManualIdButton)
        confirmManualIdButton.setOnClickListener {
            val text = manualIdEditText.text?.toString()?.trim()
            if (text.isNullOrEmpty()) {
                Toast.makeText(this, "Введите ID артефакта", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            try {
                val artifactId = text.toInt()
                fetchArtifact(artifactId)
            } catch (e: NumberFormatException) {
                Toast.makeText(this, "ID должен быть числом", Toast.LENGTH_LONG).show()
            }
        }

        // Кнопка запуска сканера
        val startScanButton = findViewById<MaterialButton>(R.id.startScanButton)
        startScanButton.setOnClickListener {
            if (hasCameraPermission()) {
                startScanner()
            } else {
                requestCameraPermission()
            }
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
        options.setDesiredBarcodeFormats(ScanOptions.CODE_128, ScanOptions.CODE_39, ScanOptions.EAN_13, ScanOptions.EAN_8)
        options.setPrompt("Наведите камеру на штрих-код артефакта\n\n💡 Совет: Тапните по экрану для фокусировки")
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

    private fun fetchArtifact(artifactId: Int) {
        RetrofitClient.artifactApi.getArtifact(artifactId)
            .enqueue(object : Callback<Artifact> {
                override fun onResponse(call: Call<Artifact>, response: Response<Artifact>) {
                    if (response.isSuccessful && response.body() != null) {
                        // Успешно получили артефакт, открываем экран с информацией
                        val intent = Intent(this@ArtifactScannerActivity, ArtifactActivity::class.java)
                        intent.putExtra("artifact_id", artifactId)
                        startActivity(intent)
                        finish()
                    } else {
                        val errorMessage = when (response.code()) {
                            404 -> "Артефакт не найден"
                            else -> NetworkErrors.http(response.code())
                        }
                        Toast.makeText(this@ArtifactScannerActivity, errorMessage, Toast.LENGTH_LONG).show()
                        LogHelper.e("ArtifactScannerActivity: Ошибка получения артефакта: ${response.code()}")
                        finish()
                    }
                }

                override fun onFailure(call: Call<Artifact>, t: Throwable) {
                    Toast.makeText(this@ArtifactScannerActivity, NetworkErrors.network(t), Toast.LENGTH_LONG).show()
                    LogHelper.e("ArtifactScannerActivity: Ошибка сети: ${t.localizedMessage}")
                    finish()
                }
            })
    }
} 