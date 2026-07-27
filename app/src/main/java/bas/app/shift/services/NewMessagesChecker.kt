package bas.app.shift.services

import android.content.Context
import android.content.SharedPreferences
import bas.app.shift.api.RetrofitClient
import bas.app.shift.helpers.LogHelper
import bas.app.shift.models.Chat
import bas.app.shift.models.Message
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Поллинг новых сообщений/чатов и решение «показывать ли уведомление» на основе кэша уже
 * увиденных/уведомлённых id в SharedPreferences. Вынесено из LocationService — сервис лишь
 * вызывает check(), а решение о показе нотификации приходит через колбэк onNewMessages.
 */
class NewMessagesChecker(
    private val context: Context,
    private val onNewMessages: (unreadCount: Int, isMG: Boolean) -> Unit
) {

    fun check(userId: String) {
        try {
            if (userId.startsWith("MG_")) {
                // Для MG пользователей используем getChats для получения списка чатов
                RetrofitClient.messagesApi.getChats(userId).enqueue(object : Callback<List<Chat>> {
                    override fun onResponse(call: Call<List<Chat>>, response: Response<List<Chat>>) {
                        if (response.isSuccessful) {
                            checkChats(userId, response.body() ?: emptyList())
                        }
                    }

                    override fun onFailure(call: Call<List<Chat>>, t: Throwable) {
                        LogHelper.e("NewMessagesChecker: Ошибка при получении чатов: ${t.message}")
                    }
                })
            } else {
                // Для обычных пользователей используем getMessages
                RetrofitClient.messagesApi.getMessages(
                    userId = userId,
                    limit = 10,
                    offset = 0,
                    type = "private"
                ).enqueue(object : Callback<List<Message>> {
                    override fun onResponse(call: Call<List<Message>>, response: Response<List<Message>>) {
                        if (response.isSuccessful) {
                            checkMessagesList(userId, response.body() ?: emptyList())
                        }
                    }

                    override fun onFailure(call: Call<List<Message>>, t: Throwable) {
                        LogHelper.e("NewMessagesChecker: Ошибка при получении сообщений: ${t.message}")
                    }
                })
            }
        } catch (e: Exception) {
            LogHelper.e("NewMessagesChecker: Ошибка при получении сообщений: ${e.message}")
        }
    }

    private fun checkChats(userId: String, chats: List<Chat>) {
        val prefs = prefs()
        val lastKnownMessageIds = prefs.getStringSet(lastKnownKey(userId), emptySet()) ?: emptySet()

        val newMessageIds = mutableSetOf<String>()
        val newMessageIdsToNotify = mutableSetOf<String>()

        chats.forEach { chat ->
            chat.lastMessage?.let { message ->
                val messageId = message.id.toString()
                newMessageIds.add(messageId)

                if (!lastKnownMessageIds.contains(messageId) && !message.senderId.startsWith("MG_")) {
                    newMessageIdsToNotify.add(messageId)
                    LogHelper.d("NewMessagesChecker: Найдено новое сообщение от ${message.senderId} (id=${message.id})")
                }
            }
        }

        prefs.edit().putStringSet(lastKnownKey(userId), newMessageIds).apply()
        notifyIfNotAlreadyNotified(prefs, userId, newMessageIdsToNotify, isMG = true)
    }

    private fun checkMessagesList(userId: String, messages: List<Message>) {
        val prefs = prefs()
        val lastKnownMessageIds = prefs.getStringSet(lastKnownKey(userId), emptySet()) ?: emptySet()

        val newMessageIds = mutableSetOf<String>()
        val newMessageIdsToNotify = mutableSetOf<String>()

        messages.forEach { message ->
            val messageId = message.id.toString()
            newMessageIds.add(messageId)

            if (!lastKnownMessageIds.contains(messageId) && message.senderId != userId) {
                newMessageIdsToNotify.add(messageId)
                LogHelper.d("NewMessagesChecker: Найдено новое сообщение от ${message.senderId}: ${message.content}")
            }
        }

        prefs.edit().putStringSet(lastKnownKey(userId), newMessageIds).apply()
        notifyIfNotAlreadyNotified(prefs, userId, newMessageIdsToNotify, isMG = false)
    }

    /** Общий хвост обоих путей: не показываем уведомление повторно за одни и те же ID. */
    private fun notifyIfNotAlreadyNotified(
        prefs: SharedPreferences,
        userId: String,
        newMessageIdsToNotify: Set<String>,
        isMG: Boolean
    ) {
        if (newMessageIdsToNotify.isEmpty()) return

        val notifiedMessageIds = prefs.getStringSet(notifiedKey(userId), emptySet()) ?: emptySet()
        val shouldNotify = newMessageIdsToNotify.any { !notifiedMessageIds.contains(it) }

        if (shouldNotify) {
            val updatedNotifiedIds = notifiedMessageIds + newMessageIdsToNotify
            prefs.edit().putStringSet(notifiedKey(userId), updatedNotifiedIds).apply()
            onNewMessages(1, isMG)
        }
    }

    private fun prefs(): SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun lastKnownKey(userId: String) = lastKnownKeyOf(userId)
    private fun notifiedKey(userId: String) = notifiedKeyOf(userId)

    companion object {
        const val PREFS_NAME = "messages_cache"

        private fun lastKnownKeyOf(userId: String) = "last_known_message_ids_$userId"
        private fun notifiedKeyOf(userId: String) = "notified_message_ids_$userId"

        /** Чистит кэш «уже виденных» и «уже уведомлённых» id сообщений для сменившегося пользователя. */
        fun clearCache(context: Context, userId: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .remove(lastKnownKeyOf(userId))
                .remove(notifiedKeyOf(userId))
                .apply()
        }
    }
}
