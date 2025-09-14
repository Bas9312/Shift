package bas.app.shift.models

import com.google.gson.annotations.SerializedName

data class GlobalNoiseResponse(
    @SerializedName("global_noise") val globalNoise: Double
)
