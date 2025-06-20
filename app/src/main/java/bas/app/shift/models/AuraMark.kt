package bas.app.shift.models

import com.google.gson.annotations.SerializedName

data class AuraMark(
    val markId: Int,

    @SerializedName("mark_type")
    val markType: AuraMarkType,

    @SerializedName("image_url")
    val imageUrl: String,
    val name: String,
    val description: String? = null,
    val external: Int,

    @SerializedName("number_of_stars")
    val numberOfStars: Int? = null
) 