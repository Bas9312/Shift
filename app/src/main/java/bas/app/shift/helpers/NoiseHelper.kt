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
}
