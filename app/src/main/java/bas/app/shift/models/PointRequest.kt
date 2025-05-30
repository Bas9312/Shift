package bas.app.shift.models

data class PointRequest(
    val lat: Double,
    val lng: Double,
    val show: Boolean,
    val id: Int
)