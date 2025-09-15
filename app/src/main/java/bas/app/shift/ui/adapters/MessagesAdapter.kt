package bas.app.shift.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import bas.app.shift.R
import bas.app.shift.models.Message
import bas.app.shift.helpers.UserPrefsHelper
import java.text.SimpleDateFormat
import java.util.*

class MessagesAdapter(
    private val onMessageClick: (Message) -> Unit = {}
) : RecyclerView.Adapter<MessagesAdapter.MessageViewHolder>() {

    private var messages = mutableListOf<Message>()
    private var currentUserId: String = ""

    fun setCurrentUserId(userId: String) {
        currentUserId = userId
    }

    fun updateMessages(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
    
    fun hasMessage(messageId: Int): Boolean {
        return messages.any { it.id == messageId }
    }
    
    fun removeMessage(messageId: Int) {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index != -1) {
            messages.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun updateMessage(message: Message) {
        val index = messages.indexOfFirst { it.id == message.id }
        if (index != -1) {
            messages[index] = message
            notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageContainer: View = itemView.findViewById(R.id.messageContainer)
        private val tvSender: TextView = itemView.findViewById(R.id.tvSender)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvContent: TextView = itemView.findViewById(R.id.tvContent)
        private val tvTags: TextView = itemView.findViewById(R.id.tvTags)
        private val tvReadStatus: TextView = itemView.findViewById(R.id.tvReadStatus)
        private val tagsContainer: View = itemView.findViewById(R.id.tagsContainer)
        private val attachmentsContainer: View = itemView.findViewById(R.id.attachmentsContainer)

        fun bind(message: Message) {
            // Определяем, является ли текущий пользователь отправителем
            val isCurrentUser = message.senderId == currentUserId
            
            // Настраиваем отображение отправителя
            tvSender.text = if (isCurrentUser) "Вы" else message.senderId
            
            // Форматируем время
            tvTime.text = formatTime(message.createdAt)
            
            // Отображаем содержимое сообщения
            tvContent.text = message.content
            
            // Отображаем теги, если они есть
            if (message.tags.isNotEmpty()) {
                tagsContainer.visibility = View.VISIBLE
                tvTags.text = message.tags.joinToString(", ")
            } else {
                tagsContainer.visibility = View.GONE
            }
            
            // Отображаем статус прочтения для отправленных сообщений
            if (isCurrentUser) {
                tvReadStatus.visibility = View.VISIBLE
                tvReadStatus.text = if (message.readStatus == "read") "✓✓" else "✓"
                tvReadStatus.setTextColor(
                    if (message.readStatus == "read") 
                        itemView.context.getColor(R.color.primary_light)
                    else 
                        itemView.context.getColor(R.color.text_secondary)
                )
            } else {
                tvReadStatus.visibility = View.GONE
            }
            
            // Показываем вложения, если они есть
            if (message.attachments.isNotEmpty()) {
                attachmentsContainer.visibility = View.VISIBLE
                // TODO: Реализовать отображение вложений
            } else {
                attachmentsContainer.visibility = View.GONE
            }
            
            // Настраиваем выравнивание сообщения
            val parentLayout = itemView as LinearLayout
            
            if (isCurrentUser) {
                // Сообщение от текущего пользователя - справа
                parentLayout.gravity = android.view.Gravity.END
                messageContainer.setBackgroundResource(R.drawable.bg_message_bubble_sent)
            } else {
                // Сообщение от другого пользователя - слева
                parentLayout.gravity = android.view.Gravity.START
                messageContainer.setBackgroundResource(R.drawable.bg_message_bubble)
            }
            
            // Обработчик клика по сообщению
            itemView.setOnClickListener {
                onMessageClick(message)
            }
        }
        
        private fun formatTime(createdAt: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val date = inputFormat.parse(createdAt)
                outputFormat.format(date ?: Date())
            } catch (e: Exception) {
                createdAt
            }
        }
    }
}
