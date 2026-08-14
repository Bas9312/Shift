package bas.app.shift.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import bas.app.shift.databinding.ItemChatBinding
import bas.app.shift.helpers.DateTimeHelper
import bas.app.shift.helpers.DisplayNames
import bas.app.shift.models.Chat

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
                tvUserName.text = DisplayNames.combine(
                    chat.interlocutorName,
                    chat.interlocutorPlayerName,
                    chat.interlocutor
                )
                tvLastMessage.text = chat.lastMessage?.content ?: "Нет сообщений"

                // Форматируем время
                tvTime.text = chat.lastMessage?.createdAt?.let { DateTimeHelper.formatMessageTime(it) } ?: ""

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
