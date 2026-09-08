package bas.app.shift.api

import bas.app.shift.models.CommandCostsResponse
import bas.app.shift.models.NoiseAdjustRequest
import bas.app.shift.models.NoiseAdjustResponse
import bas.app.shift.models.NoiseState
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface NoiseApi {
    @GET("/noize_api/api/v1/user/{userId}")
    fun getUserNoise(@Path("userId") userId: String): Call<NoiseState>

    /** Прайс команд: цены живут на сервере, приложение их только показывает. */
    @GET("/noize_api/api/v1/commands")
    fun getCommandCosts(): Call<CommandCostsResponse>

    @POST("/noize_api/api/v1/user/{userId}/adjust")
    fun adjustUserNoise(
        @Path("userId") userId: String,
        @Body body: NoiseAdjustRequest
    ): Call<NoiseAdjustResponse>

}
