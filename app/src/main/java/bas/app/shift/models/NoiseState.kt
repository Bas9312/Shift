package bas.app.shift.models

import com.google.gson.annotations.SerializedName

data class NoiseState(
    @SerializedName("userId") val userId: String,
    @SerializedName("local_noise") val localNoise: Double,
    @SerializedName("global_noise") val globalNoise: Double,
    @SerializedName("global_level") val globalLevel: Int,
    @SerializedName("noisemancers") val noisemancers: Int
)
