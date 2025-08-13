package bas.app.shift.models

import com.google.gson.annotations.SerializedName

data class AuraMarkRequest(
    @SerializedName("mark_type")
    val markType: AuraMarkType? = null,
    @SerializedName("image_url")
    val imageUrl: String? = null,
    val name: String? = null,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("external")
    val external: Boolean? = null
) 