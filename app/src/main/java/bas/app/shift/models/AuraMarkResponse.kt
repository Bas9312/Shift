package bas.app.shift.models

import com.google.gson.annotations.SerializedName

data class AuraMarkResponse(
    val success: Boolean,
    @SerializedName("mark_id")
    val markId: Int
)
