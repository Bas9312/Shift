package bas.app.shift.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class NoiseHelperTest {

    @Test
    fun getNoiseLevel_roundsDown() {
        assertEquals(3, NoiseHelper.getNoiseLevel(3.9))
        assertEquals(3, NoiseHelper.getNoiseLevel(3.0))
        assertEquals(0, NoiseHelper.getNoiseLevel(0.5))
    }

    @Test
    fun getNoiseLevel_clampsToZeroToFive() {
        assertEquals(0, NoiseHelper.getNoiseLevel(-1.0))
        assertEquals(5, NoiseHelper.getNoiseLevel(5.9))
        assertEquals(5, NoiseHelper.getNoiseLevel(100.0))
    }

    @Test
    fun formatNoiseValue_oneDecimalPlace() {
        assertEquals("3.9", NoiseHelper.formatNoiseValue(3.9))
        assertEquals("0.0", NoiseHelper.formatNoiseValue(0.0))
        assertEquals("5.0", NoiseHelper.formatNoiseValue(5.0))
    }

    @Test
    fun getLevelProgress_returnsFractionalPart() {
        assertEquals(0.3f, NoiseHelper.getLevelProgress(3.3), 0.0001f)
        assertEquals(0.0f, NoiseHelper.getLevelProgress(3.0), 0.0001f)
    }

    @Test
    fun getLevelProgress_negativeValue_stillFractional() {
        // floor(-1.0) == -1.0, но getNoiseLevel клэмпит уровень к 0 — эта функция
        // считает "сырую" дробную часть независимо от клэмпа уровня.
        assertEquals(0.0f, NoiseHelper.getLevelProgress(-1.0), 0.0001f)
    }

    @Test
    fun thresholdsCrossed_bigJump_returnsAllPassedLevels() {
        // Регрессия: раньше `when` с одной веткой ловил только первый порог при скачке 0 -> 5.
        assertEquals(listOf(3, 4, 5), NoiseHelper.thresholdsCrossed(0, 5))
    }

    @Test
    fun thresholdsCrossed_smallStep_returnsOnlyNewlyCrossed() {
        assertEquals(listOf(4), NoiseHelper.thresholdsCrossed(3, 4))
        assertEquals(emptyList<Int>(), NoiseHelper.thresholdsCrossed(3, 3))
    }

    @Test
    fun thresholdsCrossed_droppingLevel_returnsNothing() {
        assertEquals(emptyList<Int>(), NoiseHelper.thresholdsCrossed(5, 2))
    }






}
