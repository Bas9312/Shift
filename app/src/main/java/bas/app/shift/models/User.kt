package bas.app.shift.models

import com.google.gson.annotations.SerializedName

// Дисциплина или модуль
data class NamedEntity(
    val id: Int,
    val name: String
)

// Артефакт (базовая модель для списка)
data class ShortArtifact(
    val id: Int,
    val name: String
)

// Основной профиль пользователя
data class User(
    @SerializedName("userId") val userId: String,
    @SerializedName("player_name") val playerName: String?,
    @SerializedName("name") val characterName: String?,
    @SerializedName("disciplines") val disciplines: List<NamedEntity> = emptyList(),
    @SerializedName("modules") val modules: List<NamedEntity> = emptyList(),
    @SerializedName("abilities") val abilities: List<String> = emptyList(),
    @SerializedName("artifacts") val artifacts: List<ShortArtifact> = emptyList(),
    @SerializedName("instrument") val instrument: String?,
    @SerializedName("familiar") val familiar: String?,
    @SerializedName("misc") val misc: List<String> = emptyList()
) 