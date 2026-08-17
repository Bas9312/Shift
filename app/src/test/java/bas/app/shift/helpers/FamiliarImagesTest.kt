package bas.app.shift.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Выбор варианта картинки по часу. Логика перенесена из удалённого FamiliarData:
 * ночь с 2:00 до 10:00 (одна картинка), днём три картинки по кругу.
 */
class FamiliarImagesTest {

    @Test
    fun isNightTime_between2And10_isNight() {
        assertTrue(FamiliarImages.isNightTime(2))
        assertTrue(FamiliarImages.isNightTime(5))
        assertTrue(FamiliarImages.isNightTime(9))
    }

    @Test
    fun isNightTime_outsideRange_isDay() {
        assertFalse(FamiliarImages.isNightTime(1))
        assertFalse(FamiliarImages.isNightTime(10))
        assertFalse(FamiliarImages.isNightTime(23))
    }

    @Test
    fun variantForHour_night_returnsSingleNightVariant() {
        assertEquals("night", FamiliarImages.variantForHour(3))
    }

    @Test
    fun variantForHour_day_cyclesThroughThreeVariants() {
        assertEquals("day2", FamiliarImages.variantForHour(10))
        assertEquals("day3", FamiliarImages.variantForHour(11))
        assertEquals("day1", FamiliarImages.variantForHour(12))
    }

    @Test
    fun variantForHour_everyHour_producesKnownVariant() {
        val known = setOf("day1", "day2", "day3", "night")
        (0..23).forEach { hour ->
            assertTrue(
                "час $hour дал неизвестный вариант",
                FamiliarImages.variantForHour(hour) in known
            )
        }
    }

    @Test
    fun urlFor_withoutCatalog_returnsNull() {
        // Каталог в юнит-тестах не загружен: URL собрать не из чего, но падать нельзя.
        assertEquals(null, FamiliarImages.urlFor("familiar_fox", "day1"))
    }
}
