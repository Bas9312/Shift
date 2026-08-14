package bas.app.shift.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayNamesTest {

    @Test
    fun combine_bothPresent_joinsCharacterThenPlayer() {
        assertEquals("Ворон / Bas", DisplayNames.combine("Ворон", "Bas", "fallback"))
    }

    @Test
    fun combine_onlyCharacterPresent_returnsCharacter() {
        assertEquals("Ворон", DisplayNames.combine("Ворон", null, "fallback"))
        assertEquals("Ворон", DisplayNames.combine("Ворон", "", "fallback"))
    }

    @Test
    fun combine_onlyPlayerPresent_returnsPlayer() {
        assertEquals("Bas", DisplayNames.combine(null, "Bas", "fallback"))
        assertEquals("Bas", DisplayNames.combine("", "Bas", "fallback"))
    }

    @Test
    fun combine_neitherPresent_returnsFallback() {
        assertEquals("fallback", DisplayNames.combine(null, null, "fallback"))
        assertEquals("fallback", DisplayNames.combine("", "", "fallback"))
    }

    @Test
    fun combinePlayerFirst_bothPresent_joinsPlayerThenCharacter() {
        assertEquals("Bas / Ворон", DisplayNames.combinePlayerFirst("Bas", "Ворон", "fallback"))
    }

    @Test
    fun combinePlayerFirst_onlyOnePresent_returnsThatOne() {
        assertEquals("Bas", DisplayNames.combinePlayerFirst("Bas", null, "fallback"))
        assertEquals("Ворон", DisplayNames.combinePlayerFirst(null, "Ворон", "fallback"))
    }

    @Test
    fun combinePlayerFirst_neitherPresent_returnsFallback() {
        assertEquals("fallback", DisplayNames.combinePlayerFirst(null, null, "fallback"))
    }
}
