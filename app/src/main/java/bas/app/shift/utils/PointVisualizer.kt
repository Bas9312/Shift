package bas.app.shift.utils

import android.graphics.Color
import bas.app.shift.models.PointOfInterest
import bas.app.shift.models.PointType
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlin.random.Random

object PointVisualizer {
    private val circleColors = mapOf(
        PointType.FAMILIAR to Color.parseColor("#4CAF50"),        // Зеленый
        PointType.FAKE_FAMILIAR to Color.parseColor("#FF9800"),   // Оранжевый
        PointType.OPEN_PROBLEM to Color.parseColor("#F44336"),    // Красный
        PointType.AGGRESSIVE_FAMILIAR to Color.parseColor("#9C27B0"), // Фиолетовый
        PointType.HIDDEN_EFFECT to Color.parseColor("#2196F3"),   // Синий
        PointType.SHRINKING_CIRCLE to Color.parseColor("#FFEB3B") // Желтый
    )

    private val markerColors = mapOf(
        PointType.FAMILIAR to BitmapDescriptorFactory.HUE_GREEN,
        PointType.FAKE_FAMILIAR to BitmapDescriptorFactory.HUE_ORANGE,
        PointType.OPEN_PROBLEM to BitmapDescriptorFactory.HUE_RED,
        PointType.AGGRESSIVE_FAMILIAR to BitmapDescriptorFactory.HUE_VIOLET,
        PointType.HIDDEN_EFFECT to BitmapDescriptorFactory.HUE_BLUE,
        PointType.SHRINKING_CIRCLE to BitmapDescriptorFactory.HUE_YELLOW
    )

    fun getCircleOptions(center: LatLng, radius: Float, type: PointType): CircleOptions {
        val color = circleColors[type] ?: Color.GRAY
        return CircleOptions()
            .center(center)
            .radius(radius.toDouble())
            .fillColor(Color.argb(128, Color.red(color), Color.green(color), Color.blue(color)))
            .strokeColor(color)
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

    fun createPointOfInterest(id: String, position: LatLng, radius: Float, type: PointType, additionalInfo: Map<String, Any> = emptyMap()): PointOfInterest {
        val virtualCenter = getRandomOffsetPosition(position, radius)
        return PointOfInterest(id, position, virtualCenter, radius, type, additionalInfo)
    }

    private fun getRandomOffsetPosition(center: LatLng, radius: Float): LatLng {
        val angle = Random.nextDouble(0.0, 2 * Math.PI)
        val distance = Random.nextDouble(0.0, radius.toDouble())
        
        val lat = center.latitude + (distance * Math.cos(angle) / 111111.0)
        val lng = center.longitude + (distance * Math.sin(angle) / (111111.0 * Math.cos(Math.toRadians(center.latitude))))
        
        return LatLng(lat, lng)
    }
} 