package bas.app.shift.models

data class Point(
    val pointId: String,
    val type: String,
    val lat: Double,
    val lng: Double,
    val vLat: Double? = null,
    val vLng: Double? = null,
    val radius: Double,
    val initial_radius: Double? = null,
    val expireAt: String? = null,
    val ownerId: String? = null,
    val description: String? = null,
    val textToShowOnEnter: String? = null,
    val createdAt: String? = null,
    val aura_text: String? = null,
    val next_point_id: String? = null,
    val trackable: Int? = null,
    val hidden: Int? = null,
)

val Point.vLatOrLat: Double
    get() = vLat ?: lat

val Point.vLngOrLng: Double
    get() = vLng ?: lng