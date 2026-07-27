package bas.app.shift.helpers

import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round

/**
 * Чистая математика нелинейного слайдера радиуса точки (5м..3000м) и подсказки зума камеры —
 * вынесена из EkatMaps, зависимостей от Android/карты нет, поэтому легко тестируется отдельно.
 */
object PointRadiusMath {
    const val MIN_RADIUS_METERS = 5.0
    const val MAX_RADIUS_METERS = 3000.0
    const val DEFAULT_CUSTOM_RADIUS_METERS = 50.0

    /**
     * На зуме ~15–16 круги 5–20м занимают считанные пиксели и "кажутся" неправильными.
     * Это простая эвристика, чтобы визуально было понятно, что радиус выставился.
     */
    fun zoomForRadiusMeters(radiusMeters: Double): Float? {
        return when {
            radiusMeters <= 0.0 -> null
            radiusMeters <= 20.0 -> 20f
            radiusMeters <= 50.0 -> 19f
            radiusMeters <= 100.0 -> 18f
            radiusMeters <= 200.0 -> 17f
            else -> null
        }
    }

    fun radiusFromSlider(sliderValue: Float): Double {
        val min = MIN_RADIUS_METERS
        val max = MAX_RADIUS_METERS
        val t = (sliderValue / 100.0).coerceIn(0.0, 1.0)
        val ratio = max / min
        val r = min * ratio.pow(t)
        return roundRadius(r)
    }

    fun sliderFromRadius(radiusMeters: Double): Float {
        val min = MIN_RADIUS_METERS
        val max = MAX_RADIUS_METERS
        val r = radiusMeters.coerceIn(min, max)
        val ratio = max / min
        val t = ln(r / min) / ln(ratio)
        return (t * 100.0).toFloat().coerceIn(0f, 100f)
    }

    private fun roundRadius(radiusMeters: Double): Double {
        val step = when {
            radiusMeters <= 20.0 -> 1.0
            radiusMeters <= 30.0 -> 2.0
            radiusMeters <= 50.0 -> 3.0
            radiusMeters <= 100.0 -> 5.0
            radiusMeters <= 200.0 -> 10.0
            radiusMeters <= 500.0 -> 25.0
            radiusMeters <= 1000.0 -> 50.0
            radiusMeters <= 2000.0 -> 100.0
            else -> 200.0
        }

        val snapped = round(radiusMeters / step) * step
        return snapped.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
    }

    fun formatRadius(radiusMeters: Double): String {
        return if (radiusMeters < 1000.0) {
            "${radiusMeters.toInt()} м"
        } else {
            String.format(Locale.getDefault(), "%.2f км", radiusMeters / 1000.0)
        }
    }
}
