package bas.app.shift.api

import bas.app.shift.models.ChatHistory
import bas.app.shift.models.ChatSendRequest
import bas.app.shift.models.ChatSendResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ChatApi {
    @GET("chat/history")
    suspend fun getChatHistory(
        @Query("user_id") userId: String,
        @Query("familiar") familiar: String
    ): Response<ChatHistory>

    @POST("chat/send")
    suspend fun sendMessage(
        @Body request: ChatSendRequest
    ): Response<ChatSendResponse>
}
