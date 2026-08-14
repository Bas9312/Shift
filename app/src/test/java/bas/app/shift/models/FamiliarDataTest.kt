package bas.app.shift.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FamiliarDataTest {

    @Test
    fun getNameById_knownId_returnsRussianName() {
        assertEquals("Осколок зеркала", FamiliarData.getNameById("familiar_mirror"))
    }

    @Test
    fun getNameById_emptyId_returnsNoFamiliarLabel() {
        assertEquals("Нет фамильяра", FamiliarData.getNameById(""))
    }

    @Test
    fun getNameById_unknownId_fallsBackToIdItself() {
        assertEquals("familiar_unknown_xyz", FamiliarData.getNameById("familiar_unknown_xyz"))
    }

    @Test
    fun getImageNameById_defaultIndex_returnsBareId() {
        assertEquals("familiar_fox", FamiliarData.getImageNameById("familiar_fox"))
    }

    @Test
    fun getImageNameById_explicitIndexOne_returnsBareId() {
        assertEquals("familiar_fox", FamiliarData.getImageNameById("familiar_fox", 1))
    }

    @Test
    fun getImageNameById_indexTwoOrMore_appendsIndexSuffix() {
        assertEquals("familiar_fox2", FamiliarData.getImageNameById("familiar_fox", 2))
        assertEquals("familiar_fox3", FamiliarData.getImageNameById("familiar_fox", 3))
    }

    @Test
    fun getImageNameByIdWithTime_dayIndexOne_returnsBareId() {
        assertEquals("familiar_fox", FamiliarData.getImageNameByIdWithTime("familiar_fox", 1, isNight = false))
    }

    @Test
    fun getImageNameByIdWithTime_nightIndexOne_appendsNightSuffix() {
        assertEquals("familiar_fox_night", FamiliarData.getImageNameByIdWithTime("familiar_fox", 1, isNight = true))
    }

    @Test
    fun getImageNameByIdWithTime_nightIndexTwo_appendsIndexThenNightSuffix() {
        assertEquals("familiar_fox2_night", FamiliarData.getImageNameByIdWithTime("familiar_fox", 2, isNight = true))
    }

    @Test
    fun isNightTime_beforeWindow_returnsFalse() {
        assertFalse(FamiliarData.isNightTime(0))
        assertFalse(FamiliarData.isNightTime(1))
    }

    @Test
    fun isNightTime_windowStartInclusive_returnsTrue() {
        assertTrue(FamiliarData.isNightTime(2))
    }

    @Test
    fun isNightTime_withinWindow_returnsTrue() {
        assertTrue(FamiliarData.isNightTime(5))
        assertTrue(FamiliarData.isNightTime(9))
    }

    @Test
    fun isNightTime_windowEndExclusive_returnsFalse() {
        assertFalse(FamiliarData.isNightTime(10))
    }

    @Test
    fun isNightTime_afterWindow_returnsFalse() {
        assertFalse(FamiliarData.isNightTime(15))
        assertFalse(FamiliarData.isNightTime(23))
    }
}
