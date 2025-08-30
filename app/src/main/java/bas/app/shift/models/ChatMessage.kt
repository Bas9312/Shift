package bas.app.shift.models

import com.google.gson.annotations.SerializedName

data class ChatMessage(
    val role: String, // "user" или "assistant"
    val content: String,
    val ts: Double // Unix timestamp в секундах
)

data class ChatHistory(
    val messages: List<ChatMessage>
)

data class ChatSendRequest(
    @SerializedName("user_id") val userId: String,
    val familiar: String,
    val text: String
)

data class ChatSendResponse(
    val text: String
)
