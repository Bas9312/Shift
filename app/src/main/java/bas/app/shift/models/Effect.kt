package bas.app.shift.models

import com.google.gson.annotations.SerializedName

data class Effect(
    val id: Int,
    @SerializedName("textToShowPlayers")
    val textToShowPlayers: String,
    @SerializedName("markId")
    val markId: Int,
    @SerializedName("expireAt")
    val expireAt: String?
)



