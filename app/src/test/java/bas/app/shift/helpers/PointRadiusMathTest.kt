package bas.app.shift.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PointRadiusMathTest {

    @Test
    fun radiusFromSlider_endpointsMatchDeclaredMinAndMax() {
        assertEquals(PointRadiusMath.MIN_RADIUS_METERS, PointRadiusMath.radiusFromSlider(0f), 0.0)
        assertEquals(PointRadiusMath.MAX_RADIUS_METERS, PointRadiusMath.radiusFromSlider(100f), 0.0)
    }

    @Test
    fun radiusFromSlider_outOfRangeValues_areClamped() {
        assertEquals(PointRadiusMath.MIN_RADIUS_METERS, PointRadiusMath.radiusFromSlider(-50f), 0.0)
        assertEquals(PointRadiusMath.MAX_RADIUS_METERS, PointRadiusMath.radiusFromSlider(150f), 0.0)
    }

    @Test
    fun radiusFromSlider_isMonotonicallyIncreasing() {
        var previous = PointRadiusMath.radiusFromSlider(0f)
        for (v in 5..100 step 5) {
            val current = PointRadiusMath.radiusFromSlider(v.toFloat())
            assertTrue("slider=$v: $current should be >= previous $previous", current >= previous)
            previous = current
        }
    }

    @Test
    fun radiusFromSlider_alwaysWithinDeclaredBounds() {
        for (v in 0..100) {
            val r = PointRadiusMath.radiusFromSlider(v.toFloat())
            assertTrue(r >= PointRadiusMath.MIN_RADIUS_METERS)
            assertTrue(r <= PointRadiusMath.MAX_RADIUS_METERS)
        }
    }

    @Test
    fun sliderFromRadius_endpointsRoundTrip() {
        assertEquals(0f, PointRadiusMath.sliderFromRadius(PointRadiusMath.MIN_RADIUS_METERS), 0.01f)
        assertEquals(100f, PointRadiusMath.sliderFromRadius(PointRadiusMath.MAX_RADIUS_METERS), 0.01f)
    }

    @Test
    fun sliderFromRadius_outOfRangeValues_areClamped() {
        assertEquals(0f, PointRadiusMath.sliderFromRadius(0.0), 0.01f)
        assertEquals(100f, PointRadiusMath.sliderFromRadius(999999.0), 0.01f)
    }

    @Test
    fun radiusFromSlider_roundTripsThroughSliderFromRadius_closeToOriginalPosition() {
        // Слайдер снапает радиус к "красивому" шагу, поэтому точного round-trip нет —
        // проверяем, что обратное преобразование остаётся в пределах пары процентных пунктов.
        for (v in 0..100 step 10) {
            val radius = PointRadiusMath.radiusFromSlider(v.toFloat())
            val back = PointRadiusMath.sliderFromRadius(radius)
            assertTrue(
                "slider=$v -> radius=$radius -> back=$back",
                kotlin.math.abs(back - v) <= 3f
            )
        }
    }

    @Test
    fun zoomForRadiusMeters_bucketsMatchDocumentedThresholds() {
        assertNull(PointRadiusMath.zoomForRadiusMeters(0.0))
        assertEquals(20f, PointRadiusMath.zoomForRadiusMeters(20.0))
        assertEquals(19f, PointRadiusMath.zoomForRadiusMeters(50.0))
        assertEquals(18f, PointRadiusMath.zoomForRadiusMeters(100.0))
        assertEquals(17f, PointRadiusMath.zoomForRadiusMeters(200.0))
        assertNull(PointRadiusMath.zoomForRadiusMeters(201.0))
        assertNull(PointRadiusMath.zoomForRadiusMeters(3000.0))
    }

    @Test
    fun formatRadius_metersBelowOneThousand_formattedAsWholeMeters() {
        assertEquals("5 м", PointRadiusMath.formatRadius(5.0))
        assertEquals("999 м", PointRadiusMath.formatRadius(999.0))
    }

    @Test
    fun formatRadius_oneThousandAndAbove_formattedAsKilometers() {
        assertEquals("1.00 км", PointRadiusMath.formatRadius(1000.0).replace(",", "."))
        assertEquals("3.00 км", PointRadiusMath.formatRadius(3000.0).replace(",", "."))
    }
}
