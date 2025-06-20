package bas.app.shift.models

enum class AuraMarkType(val serverValue: String) {
    // Внутренние
    MAGIC_DISCIPLINE("MAGIC_DISCIPLINE"),
    BLESSING("BLESSING"),
    CURSE("CURSE"),
    JUDGE_STATUS("JUDGE_STATUS"),
    CONTRACT_BREACH("CONTRACT_BREACH"),
    INSTRUMENT_LINK("INSTRUMENT_LINK"),
    SPIRITUAL_BEING_INSIDE("SPIRITUAL_BEING_INSIDE"),
    // Внешние
    MAGIC_CONTRACT("MAGIC_CONTRACT"),
    FAMILIAR_LINK("FAMILIAR_LINK"),
    MAGIC_LINK("MAGIC_LINK"),
    ARTIFACT_LINK("ARTIFACT_LINK"),
    FOREIGN_PLANE_INFLUENCE("FOREIGN_PLANE_INFLUENCE");

    companion object {
        fun fromServerValue(value: String): AuraMarkType =
            values().find { it.serverValue == value } ?: MAGIC_DISCIPLINE
    }
} 