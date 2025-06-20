package bas.app.shift.models

data class AuraMarkRequest(
    val markType: AuraMarkType? = null,
    val imageUrl: String? = null,
    val name: String? = null,
    val description: String? = null,
    val external: Boolean? = null
) 