package bas.app.shift.models

import com.google.gson.annotations.SerializedName

data class Aura(
    val userId: String,
    val type: AuraType,
    @SerializedName("percent_of_humanism")
    val percentOfHumanism: Int,
    @SerializedName("aura_hidden")
    val auraHidden: Boolean,
    @SerializedName("aura_problems")
    val auraProblems: List<AuraProblem>?,
    val marks: List<AuraMark>?
)

data class AuraHiddenRequest(
    @SerializedName("aura_hidden")
    val auraHidden: Int
) 