package bas.app.shift.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class UserRolesTest {

    @Test
    fun isMg_mgPrefixedId_returnsTrue() {
        assertEquals(true, UserRoles.isMg("MG_Bas"))
    }

    @Test
    fun isMg_plainId_returnsFalse() {
        assertEquals(false, UserRoles.isMg("Bas"))
    }

    @Test
    fun isMg_null_returnsFalse() {
        assertEquals(false, UserRoles.isMg(null))
    }

    @Test
    fun isMg_empty_returnsFalse() {
        assertEquals(false, UserRoles.isMg(""))
    }

    @Test
    fun isMg_prefixInMiddle_returnsFalse() {
        assertEquals(false, UserRoles.isMg("Bas_MG_"))
    }
}
