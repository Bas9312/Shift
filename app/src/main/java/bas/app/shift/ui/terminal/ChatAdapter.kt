package bas.app.shift.ui.terminal

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import bas.app.shift.R
import bas.app.shift.models.ChatMessage
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {
    
    private val messages = mutableListOf<ChatMessage>()
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    fun updateMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }
    
    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(messages[position])
    }
    
    override fun getItemCount(): Int = messages.size
    
    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRole: TextView = itemView.findViewById(R.id.tvRole)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvContent: TextView = itemView.findViewById(R.id.tvContent)
        
        fun bind(message: ChatMessage) {
            val roleText = when (message.role) {
                "user" -> "Вы"
                "assistant" -> "Фамильяр"
                else -> message.role
            }
            
            tvRole.text = roleText
            tvTimestamp.text = dateFormat.format(Date((message.ts * 1000).toLong()))
            tvContent.text = message.content
            
            // Выравнивание сообщений
            val layoutParams = tvContent.layoutParams as LinearLayout.LayoutParams
            if (message.role == "user") {
                layoutParams.marginStart = 64
                layoutParams.marginEnd = 0
                tvContent.setBackgroundResource(R.drawable.bg_chat_message_user)
            } else {
                layoutParams.marginStart = 0
                layoutParams.marginEnd = 64
                tvContent.setBackgroundResource(R.drawable.bg_chat_message)
            }
            tvContent.layoutParams = layoutParams
        }
    }
}
