package bas.app.shift.models

import bas.app.shift.helpers.LogHelper

enum class PointType(val serverValue: String) {
    USER("USER"),
    /** Обычная точка: ведёт себя ровно так, как её настроил мастер в панели МГ. */
    POINT("POINT"),
    FAMILIAR("FAMILIAR"),
    HIDDEN_EFFECT_AREA("HIDDEN_EFFECT_AREA"),
    FAKE_FAMILIAR_BITER("FAKE_FAMILIAR_BITER"),
    APPROACHING_BITER("APPROACHING_BITER"),
    OPEN_PROBLEM("OPEN_PROBLEM"),
    SHRINKING_CIRCLE("SHRINKING_CIRCLE"),
    DEMON_BLACK_CIRCLE("DEMON_BLACK_CIRCLE"),
    APPROACHING_VIRTUAL("APPROACHING_VIRTUAL"),
    // HIDDEN_AR_POINT убран 2026-09-22: дополненной реальности в игре нет и не было,
    // обработчика у типа никогда не существовало, а на карте он выглядел безымянной
    // серой точкой. Старые строки, если всплывут, отрисуются как UNKNOWN.
    POINT_WITH_TEXT("POINT_WITH_TEXT"),
    // Незнакомый серверу тип точки — раньше молча превращался в USER (реального игрока),
    // из-за чего точка теряла круг и маскировалась под другого человека на карте.
    UNKNOWN("UNKNOWN");

    companion object {
        /**
         * Типы, скрытые по своей природе: игрок натыкается на них вслепую и получает
         * уведомление, на карте их нет никогда. Сервер держит у них `hidden = 1` сам
         * (`ALWAYS_HIDDEN_POINT_TYPES` в api_geo/api.php и в панели МГ) — здесь список
         * нужен только чтобы приложение не предлагало мастеру снять галочку, которой всё
         * равно не подчинится.
         */
        val ALWAYS_HIDDEN = setOf(POINT_WITH_TEXT, HIDDEN_EFFECT_AREA)

        fun fromServerValue(value: String): PointType {
            val match = values().find { it.serverValue == value }
            if (match == null) {
                LogHelper.w("PointType: неизвестный тип точки с сервера: '$value', используется UNKNOWN")
            }
            return match ?: UNKNOWN
        }
    }
}