package bas.app.shift.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import bas.app.shift.R
import bas.app.shift.models.Message
import bas.app.shift.models.Disciplines
import bas.app.shift.helpers.UserPrefsHelper
import java.text.SimpleDateFormat
import java.util.*

class MessagesAdapter(
    private val onMessageClick: (Message) -> Unit = {},
    private val onMessageLongClick: (Message) -> Unit = {}
) : RecyclerView.Adapter<MessagesAdapter.MessageViewHolder>() {

    private var messages = mutableListOf<Message>()
    private var currentUserId: String = ""
    private var selectedMessageId: Int? = null
    private var interlocutorName: String? = null
    private var interlocutorId: String? = null

    fun setCurrentUserId(userId: String) {
        currentUserId = userId
    }
    
    fun setInterlocutorName(name: String?) {
        interlocutorName = name
        android.util.Log.d("MessagesAdapter", "setInterlocutorName called with: $name")
    }
    
    fun setInterlocutorId(id: String?) {
        interlocutorId = id
        android.util.Log.d("MessagesAdapter", "setInterlocutorId called with: $id")
    }
    
    fun selectMessage(messageId: Int) {
        selectedMessageId = messageId
        notifyDataSetChanged()
    }
    
    fun clearSelection() {
        selectedMessageId = null
        notifyDataSetChanged()
    }
    
    fun getSelectedMessage(): Message? {
        return selectedMessageId?.let { id ->
            messages.find { it.id == id }
        }
    }

    fun updateMessages(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun addMessage(message: Message) {
        // При stackFromEnd = true, новые сообщения добавляются в конец списка (позиция 0)
        // Но нужно добавить в правильном порядке - новые сообщения должны быть внизу
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
            val isSelected = selectedMessageId == message.id
            
            // Для МГ пользователей: все сообщения от МГ считаются "своими" для подсвечивания
            val isFromMG = message.senderId.startsWith("MG_")
            val shouldHighlightAsOwn = if (currentUserId.startsWith("MG_")) {
                isFromMG // Если мы МГ, то все МГ сообщения подсвечиваем как свои
            } else {
                isCurrentUser // Если мы не МГ, то только свои сообщения
            }
            
            // Подсвечиваем выбранное сообщение (будет установлен в конце)
            
            // Настраиваем отображение отправителя
            val senderName = if (isCurrentUser) {
                "Вы"
            } else {
                // Для МГ пользователей проверяем, является ли отправитель собеседником
                if (currentUserId.startsWith("MG_")) {
                    // Если это сообщение от собеседника (с кем открыт чат), показываем имя
                    if (message.senderId == interlocutorId) {
                        interlocutorName ?: message.senderId
                    } else {
                        // Если от другого МГ, показываем ID
                        message.senderId
                    }
                } else {
                    // Для обычных пользователей показываем ID
                    message.senderId
                }
            }
            tvSender.text = senderName
            android.util.Log.d("MessagesAdapter", "bind message ${message.id}: currentUserId=$currentUserId, message.senderId=${message.senderId}, isCurrentUser=$isCurrentUser, isFromMG=$isFromMG, shouldHighlightAsOwn=$shouldHighlightAsOwn, interlocutorId=$interlocutorId, interlocutorName=$interlocutorName, senderName=$senderName")
            
            // Форматируем время
            tvTime.text = formatTime(message.createdAt)
            
            // Отображаем содержимое сообщения
            tvContent.text = message.content
            
            // Отображаем теги, если они есть
            if (message.tags.isNotEmpty()) {
                tagsContainer.visibility = View.VISIBLE
                // Преобразуем ID тегов в названия дисциплин
                val disciplineNames = message.tags.mapNotNull { tagId ->
                    Disciplines.DISCIPLINES.find { it.id == tagId }?.name
                }
                tvTags.text = disciplineNames.joinToString(", ")
            } else {
                tagsContainer.visibility = View.GONE
            }
            
            // Отображаем статус прочтения для всех сообщений
            if (message.readStatus == "read") {
                tvReadStatus.visibility = View.VISIBLE
                tvReadStatus.text = "✓✓"
                tvReadStatus.setTextColor(itemView.context.getColor(R.color.primary_light))
            } else if (message.readStatus == "unread") {
                tvReadStatus.visibility = View.VISIBLE
                tvReadStatus.text = "✓"
                tvReadStatus.setTextColor(itemView.context.getColor(R.color.text_secondary))
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
            
            if (shouldHighlightAsOwn) {
                // Сообщение от МГ (если мы МГ) или от нас - справа, синий фон
                parentLayout.gravity = android.view.Gravity.END
                messageContainer.setBackgroundResource(R.drawable.bg_message_bubble_sent)
            } else {
                // Сообщение от обычного пользователя - слева, белый фон
                parentLayout.gravity = android.view.Gravity.START
                // Устанавливаем фон с учетом выбора
                messageContainer.setBackgroundResource(
                    if (isSelected) R.drawable.bg_message_bubble_selected
                    else R.drawable.bg_message_bubble
                )
            }
            
            // Обработчики кликов по сообщению
            itemView.setOnClickListener {
                onMessageClick(message)
            }
            
            // Обработчик длинного клика на контейнере сообщения (только для сообщений от других пользователей)
            messageContainer.setOnLongClickListener {
                android.util.Log.d("MessagesAdapter", "Long click on message: ${message.id}, isCurrentUser: $isCurrentUser")
                if (!isCurrentUser) {
                    onMessageLongClick(message)
                }
                true
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
