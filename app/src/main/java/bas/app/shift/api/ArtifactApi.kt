package bas.app.shift.api

import bas.app.shift.models.Artifact
import bas.app.shift.models.ArtifactRequest
import bas.app.shift.models.ArtifactUpdateRequest
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Body

interface ArtifactApi {
    @GET("/artifacts_api/api/v1/artifacts/{id}")
    fun getArtifact(@Path("id") artifactId: Int): Call<Artifact>
    
    @GET("/artifacts_api/api/v1/artifacts")
    fun getAllArtifacts(): Call<List<Artifact>>
    
    @POST("/artifacts_api/api/v1/artifacts")
    fun createArtifact(@Body artifact: ArtifactRequest): Call<Artifact>
    
    @PUT("/artifacts_api/api/v1/artifact/{id}")
    fun updateArtifact(@Path("id") artifactId: Int, @Body update: ArtifactUpdateRequest): Call<Artifact>
} 