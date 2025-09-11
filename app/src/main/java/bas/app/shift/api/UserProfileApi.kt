package bas.app.shift.api

import bas.app.shift.models.Ability
import bas.app.shift.models.ShortUser
import bas.app.shift.models.User
import bas.app.shift.models.UserUpdateRequest
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Body

interface UserProfileApi {
    @GET("/mage_profile_api/api/v1/user/{id}")
    fun getUserProfile(@Path("id") userId: String): Call<User>

    @GET("/mage_profile_api/api/v1/users")
    fun getAllUserShortProfiles(): Call<List<ShortUser>>

    @GET("/mage_profile_api/api/v1/abilities")
    fun getAllAbilities(): Call<List<Ability>>

    @PUT("/mage_profile_api/api/v1/user/{id}")
    fun updateUserProfile(@Path("id") userId: String, @Body update: UserUpdateRequest): Call<Unit>
} 