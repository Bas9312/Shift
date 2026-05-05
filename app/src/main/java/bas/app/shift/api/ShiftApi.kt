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
    suspend fun updatePointHidden(
        @Path("id") pointId: String,
        @Body body: UpdatePointHiddenRequest,
    ): Response<Point>

    @DELETE("/api_geo/api/v1/points/{id}")
    suspend fun deletePoint(@Path("id") pointId: String): Response<Unit>
}