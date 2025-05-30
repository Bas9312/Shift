package bas.app.shift.ui.terminal

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import bas.app.shift.R
import bas.app.shift.databinding.ActivityTerminalBinding

class TerminalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTerminalBinding

    private lateinit var adapter: ConsoleAdapter
    private val levelViews by lazy {
        listOf<View>(
            findViewById(R.id.lvl1), findViewById(R.id.lvl2), findViewById(R.id.lvl3),
            findViewById(R.id.lvl4), findViewById(R.id.lvl5), findViewById(R.id.lvl6),
            findViewById(R.id.lvl7)
        )
    }

    private val colors = listOf(
        R.color.noise1, R.color.noise2, R.color.noise3,
        R.color.noise4, R.color.noise5, R.color.noise6, R.color.noise7
    )

    private var noise = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTerminalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initAutocomplete()
        setSupportActionBar(binding.topBar)

        adapter = ConsoleAdapter(mutableListOf())
        binding.consoleList.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.consoleList.adapter = adapter

        binding.btnSend.setOnClickListener {
            val cmd = binding.prompt.editCommand.text.toString().trim()
            if (cmd.isNotEmpty()) {
                addLine(cmd, Line.Type.CMD)
                binding.prompt.editCommand.text.clear()
                sendToServer(cmd)
            }
        }

        updateNoise(0)          // старт
    }

    private fun addLine(text: String, type: Line.Type) {
        adapter.add(Line(text, type))
        // автоскролл вниз
        binding.consoleList
            .scrollToPosition(adapter.itemCount - 1)
    }

    /** заглушка: вместо сервера просто эхо и ++шум */
    private fun sendToServer(cmd: String) {
        // TODO: реальный websocket / retrofit
        Handler(Looper.getMainLooper()).postDelayed({
            adapter.addTyping("OK, it's nice text, thanks!")
            binding.consoleList
                .scrollToPosition(adapter.itemCount - 1)
            incNoise(1)
        }, 300)
    }

    private fun incNoise(delta: Int) {
        noise = (noise + delta).coerceAtMost(7)
        updateNoise(noise)
        if (noise == 5) {
            showGlitchEvent()
        }
    }

    private fun showGlitchEvent() {
        Toast.makeText(this, "Глюк-атака! Шум 5", Toast.LENGTH_SHORT).show()
    }

    fun updateNoise(n: Int) {
        levelViews.forEachIndexed { idx, v ->
            v.setBackgroundColor(
                ContextCompat.getColor(
                    this,
                    if (idx < n) colors[idx] else R.color.noiseOff
                )
            )
        }

        when (n) {
            0, 1 -> binding.noiseOverlay.visibility = View.GONE
            2, 3 -> showNoise(n)
            4 -> {
                showNoise(n); applyGlitch(n)
            }

            5 -> {
                showNoise(n); applyGlitch(n); vibrator()
            }

            6 -> showRedScrim()
            7 -> demonJumpScare()
        }
    }

    private fun initAutocomplete() {
        val cursorAnim = ValueAnimator.ofFloat(0f, 8f).apply {
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            duration = 600
            addUpdateListener {
                binding.prompt.editCommand.setPaddingRelative(
                    binding.prompt.editCommand.paddingStart,
                    binding.prompt.editCommand.paddingTop,
                    (it.animatedValue as Float).toInt(),          // курсор-паддинг
                    binding.prompt.editCommand.paddingBottom
                )
            }
        }
        cursorAnim.start()

        val arr = resources.getStringArray(R.array.cmd_list)
        val autoAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            arr
        )

        binding.prompt.editCommand.setAdapter(autoAdapter)

        /* красивый фон у выпадашки */
        binding.prompt.editCommand.setDropDownBackgroundResource(
            R.drawable.bg_dropdown_dark
        )


    }

    private fun showNoise(level: Int) {
        if (binding.noiseOverlay.visibility != View.VISIBLE) binding.noiseOverlay.visibility = View.VISIBLE
        binding.noiseOverlay.alpha = 0.08f * level
        // ImageView с @drawable/noise
        val anim = ValueAnimator.ofFloat(0f, 16f, -16f, 0f).apply {
            duration = 4000                    // 4 сек / цикл
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val shift = it.animatedValue as Float
                binding.noiseOverlay .translationX = shift
                binding.noiseOverlay .translationY = -shift / 2     // диагональный дрейф
            }
        }
        anim.start()
    }

    fun applyGlitch(level: Int) {
        // shake once
        ObjectAnimator.ofFloat(binding.root, "translationX", 0f, 8f, -8f, 0f).apply {
            duration = 200
            start()
        }

        // purple tint matrix
        val matrix = ColorMatrix().apply {
            setScale(1f, 1f - 0.1f * level, 1f, 1f)
        }
        binding.root.foreground = ColorDrawable(Color.TRANSPARENT).also {
            it.colorFilter = ColorMatrixColorFilter(matrix)
        }
    }

    private val redScrim by lazy {
        View(this).apply {
            setBackgroundColor(0x55e74c3c)
            alpha = 0f
            binding.root.addView(
                this,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    fun showRedScrim() {
        redScrim.animate().alpha(0.5f).setDuration(150).start()
    }

    fun demonJumpScare() {
        val demon = ImageView(this).apply {
            setImageResource(R.drawable.demon_silhouette)
            scaleX = 1.1f; scaleY = 1.1f
            alpha = 0f
        }
        binding.root.addView(
            demon,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        demon.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(600).withEndAction {
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.root.removeView(demon)
                }, 1500)
            }.start()
    }

    private fun vibrator() {
        val vib: Vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vib.hasVibrator()) {
            val effect = VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
            vib.vibrate(effect)
        }
    }


}
