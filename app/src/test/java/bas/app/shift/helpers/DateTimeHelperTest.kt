package bas.app.shift.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class DateTimeHelperTest {

    @Test
    fun formatMessageTime_validTimestamp_returnsHoursMinutes() {
        assertEquals("14:35", DateTimeHelper.formatMessageTime("2026-08-11 14:35:07"))
    }

    @Test
    fun formatMessageTime_midnight_returnsHoursMinutes() {
        assertEquals("00:00", DateTimeHelper.formatMessageTime("2026-01-01 00:00:00"))
    }

    @Test
    fun formatMessageTime_unparseable_returnsOriginalString() {
        assertEquals("not-a-date", DateTimeHelper.formatMessageTime("not-a-date"))
    }

    @Test
    fun formatMessageTime_empty_returnsEmptyString() {
        assertEquals("", DateTimeHelper.formatMessageTime(""))
    }
}
