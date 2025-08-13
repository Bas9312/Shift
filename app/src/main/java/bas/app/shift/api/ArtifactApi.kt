package bas.app.shift.api

import bas.app.shift.models.Artifact
import bas.app.shift.models.ArtifactRequest
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Body

interface ArtifactApi {
    @GET("/artifacts_api/api/v1/artifacts/{id}")
    fun getArtifact(@Path("id") artifactId: Int): Call<Artifact>
    
    @GET("/artifacts_api/api/v1/artifacts")
    fun getAllArtifacts(): Call<List<Artifact>>
    
    @POST("/artifacts_api/api/v1/artifacts")
    fun createArtifact(@Body artifact: ArtifactRequest): Call<Artifact>
} 