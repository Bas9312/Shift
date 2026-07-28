package bas.app.shift.api

import bas.app.shift.models.*
import retrofit2.Response
import retrofit2.http.*

interface ShiftApi {
    @GET("/api_geo/")
    suspend fun getApiInfo(): Response<ApiInfoResponse>

    @POST("/api_geo/api/v1/users/location")
    suspend fun updateUserLocation(@Body userLocation: UserLocation): Response<StatusResponse>

    @GET("/api_geo/api/v1/points")
    suspend fun getPoints(@Query("user_id") userId: String): Response<PointsResponse>

    @POST("/api_geo/api/v1/points")
    suspend fun createPoint(@Body pointRequest: PointRequest): Response<Point>

    @PATCH("/api_geo/api/v1/points/{id}")
    suspend fun updatePoint(
        @Path("id") pointId: String,
        @Body body: UpdatePointRequest,
    ): Response<Point>

    /** Занять фамильяра под себя. 409, если с ним уже общается другой игрок. */
    @POST("/api_geo/api/v1/points/{id}/bind")
    suspend fun bindFamiliar(
        @Path("id") pointId: String,
        @Body body: BindFamiliarRequest,
    ): Response<Point>

    /** Продлить свою привязку (сервер двигает last_message_time, отсчёт 15 минут — заново). */
    @POST("/api_geo/api/v1/points/{id}/touch")
    suspend fun touchFamiliar(@Path("id") pointId: String): Response<Point>

    @DELETE("/api_geo/api/v1/points/{id}")
    suspend fun deletePoint(@Path("id") pointId: String): Response<Unit>
}