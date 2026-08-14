package bas.app.shift.models

import org.junit.Assert.assertEquals
import org.junit.Test

class EnumFromServerValueTest {

    @Test
    fun pointType_knownValue_returnsMatchingConstant() {
        assertEquals(PointType.USER, PointType.fromServerValue("USER"))
        assertEquals(PointType.SHRINKING_CIRCLE, PointType.fromServerValue("SHRINKING_CIRCLE"))
    }

    @Test
    fun pointType_unknownValue_fallsBackToUnknown() {
        assertEquals(PointType.UNKNOWN, PointType.fromServerValue("SOME_NEW_TYPE"))
    }

    @Test
    fun pointType_caseMismatch_fallsBackToUnknown() {
        assertEquals(PointType.UNKNOWN, PointType.fromServerValue("user"))
    }

    @Test
    fun auraType_knownValue_returnsMatchingConstant() {
        assertEquals(AuraType.HUMAN, AuraType.fromServerValue("human"))
        assertEquals(AuraType.DEMON, AuraType.fromServerValue("demon"))
    }

    @Test
    fun auraType_unknownValue_fallsBackToOther() {
        assertEquals(AuraType.OTHER, AuraType.fromServerValue("something_new"))
    }

    @Test
    fun auraMarkType_knownValue_returnsMatchingConstant() {
        assertEquals(AuraMarkType.CURSE, AuraMarkType.fromServerValue("CURSE"))
        assertEquals(AuraMarkType.FAMILIAR_LINK, AuraMarkType.fromServerValue("FAMILIAR_LINK"))
    }

    @Test
    fun auraMarkType_unknownValue_fallsBackToMagicDiscipline() {
        assertEquals(AuraMarkType.MAGIC_DISCIPLINE, AuraMarkType.fromServerValue("NOT_A_REAL_MARK"))
    }

    @Test
    fun auraProblemType_knownValue_returnsMatchingConstant() {
        assertEquals(AuraProblemType.HOLE, AuraProblemType.fromServerValue("HOLE"))
        assertEquals(AuraProblemType.PARASITE, AuraProblemType.fromServerValue("PARASITE"))
    }

    @Test
    fun auraProblemType_unknownValue_fallsBackToOther() {
        assertEquals(AuraProblemType.OTHER, AuraProblemType.fromServerValue("NOT_A_REAL_PROBLEM"))
    }
}
