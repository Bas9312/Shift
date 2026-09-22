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

    // Цвет есть у каждого типа: раньше половина таблицы пустовала, и «скрытая зона эффекта»,
    // мёртвая AR-точка и по-настоящему неизвестный тип выглядели на карте одинаковым серым
    // пятном. Серый остался только у UNKNOWN — как сигнал «сервер прислал что-то новое».
    private val circleColors = mapOf(
        PointType.USER to Color.parseColor("#4CAF50"),                  // зелёный
        PointType.POINT to Color.parseColor("#2196F3"),                 // синий, нейтральная точка
        PointType.FAMILIAR to Color.parseColor("#1CAF50"),              // зелёный
        PointType.FAKE_FAMILIAR_BITER to Color.parseColor("#1CAF50"),   // зелёный, как у фамильяра
        PointType.HIDDEN_EFFECT_AREA to Color.parseColor("#795548"),    // коричневый, видит только МГ
        PointType.OPEN_PROBLEM to Color.parseColor("#F44336"),          // красный
        PointType.APPROACHING_BITER to Color.parseColor("#9C27B0"),     // фиолетовый
        PointType.SHRINKING_CIRCLE to Color.parseColor("#1FEB3B"),      // салатовый
        PointType.DEMON_BLACK_CIRCLE to Color.parseColor("#000000"),    // чёрный
        PointType.APPROACHING_VIRTUAL to Color.parseColor("#8FEB3B"),   // светло-салатовый
        PointType.POINT_WITH_TEXT to Color.parseColor("#9E9E9E"),       // серый, видит только МГ
    )

    private val markerColors = mapOf(
        PointType.USER to BitmapDescriptorFactory.HUE_GREEN,
        PointType.POINT to BitmapDescriptorFactory.HUE_BLUE,
        PointType.FAMILIAR to BitmapDescriptorFactory.HUE_ROSE,
        PointType.FAKE_FAMILIAR_BITER to BitmapDescriptorFactory.HUE_ROSE,
        PointType.HIDDEN_EFFECT_AREA to BitmapDescriptorFactory.HUE_ORANGE,
        PointType.OPEN_PROBLEM to BitmapDescriptorFactory.HUE_RED,
        PointType.APPROACHING_BITER to BitmapDescriptorFactory.HUE_VIOLET,
        PointType.SHRINKING_CIRCLE to BitmapDescriptorFactory.HUE_CYAN,
        PointType.DEMON_BLACK_CIRCLE to BitmapDescriptorFactory.HUE_MAGENTA,
        PointType.APPROACHING_VIRTUAL to BitmapDescriptorFactory.HUE_YELLOW,
        PointType.POINT_WITH_TEXT to BitmapDescriptorFactory.HUE_AZURE,
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