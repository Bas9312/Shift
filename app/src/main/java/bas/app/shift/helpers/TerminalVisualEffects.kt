package bas.app.shift.helpers

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import bas.app.shift.R

/**
 * Визуальные и тактильные эффекты уровней шума в терминале (мигание, глитч, красная
 * пелена, "демон", вибрация). Вынесено из TerminalActivity — чистые эффекты над view,
 * без завязки на бизнес-логику терминала.
 */
class TerminalVisualEffects(
    private val activity: AppCompatActivity,
    private val rootView: ViewGroup,
    private val noiseOverlay: View
) {

    private val redScrim: View by lazy {
        val scrim = View(activity)
        scrim.setBackgroundColor(0x55e74c3c)
        scrim.alpha = 0f
        rootView.addView(scrim, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        scrim
    }

    fun showNoise(level: Int) {
        if (noiseOverlay.visibility != View.VISIBLE) noiseOverlay.visibility = View.VISIBLE
        noiseOverlay.alpha = 0.08f * level
        // ImageView с @drawable/noise
        val anim = ValueAnimator.ofFloat(0f, 16f, -16f, 0f).apply {
            duration = 4000                    // 4 сек / цикл
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val shift = it.animatedValue as Float
                noiseOverlay.translationX = shift
                noiseOverlay.translationY = -shift / 2     // диагональный дрейф
            }
        }
        anim.start()
    }

    fun applyGlitch(level: Int) {
        // shake once
        ObjectAnimator.ofFloat(rootView, "translationX", 0f, 8f, -8f, 0f).apply {
            duration = 200
            start()
        }

        // purple tint matrix
        val matrix = ColorMatrix().apply {
            setScale(1f, 1f - 0.1f * level, 1f, 1f)
        }
        rootView.foreground = ColorDrawable(Color.TRANSPARENT).also {
            it.colorFilter = ColorMatrixColorFilter(matrix)
        }
    }

    fun showRedScrim() {
        redScrim.animate().alpha(0.5f).setDuration(150).start()
    }

    fun demonJumpScare() {
        val demon = ImageView(activity).apply {
            setImageResource(R.drawable.demon_silhouette)
            scaleX = 1.1f; scaleY = 1.1f
            alpha = 0f
        }
        rootView.addView(demon, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        demon.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(600).withEndAction {
                Handler(Looper.getMainLooper()).postDelayed({
                    rootView.removeView(demon)
                }, 1500)
            }.start()
    }

    fun vibrate() {
        val vib: Vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
            (activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vib.hasVibrator()) {
            val effect = VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
            vib.vibrate(effect)
        }
    }
}
