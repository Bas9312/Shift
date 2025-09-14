package bas.app.shift.models

import com.google.gson.annotations.SerializedName

data class NoiseAdjustResponse(
    @SerializedName("userId") val userId: String,
    val input: NoiseInput,
    val local: NoiseLocal,
    val global: NoiseGlobal
)

data class NoiseInput(
    val delta: Double,
    val command: String
)

data class NoiseLocal(
    val before: Double,
    val after: Double,
    @SerializedName("applied_delta") val appliedDelta: Double,
    @SerializedName("activity_rate_ema") val activityRateEma: Double,
    @SerializedName("activity_mult") val activityMult: Double,
    @SerializedName("softcap_mult") val softcapMult: Double
)

data class NoiseGlobal(
    val before: Double,
    val after: Double,
    @SerializedName("applied_delta") val appliedDelta: Double,
    @SerializedName("n_active") val nActive: Int
)
