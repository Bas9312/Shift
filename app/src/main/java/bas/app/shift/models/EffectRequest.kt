package bas.app.shift.models

import com.google.gson.annotations.SerializedName

data class EffectRequest(
    @SerializedName("textToShowPlayers")
    val textToShowPlayers: String,
    @SerializedName("time_to_live_in_minutes")
    val timeToLiveInMinutes: Int?,
    val mark: AuraMarkRequest?
)



