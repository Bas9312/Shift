package bas.app.shift.models

enum class AuraProblemType(val serverValue: String) {
    HOLE("HOLE"),
    TEAR("TEAR"),
    SCAR("SCAR"),
    PARASITE("PARASITE"),
    OTHER("OTHER");

    companion object {
        fun fromServerValue(value: String): AuraProblemType =
            values().find { it.serverValue == value } ?: OTHER
    }
} 