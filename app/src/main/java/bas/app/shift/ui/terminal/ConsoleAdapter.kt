package bas.app.shift.ui.terminal

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import bas.app.shift.R
import bas.app.shift.databinding.ItemConsoleLineBinding
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class Line(val text: String, val type: Type) {
    enum class Type { CMD, RSP, ERR, SYS, TYPING }
}

class ConsoleAdapter(
    private val data: MutableList<Line>
) : RecyclerView.Adapter<ConsoleAdapter.Holder>() {
    
    private var onScrollCallback: (() -> Unit)? = null
    
    fun setOnScrollCallback(callback: () -> Unit) {
        this.onScrollCallback = callback
    }

    inner class Holder(val binding: ItemConsoleLineBinding)
        : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemConsoleLineBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val line = data[position]
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val fullText = "[$time]  ${line.text}"
        
        // Создаем SpannableString для поддержки кликабельных ссылок
        val spannable = android.text.SpannableString(fullText)
        
        // Находим и делаем кликабельными ссылки Wikipedia
        val urlPattern = "https://ru\\.wikipedia\\.org/wiki/[^\\s]+"
        val regex = urlPattern.toRegex()
        val matches = regex.findAll(fullText)
        
        matches.forEach { matchResult ->
            val start = matchResult.range.first
            val end = matchResult.range.last + 1
            val url = matchResult.value
            
            // Добавляем кликабельную ссылку
            spannable.setSpan(
                android.text.style.URLSpan(url),
                start,
                end,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        holder.binding.tvLine.text = spannable
        holder.binding.tvLine.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        holder.binding.tvLine.setTextColor(
            ContextCompat.getColor(
                holder.itemView.context,
                when (line.type) {
                    Line.Type.CMD -> R.color.cmdGreen    // добавь в colors.xml
                    Line.Type.RSP -> R.color.rspGray     //   ‟
                    Line.Type.ERR -> R.color.errRed
                    Line.Type.SYS -> R.color.sysBlue
                    Line.Type.TYPING -> R.color.rspGray
                }
            )
        )
    }

    fun addTyping(text: String) {
        data += Line("", Line.Type.TYPING)        // пустая
        notifyItemInserted(data.lastIndex)
        onScrollCallback?.invoke()  // скроллим сразу при добавлении
        graduallyFill(text, data.lastIndex)
    }

    private fun graduallyFill(full: String, pos: Int) {
        var i = 0
        val h = Handler(Looper.getMainLooper())
        
        val step = object : Runnable {
            override fun run() {
                if (i <= full.length) {
                    data[pos] = Line(full.take(i), Line.Type.RSP)
                    notifyItemChanged(pos)
                    
                    // Скроллим при каждом обновлении текста
                    onScrollCallback?.invoke()
                    
                    i++
                    h.postDelayed(this, 25)       // 40 симв/сек
                } else {
                    // Финальный скроллинг в конце печати
                    onScrollCallback?.invoke()
                }
            }
        }
        h.post(step)
    }

    /** публичный метод для терминала */
    fun add(line: Line) {
        data += line

        notifyItemInserted(data.lastIndex)
    }
}
