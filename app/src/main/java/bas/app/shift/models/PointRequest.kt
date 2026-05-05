package bas.app.shift.models

data class PointRequest(
    val type: String,
    val lat: Double,
    val lng: Double,
    val radius: Double? = null,
    val ownerId: String? = null,
    val description: String? = null,
    val textToShowOnEnter: String? = null,
    val aura_text: String? = null,
    val next_point_id: String? = null,
    val trackable: Boolean? = null,
    val hidden: Boolean? = null,
    val createdAt: String? = null,
)