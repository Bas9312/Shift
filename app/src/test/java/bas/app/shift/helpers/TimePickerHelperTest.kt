package bas.app.shift.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimePickerHelperTest {

    @Test
    fun formatTime_zero_returnsEmptyString() {
        assertEquals("", TimePickerHelper.formatTime(0, 0))
    }

    @Test
    fun formatTime_hoursOnly() {
        assertEquals("2ч", TimePickerHelper.formatTime(2, 0))
    }

    @Test
    fun formatTime_minutesOnly() {
        assertEquals("30м", TimePickerHelper.formatTime(0, 30))
    }

    @Test
    fun formatTime_hoursAndMinutes() {
        assertEquals("2ч 30м", TimePickerHelper.formatTime(2, 30))
    }

    @Test
    fun parseTimeToMinutes_hoursAndMinutes() {
        assertEquals(150, TimePickerHelper.parseTimeToMinutes("2ч 30м"))
    }

    @Test
    fun parseTimeToMinutes_hoursOnly() {
        assertEquals(120, TimePickerHelper.parseTimeToMinutes("2ч"))
    }

    @Test
    fun parseTimeToMinutes_minutesOnly() {
        assertEquals(30, TimePickerHelper.parseTimeToMinutes("30м"))
    }

    @Test
    fun parseTimeToMinutes_blankOrUnparseable_returnsNull() {
        assertNull(TimePickerHelper.parseTimeToMinutes(""))
        assertNull(TimePickerHelper.parseTimeToMinutes("   "))
        assertNull(TimePickerHelper.parseTimeToMinutes("garbage"))
    }

    @Test
    fun roundTrip_formatThenParse_recoversTotalMinutes() {
        val formatted = TimePickerHelper.formatTime(2, 30)
        assertEquals(150, TimePickerHelper.parseTimeToMinutes(formatted))
    }
}
