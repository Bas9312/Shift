package bas.app.shift.models

data class Point(
    val pointId: String,
    val type: String,
    val lat: Double,
    val lng: Double,
    val vLat: Double,
    val vLng: Double,
    val radius: Double,
    val description: String? = null,
    val textToShowOnEnter: String? = null
)