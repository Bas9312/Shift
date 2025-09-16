package bas.app.shift.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import bas.app.shift.databinding.ItemChatBinding
import bas.app.shift.models.Chat
import java.text.SimpleDateFormat
import java.util.*

class ChatsAdapter(
    private val onChatClick: (Chat) -> Unit
) : RecyclerView.Adapter<ChatsAdapter.ChatViewHolder>() {

    private var chats: List<Chat> = emptyList()

    fun updateChats(newChats: List<Chat>) {
        chats = newChats
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(chats[position])
    }

    override fun getItemCount(): Int = chats.size

    inner class ChatViewHolder(
        private val binding: ItemChatBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: Chat) {
            binding.apply {
                // Формируем имя в формате interlocutor_name / interlocutor_player_name
                val displayName = when {
                    !chat.interlocutorName.isNullOrEmpty() && !chat.interlocutorPlayerName.isNullOrEmpty() -> 
                        "${chat.interlocutorName} / ${chat.interlocutorPlayerName}"
                    !chat.interlocutorName.isNullOrEmpty() -> 
                        chat.interlocutorName
                    !chat.interlocutorPlayerName.isNullOrEmpty() -> 
                        chat.interlocutorPlayerName
                    else -> 
                        chat.interlocutor
                }
                tvUserName.text = displayName
                tvLastMessage.text = chat.lastMessage?.content ?: "Нет сообщений"
                
                // Форматируем время
                chat.lastMessage?.createdAt?.let { timeStr ->
                    try {
                        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                        val date = inputFormat.parse(timeStr)
                        tvTime.text = outputFormat.format(date ?: Date())
                    } catch (e: Exception) {
                        tvTime.text = timeStr
                    }
                } ?: run {
                    tvTime.text = ""
                }

                // Показываем счетчик непрочитанных
                if (chat.unreadCount > 0) {
                    tvUnreadCount.visibility = android.view.View.VISIBLE
                    tvUnreadCount.text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString()
                } else {
                    tvUnreadCount.visibility = android.view.View.GONE
                }

                // Обработчик клика
                root.setOnClickListener {
                    onChatClick(chat)
                }
            }
        }
    }
}
