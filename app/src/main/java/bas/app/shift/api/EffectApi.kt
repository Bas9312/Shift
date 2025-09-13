package bas.app.shift.api

import bas.app.shift.models.Effect
import bas.app.shift.models.EffectRequest
import retrofit2.Response
import retrofit2.http.*

interface EffectApi {
    @POST("/effects_api/api/v1/effects/{userId}")
    suspend fun createEffect(
        @Path("userId") userId: String,
        @Body effect: EffectRequest
    ): Response<Effect>

    @DELETE("/effects_api/api/v1/effects/{userId}/{effectId}")
    suspend fun deleteEffect(
        @Path("userId") userId: String,
        @Path("effectId") effectId: Int
    ): Response<Unit>
}



