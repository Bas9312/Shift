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

// Модель для получения профиля с сервера (с ID массивами)
data class UserServer(
    @SerializedName("userId") val userId: String,
    @SerializedName("player_name") val playerName: String,
    @SerializedName("name") val characterName: String,
    @SerializedName("disciplines") val disciplines: List<Int> = emptyList(),
    @SerializedName("modules") val modules: List<Int> = emptyList(),
    @SerializedName("abilities") val abilities: List<Int> = emptyList(),
    @SerializedName("artifacts") val artifacts: List<ShortArtifact> = emptyList(),
    @SerializedName("instrument") val instrument: String?,
    @SerializedName("familiar") val familiar: String?,
    @SerializedName("misc") val misc: List<String> = emptyList(),
    @SerializedName("showUser") val showUser: Boolean = true,
    @SerializedName("lastUpdate") val lastUpdate: String? = null
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
    @SerializedName("lastUpdate") val lastUpdate: String? = null
)

// Модель для получения профиля с сервера (с ID массивами)
data class ShortUser(
    @SerializedName("userId") val userId: String,
    @SerializedName("player_name") val playerName: String,
    @SerializedName("name") val characterName: String,
)

// Модель для отображения профиля (с объектами для UI)
data class UserDisplay(
    val userId: String,
    val playerName: String,
    val characterName: String,
    val disciplines: List<NamedEntity> = emptyList(),
    val modules: List<NamedEntity> = emptyList(),
    val abilities: List<Ability> = emptyList(),
    val artifacts: List<ShortArtifact> = emptyList(),
    val instrument: String?,
    val familiar: String?,
    val misc: List<String> = emptyList(),
    val showUser: Boolean = true,
    val lastUpdate: String? = null
)

// Конвертер для преобразования UserServer в User
fun UserServer.toUser(
    allDisciplines: List<NamedEntity> = emptyList(),
    allModules: List<NamedEntity> = emptyList(),
    allAbilities: List<Ability> = emptyList()
): User {
    return User(
        userId = userId,
        playerName = playerName,
        characterName = characterName,
        disciplines = disciplines.mapNotNull { id -> allDisciplines.find { it.id == id } },
        modules = modules.mapNotNull { id -> allModules.find { it.id == id } },
        abilities = abilities.mapNotNull { id -> allAbilities.find { it.id == id } },
        artifacts = artifacts,
        instrument = instrument,
        familiar = familiar,
        misc = misc,
        showUser = showUser,
        lastUpdate = lastUpdate
    )
}

// Конвертер для преобразования User в UserDisplay
fun User.toUserDisplay(): UserDisplay {
    return UserDisplay(
        userId = userId,
        playerName = playerName,
        characterName = characterName,
        disciplines = disciplines,
        modules = modules,
        abilities = abilities,
        artifacts = artifacts,
        instrument = instrument,
        familiar = familiar,
        misc = misc,
        showUser = showUser,
        lastUpdate = lastUpdate
    )
} 