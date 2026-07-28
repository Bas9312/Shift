package bas.app.shift.utils

import android.graphics.Color
import bas.app.shift.models.PointType
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

object PointVisualizer {
    /**
     * Точки, требующие мастера (trackable=1), обводим жирным янтарным пунктиром.
     * До входа в круг игрок видит на карте ТОЛЬКО круг — маркер создаётся лишь когда игрок
     * внутри радиуса (см. MapPointsRenderer.refreshMarkersForLocation), поэтому обводка —
     * единственное место, где можно предупредить заранее, до того как игрок туда пойдёт.
     */
    private val TRACKABLE_STROKE_COLOR = Color.parseColor("#FFC107") // янтарный
    private const val TRACKABLE_STROKE_WIDTH = 7f
    private val TRACKABLE_STROKE_PATTERN = listOf(Dash(40f), Gap(24f))

    private val circleColors = mapOf(
        PointType.USER to Color.parseColor("#4CAF50"),        // Зеленый
        PointType.FAMILIAR to Color.parseColor("#1CAF50"),        // Зеленый
        PointType.FAKE_FAMILIAR_BITER to Color.parseColor("#1CAF50"),   // Зеленый, как у фамильяра
        PointType.OPEN_PROBLEM to Color.parseColor("#F44336"),    // Красный
        PointType.APPROACHING_BITER to Color.parseColor("#9C27B0"), // Фиолетовый
        PointType.SHRINKING_CIRCLE to Color.parseColor("#1FEB3B"), // Желтый
        PointType.DEMON_BLACK_CIRCLE to Color.parseColor("#000000"), // Желтый
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

    fun getCircleOptions(
        center: LatLng,
        radius: Float,
        type: PointType,
        isTrackable: Boolean = false
    ): CircleOptions {
        val color = circleColors[type] ?: Color.GRAY
        val options = CircleOptions()
            .center(center)
            .radius(radius.toDouble())
            .fillColor(Color.argb(128, Color.red(color), Color.green(color), Color.blue(color)))

        return if (isTrackable) {
            options
                .strokeColor(TRACKABLE_STROKE_COLOR)
                .strokeWidth(TRACKABLE_STROKE_WIDTH)
                .strokePattern(TRACKABLE_STROKE_PATTERN)
                // Кликабельность нужна, чтобы игрок мог ткнуть в круг ИЗДАЛЕКА и прочитать,
                // что сюда нужен мастер. Обычные точки некликабельны — иначе по ним можно
                // было бы собрать информацию, не доходя до места.
                .clickable(true)
        } else {
            options.strokeWidth(2f)
        }
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