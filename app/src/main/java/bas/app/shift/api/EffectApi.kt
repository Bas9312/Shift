package bas.app.shift.api

import bas.app.shift.models.Effect
import bas.app.shift.models.EffectRequest
import retrofit2.Response
import retrofit2.http.*

interface EffectApi {
    @POST("/api/v1/effects_api/api/{userId}")
    suspend fun createEffect(
        @Path("userId") userId: String,
        @Body effect: EffectRequest
    ): Response<Effect>

    @DELETE("/api/v1/effects_api/api/{userId}/{effectId}")
    suspend fun deleteEffect(
        @Path("userId") userId: String,
        @Path("effectId") effectId: Int
    ): Response<Unit>
}



