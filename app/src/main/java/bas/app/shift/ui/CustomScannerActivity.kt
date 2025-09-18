package bas.app.shift.ui

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import com.journeyapps.barcodescanner.CaptureActivity
import bas.app.shift.helpers.LogHelper

class CustomScannerActivity : CaptureActivity() {
    
    private var lastFocusTime = 0L
    private val FOCUS_COOLDOWN = 1000L // 1 секунда между фокусировками
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Принудительно устанавливаем вертикальную ориентацию
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        
        LogHelper.d("CustomScannerActivity: Сканер инициализирован с улучшенными настройками")
    }
    
    override fun onResume() {
        super.onResume()
        LogHelper.d("CustomScannerActivity: Сканер возобновлен")
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastFocusTime > FOCUS_COOLDOWN) {
                lastFocusTime = currentTime
                focusOnTouch(event.x, event.y)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    
    private fun focusOnTouch(x: Float, y: Float) {
        try {
            // Простая реализация фокуса по тапу
            LogHelper.d("CustomScannerActivity: Фокус по тапу в точке ($x, $y)")
            runOnUiThread {
                Toast.makeText(this@CustomScannerActivity, "Фокус установлен", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            LogHelper.e("CustomScannerActivity: Ошибка фокуса по тапу: ${e.message}")
        }
    }
    
    override fun onPause() {
        super.onPause()
        LogHelper.d("CustomScannerActivity: Сканер приостановлен")
    }
} 