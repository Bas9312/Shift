package bas.app.shift.models

import com.google.gson.annotations.SerializedName

data class Familiar(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String
)

object FamiliarData {
    val familiars = mapOf(
        "" to "Нет фамильяра",
        "familiar_mirror" to "Осколок зеркала",
        "familiar_vaynera_spirit" to "Дух улицы Вайнера",
        "familiar_weird_compass" to "Компас \"туда где странно\"",
        "familiar_gentlemans_tear" to "Кубок \"Слеза джентльмена\"",
        "familiar_dobyvala" to "Добывала",
        "familiar_abyss_eater" to "Зев бездны",
        "familiar_earth_cat" to "Земляная кошка",
        "familiar_malachite_lizard" to "Малахитовая ящерица",
        "familiar_fox" to "Чудо-лиса"
    )
    
    fun getNameById(id: String): String {
        return familiars[id] ?: "Неизвестный фамильяр"
    }
    
    fun getImageNameById(id: String, imageIndex: Int = 1): String {
        return if (imageIndex == 1) id else "${id}${imageIndex}"
    }
    
    fun getImageNameByIdWithTime(id: String, imageIndex: Int = 1, isNight: Boolean = false): String {
        val baseName = if (imageIndex == 1) id else "${id}${imageIndex}"
        return if (isNight) "${baseName}_night" else baseName
    }
    
    fun isNightTime(currentHour: Int): Boolean {
        // Ночное время с 2:00 до 10:00
        return currentHour >= 2 && currentHour < 10
    }
}
