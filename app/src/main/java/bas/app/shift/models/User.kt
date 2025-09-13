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

// Способность с ID
data class Ability(
    val id: Int,
    val type: String,
    val description: String
)

// Модель для обновления профиля (с ID для отправки на сервер)
data class UserUpdateRequest(
    val disciplines: List<Int>? = null,
    val modules: List<Int>? = null,
    val abilities: List<Int>? = null,
    val instrument: String? = null,
    val familiar: String? = null,
    val misc: List<String>? = null
)

// Модель для получения профиля с сервера (с объектами)
data class User(
    @SerializedName("userId") val userId: String,
    @SerializedName("player_name") val playerName: String,
    @SerializedName("name") val characterName: String,
    @SerializedName("disciplines") val disciplines: List<NamedEntity> = emptyList(),
    @SerializedName("modules") val modules: List<NamedEntity> = emptyList(),
    @SerializedName("abilities") val abilities: List<Ability> = emptyList(),
    @SerializedName("artifacts") val artifacts: List<ShortArtifact> = emptyList(),
    @SerializedName("instrument") val instrument: String?,
    @SerializedName("familiar") val familiar: String?,
    @SerializedName("misc") val misc: List<String> = emptyList(),
    @SerializedName("showUser") val showUser: Boolean = true,
    @SerializedName("lastUpdate") val lastUpdate: String? = null,
    val type: AuraType,
    val effects: List<Effect> = emptyList()
)

// Модель для получения профиля с сервера (с ID массивами)
data class ShortUser(
    @SerializedName("userId") val userId: String,
    @SerializedName("player_name") val playerName: String,
    @SerializedName("name") val characterName: String,
)