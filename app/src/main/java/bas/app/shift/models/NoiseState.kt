package bas.app.shift.models

import com.google.gson.annotations.SerializedName

data class NoiseState(
    @SerializedName("userId") val userId: String,
    @SerializedName("local_noise") val localNoise: Int
)
