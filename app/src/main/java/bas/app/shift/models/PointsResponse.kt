package bas.app.shift.models

data class PointsResponse(
    val timestamp: Long,
    val points: List<Point>
)