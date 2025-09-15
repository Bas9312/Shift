package bas.app.shift.models

import com.google.gson.annotations.SerializedName

// Модель для вложения в сообщении
data class MessageAttachment(
    val id: Int,
    @SerializedName("message_id")
    val messageId: Int,
    @SerializedName("file_type")
    val fileType: String, // "image" или "video"
    @SerializedName("file_path")
    val filePath: String,
    @SerializedName("original_name")
    val originalName: String,
    @SerializedName("mime_type")
    val mimeType: String
)

// Модель сообщения
data class Message(
    val id: Int,
    @SerializedName("sender_id")
    val senderId: String,
    @SerializedName("recipient_id")
    val recipientId: String?,
    val content: String,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("read_status")
    val readStatus: String, // "unread" или "read"
    val attachments: List<MessageAttachment> = emptyList(),
    val tags: List<Int> = emptyList()
)

// Запрос на создание сообщения
data class CreateMessageRequest(
    val text: String,
    @SerializedName("recipient_id")
    val recipientId: String,
    val tags: String? = null, // Список тегов через запятую
    val files: List<MultipartFile>? = null
)

// Модель для файла в multipart запросе
data class MultipartFile(
    val filePath: String,
    val fileName: String,
    val mimeType: String
)

// Ответ на создание сообщения
data class CreateMessageResponse(
    val id: Int,
    @SerializedName("sender_id")
    val senderId: String,
    @SerializedName("recipient_id")
    val recipientId: String,
    val content: String,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("read_status")
    val readStatus: String,
    val tags: List<Int>
)

// Запрос на получение сообщений
data class GetMessagesRequest(
    val limit: Int = 10,
    val offset: Int = 0,
    val tags: String? = null,
    val type: String? = null // "private" для личных сообщений
)

// Ответ на запрос сообщений
typealias GetMessagesResponse = List<Message>

// Запрос на пометку сообщения как прочитанного
data class MarkAsReadResponse(
    val success: Boolean
)

// Ошибка API
data class ApiError(
    val error: String
)
