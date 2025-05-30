package bas.app.shift.models

enum class PointType(val serverValue: String) {
    USER("USER"),
    FAMILIAR("FAMILIAR"),
    HIDDEN_EFFECT_AREA("HIDDEN_EFFECT_AREA"),
    FAKE_FAMILIAR_BITER("FAKE_FAMILIAR_BITER"),
    APPROACHING_BITER("APPROACHING_BITER"),
    OPEN_PROBLEM("OPEN_PROBLEM"),
    SHRINKING_CIRCLE("SHRINKING_CIRCLE"),
    DEMON_BLACK_CIRCLE("DEMON_BLACK_CIRCLE"),
    APPROACHING_VIRTUAL("APPROACHING_VIRTUAL"),
    HIDDEN_AR_POINT("HIDDEN_AR_POINT");

    companion object {
        fun fromServerValue(value: String): PointType {
            return values().find { it.serverValue == value } ?: USER
        }
    }
}