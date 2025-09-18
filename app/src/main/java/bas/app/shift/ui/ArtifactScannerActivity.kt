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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import bas.app.shift.api.RetrofitClient
import bas.app.shift.helpers.LogHelper
import bas.app.shift.models.Artifact
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ArtifactScannerActivity : AppCompatActivity() {

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
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            REQUEST_CAMERA_PERMISSION
        )
    }

    private fun startScanner() {
        // Настраиваем сканер с улучшенными параметрами
        val integrator = IntentIntegrator(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.CODE_128, IntentIntegrator.CODE_39, IntentIntegrator.EAN_13, IntentIntegrator.EAN_8)
        integrator.setPrompt("Наведите камеру на штрих-код артефакта\n\n💡 Совет: Тапните по экрану для фокусировки")
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
                Toast.makeText(this, "Для сканирования штрих-кода требуется разрешение на камеру", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 101
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
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
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
                        Toast.makeText(
                            this@ArtifactScannerActivity, 
                            "Артефакт не найден или ошибка сервера: ${response.code()}", 
                            Toast.LENGTH_LONG
                        ).show()
                        LogHelper.e("ArtifactScannerActivity: Ошибка получения артефакта: ${response.code()}")
                        finish()
                    }
                }
                
                override fun onFailure(call: Call<Artifact>, t: Throwable) {
                    Toast.makeText(
                        this@ArtifactScannerActivity, 
                        "Ошибка сети: ${t.localizedMessage}", 
                        Toast.LENGTH_LONG
                    ).show()
                    LogHelper.e("ArtifactScannerActivity: Ошибка сети: ${t.localizedMessage}")
                    finish()
                }
            })
    }
} 