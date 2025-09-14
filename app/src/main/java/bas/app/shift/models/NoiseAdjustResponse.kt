package bas.app.shift.models

import com.google.gson.annotations.SerializedName

data class NoiseAdjustResponse(
    @SerializedName("userId") val userId: String,
    val delta: Int,
    @SerializedName("local_before") val localBefore: Int,
    @SerializedName("local_after") val localAfter: Int,
    @SerializedName("global_before") val globalBefore: Double,
    @SerializedName("global_after") val globalAfter: Double,
    @SerializedName("global_delta") val globalDelta: Double,
    @SerializedName("active_users") val activeUsers: Int?
)
