package bas.app.shift.api

import bas.app.shift.models.*
import retrofit2.Response
import retrofit2.http.*

interface AuraApi {
    @GET("/aura_api/aura/{entity_id}")
    suspend fun getAura(@Path("entity_id") entityId: String): Response<Aura>

    @PUT("/aura_api/aura/{entity_id}/marks/{mark_id}")
    suspend fun updateAuraMark(
        @Path("entity_id") entityId: String,
        @Path("mark_id") markId: Int,
        @Body mark: AuraMarkRequest
    ): Response<Unit>

    @DELETE("/aura_api/aura/{entity_id}/marks/{mark_id}")
    suspend fun deleteAuraMark(
        @Path("entity_id") entityId: String,
        @Path("mark_id") markId: Int
    ): Response<Unit>

    @DELETE("/aura_api/aura/{entity_id}/problems/{slot}")
    suspend fun deleteAuraProblem(
        @Path("entity_id") entityId: String,
        @Path("slot") slot: Int
    ): Response<Unit>

    @POST("/aura_api/aura/{entity_id}/problems")
    suspend fun addAuraProblem(
        @Path("entity_id") entityId: String,
        @Body problem: AuraProblemRequest
    ): Response<Unit>

    @PUT("/aura_api/aura/{entity_id}/problems/{slot}")
    suspend fun updateAuraProblem(
        @Path("entity_id") entityId: String,
        @Path("slot") slot: Int,
        @Body problem: AuraProblemRequest
    ): Response<Unit>

    @POST("/aura_api/aura/{entity_id}/marks")
    suspend fun addAuraMark(
        @Path("entity_id") entityId: String,
        @Body mark: AuraMarkRequest
    ): Response<AuraMarkResponse>

    @PUT("/aura_api/aura/{entity_id}/hidden")
    suspend fun updateAuraHidden(
        @Path("entity_id") entityId: String,
        @Body request: AuraHiddenRequest
    ): Response<Unit>
} 