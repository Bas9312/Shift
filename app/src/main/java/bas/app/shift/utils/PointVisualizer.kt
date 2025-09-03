package bas.app.shift.utils

import android.graphics.Color
import bas.app.shift.models.PointType
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

object PointVisualizer {
    private val circleColors = mapOf(
        PointType.USER to Color.parseColor("#4CAF50"),        // Зеленый
        PointType.FAMILIAR to Color.parseColor("#1CAF50"),        // Зеленый
        PointType.FAKE_FAMILIAR_BITER to Color.parseColor("#1CAF50"),   // Зеленый, как у фамильяра
        PointType.OPEN_PROBLEM to Color.parseColor("#F44336"),    // Красный
        PointType.APPROACHING_BITER to Color.parseColor("#9C27B0"), // Фиолетовый
        PointType.SHRINKING_CIRCLE to Color.parseColor("#1FEB3B"), // Желтый
        PointType.DEMON_BLACK_CIRCLE to Color.parseColor("#2196F3"), // Желтый
        PointType.APPROACHING_VIRTUAL to Color.parseColor("#8FEB3B") // Желтый
    )

    private val markerColors = mapOf(
        PointType.USER to BitmapDescriptorFactory.HUE_GREEN,
        PointType.FAMILIAR to BitmapDescriptorFactory.HUE_ROSE,
        PointType.FAKE_FAMILIAR_BITER to BitmapDescriptorFactory.HUE_ROSE,
        PointType.OPEN_PROBLEM to BitmapDescriptorFactory.HUE_RED,
        PointType.APPROACHING_BITER to BitmapDescriptorFactory.HUE_VIOLET,
        PointType.OPEN_PROBLEM to BitmapDescriptorFactory.HUE_BLUE,
        PointType.SHRINKING_CIRCLE to BitmapDescriptorFactory.HUE_CYAN,
        PointType.APPROACHING_VIRTUAL to BitmapDescriptorFactory.HUE_YELLOW,
    )

    fun getCircleOptions(center: LatLng, radius: Float, type: PointType): CircleOptions {
        val color = circleColors[type] ?: Color.GRAY
        return CircleOptions()
            .center(center)
            .radius(radius.toDouble())
            .fillColor(Color.argb(128, Color.red(color), Color.green(color), Color.blue(color)))
            .strokeWidth(2f)
    }

    fun getMarkerOptions(position: LatLng, type: PointType, title: String, snippet: String): MarkerOptions {
        val color = markerColors[type] ?: BitmapDescriptorFactory.HUE_MAGENTA
        return MarkerOptions()
            .position(position)
            .title(title)
            .snippet(snippet)
            .icon(BitmapDescriptorFactory.defaultMarker(color))
    }
}