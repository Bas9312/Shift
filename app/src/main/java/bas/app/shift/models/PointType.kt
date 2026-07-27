package bas.app.shift.models

import bas.app.shift.helpers.LogHelper

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
    HIDDEN_AR_POINT("HIDDEN_AR_POINT"),
    POINT_WITH_TEXT("POINT_WITH_TEXT"),
    // Незнакомый серверу тип точки — раньше молча превращался в USER (реального игрока),
    // из-за чего точка теряла круг и маскировалась под другого человека на карте.
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromServerValue(value: String): PointType {
            val match = values().find { it.serverValue == value }
            if (match == null) {
                LogHelper.w("PointType: неизвестный тип точки с сервера: '$value', используется UNKNOWN")
            }
            return match ?: UNKNOWN
        }
    }
}