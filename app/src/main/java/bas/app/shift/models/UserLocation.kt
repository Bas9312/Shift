package bas.app.shift.models

data class UserLocation(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val show: Boolean,
)