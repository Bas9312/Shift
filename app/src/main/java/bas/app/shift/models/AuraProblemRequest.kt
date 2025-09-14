package bas.app.shift.models

import com.google.gson.annotations.SerializedName

data class AuraProblemRequest(
    val slot: Int?,
    @SerializedName("problem_type")
    val problemType: AuraProblemType,
    val name: String,
    val description: String? = null
)
