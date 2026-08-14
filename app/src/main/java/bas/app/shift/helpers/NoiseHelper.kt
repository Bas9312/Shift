package bas.app.shift.helpers

import kotlin.math.floor

object NoiseHelper {

    /**
     * Получает уровень шума (0-5) из дробного значения
     * Использует округление вниз: 3.9 -> 3 уровень
     */
    fun getNoiseLevel(noiseValue: Double): Int {
        return floor(noiseValue).toInt().coerceIn(0, 5)
    }

    /**
     * Форматирует дробное значение шума для отображения
     */
    fun formatNoiseValue(noiseValue: Double): String {
        return String.format("%.1f", noiseValue)
    }

    /**
     * Получает процент заполнения шкалы для уровня
     * Например: 3.3 -> 30% заполнения для 3-го уровня
     */
    fun getLevelProgress(noiseValue: Double): Float {
        val level = getNoiseLevel(noiseValue)
        val fractionalPart = noiseValue - floor(noiseValue)
        return fractionalPart.toFloat()
    }

    /**
     * Уровни эффектов (3-5), пересечённые при изменении шума с oldLevel на newLevel.
     * Возвращает все пороги по пути, а не только первый — скачок 0 -> 5 обязан задеть 3, 4 и 5.
     */
    fun thresholdsCrossed(oldLevel: Int, newLevel: Int): List<Int> {
        return (3..5).filter { oldLevel < it && newLevel >= it }
    }

    /** Итог деления дельты шума между пользователем, Proxy-узлом и Cross-Link партнёром. */
    data class NoiseSplit(val proxyDelta: Double, val partnerDelta: Double, val selfDelta: Double)

    /**
     * Считает, как дельта шума `delta` делится между самим пользователем, Proxy-узлом
     * (если активен) и Cross-Link партнёром (если активен и Proxy уже забрал свою долю).
     * Каждый активный эффект отщипывает половину от того, что осталось; себе начисляется
     * остаток ровно один раз. Деление применяется только к положительной дельте.
     */
    fun calculateNoiseSplit(delta: Double, hasProxyEffect: Boolean, hasCrossLinkEffect: Boolean): NoiseSplit {
        var selfDelta = delta
        var proxyDelta = 0.0
        var partnerDelta = 0.0

        if (hasProxyEffect && selfDelta > 0) {
            proxyDelta = selfDelta / 2.0
            selfDelta -= proxyDelta
        }

        if (hasCrossLinkEffect && selfDelta > 0) {
            partnerDelta = selfDelta / 2.0
            selfDelta -= partnerDelta
        }

        return NoiseSplit(proxyDelta, partnerDelta, selfDelta)
    }
}
