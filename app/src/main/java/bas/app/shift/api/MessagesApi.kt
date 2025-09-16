package bas.app.shift.api

import bas.app.shift.models.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*

interface MessagesApi {
    
    // Создать новое сообщение
    @Multipart
    @POST("messages_api/messages")
    fun createMessage(
        @Header("X-User-Id") userId: String,
        @Part("text") text: RequestBody,
        @Part("recipient_id") recipientId: RequestBody,
        @Part("tags") tags: RequestBody?,
        @Part("answer_to") answerTo: RequestBody?,
        @Part files: List<MultipartBody.Part>?
    ): Call<CreateMessageResponse>
    
    // Получить список сообщений
    @GET("messages_api/messages")
    fun getMessages(
        @Header("X-User-Id") userId: String,
        @Query("limit") limit: Int = 10,
        @Query("offset") offset: Int = 0,
        @Query("tags") tags: String? = null,
        @Query("type") type: String? = null
    ): Call<GetMessagesResponse>
    
    // Пометить сообщение как прочитанное
    @PUT("messages_api/messages/{id}/read")
    fun markAsRead(
        @Header("X-User-Id") userId: String,
        @Path("id") messageId: Int
    ): Call<MarkAsReadResponse>
    
    // Получить список чатов (только для МГ пользователей)
    @GET("messages_api/chats")
    fun getChats(
        @Header("X-User-Id") userId: String
    ): Call<GetChatsResponse>
    
    // Получить историю чата с конкретным пользователем (только для МГ)
    @GET("messages_api/chats/{peerId}/history")
    fun getChatHistory(
        @Header("X-User-Id") userId: String,
        @Path("peerId") peerId: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("tags") tags: String? = null
    ): Call<GetMessagesResponse>
}
