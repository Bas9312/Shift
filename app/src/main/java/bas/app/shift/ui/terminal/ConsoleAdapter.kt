package bas.app.shift.ui.terminal

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import bas.app.shift.R
import bas.app.shift.databinding.ItemConsoleLineBinding
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class Line(val text: String, val type: Type) {
    enum class Type { CMD, RSP, ERR, SYS }
}

class ConsoleAdapter(
    private val data: MutableList<Line>
) : RecyclerView.Adapter<ConsoleAdapter.Holder>() {

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
        holder.binding.tvLine.text = "[$time]  ${line.text}"
        holder.binding.tvLine.setTextColor(
            ContextCompat.getColor(
                holder.itemView.context,
                when (line.type) {
                    Line.Type.CMD -> R.color.cmdGreen    // добавь в colors.xml
                    Line.Type.RSP -> R.color.rspGray     //   ‟
                    Line.Type.ERR -> R.color.errRed
                    Line.Type.SYS -> R.color.sysBlue
                }
            )
        )
    }

    /** публичный метод для терминала */
    fun add(line: Line) {
        data += line

        notifyItemInserted(data.lastIndex)
    }
}
