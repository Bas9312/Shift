package bas.app.shift.api

import bas.app.shift.models.User
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

interface UserProfileApi {
    @GET("/mage_profile_api/api/v1/users/{id}")
    fun getUserProfile(@Path("id") userId: String): Call<User>

    @GET("/mage_profile_api/api/v1/users")
    fun getAllUserProfiles(): Call<List<User>>
} 