package bas.app.shift.api

import bas.app.shift.models.Artifact
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

interface ArtifactApi {
    @GET("/artifacts_api/api/v1/artifacts/{id}")
    fun getArtifact(@Path("id") artifactId: Int): Call<Artifact>
} 