package bas.app.shift.ui.terminal

import android.animation.ValueAnimator
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
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
            addLine("OK", Line.Type.RSP)
            incNoise(1)
        }, 300)
    }

    private fun incNoise(delta: Int) {
        noise = (noise + delta).coerceAtMost(7)
        updateNoise(noise)
        if (noise == 5) { showGlitchEvent() }
    }

    private fun showGlitchEvent() {
        Toast.makeText(this, "Глюк-атака! Шум 5", Toast.LENGTH_SHORT).show()
    }

    fun updateNoise(n: Int) {
        levelViews.forEachIndexed { idx, v ->
            v.setBackgroundColor(
                ContextCompat.getColor(this,
                    if (idx < n) colors[idx] else R.color.noiseOff)
            )
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
            R.drawable.bg_dropdown_dark)



    }

}
