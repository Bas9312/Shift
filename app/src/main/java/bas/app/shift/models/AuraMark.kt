package bas.app.shift.models

data class AuraMark(
    val markId: Int,
    val markType: AuraMarkType,
    val imageUrl: String,
    val name: String,
    val description: String? = null,
    val external: Boolean,
    val numberOfStars: Int? = null
) 