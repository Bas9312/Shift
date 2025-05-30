package bas.app.shift.models

data class PointRequest(
    val lat: Double,
    val lng: Double,
    val pointId: String,
    val type: String,
    val radius: Double,
    val description: String
    //val ownerId: String = ""
)