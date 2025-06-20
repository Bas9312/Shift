package bas.app.shift.models

enum class AuraType(val serverValue: String) {
    HUMAN("human"),
    MAGE("mage"),
    CREATURE_OF_SPIRIT_WORLD("creature_of_spirit_world"),
    CREATURE_OF_ABYSS("creature_of_abyss"),
    CREATURE_OF_MYTH("creature_of_myth"),
    CREATURE_OF_REALITY("creature_of_reality"),
    DEMON("demon"),
    ANGEL("angel"),
    OTHER("other");

    companion object {
        fun fromServerValue(value: String): AuraType =
            values().find { it.serverValue == value } ?: OTHER
    }
} 