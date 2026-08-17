package bas.app.shift.models

import com.google.gson.annotations.SerializedName

/**
 * Ответ каталога фамильяров: GET /familiars_api/api/v1/familiars.
 * Картинки лежат статикой, клиент собирает URL сам из base_url + id + variant + image_version.
 */
data class FamiliarCatalogResponse(
    @SerializedName("version") val version: String = "",
    @SerializedName("base_url") val baseUrl: String = "",
    @SerializedName("familiars") val familiars: List<FamiliarInfo> = emptyList()
)

data class FamiliarInfo(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("description") val description: String? = null,
    /** "creature" — обычный фамильяр; "player" — фамильяра играет живой человек. */
    @SerializedName("kind") val kind: String = KIND_CREATURE,
    /** Доступные картинки: day1/day2/day3/night. Пусто — картинок нет, показываем плейсхолдер. */
    @SerializedName("variants") val variants: List<String> = emptyList(),
    @SerializedName("image_version") val imageVersion: String = "1",
    /** Показывать ли в выборе фамильяра. Кастомные одноразовые — false. */
    @SerializedName("is_listed") val isListed: Boolean = true,
    @SerializedName("sort_order") val sortOrder: Int = 0
) {
    val isPlayer: Boolean get() = kind == KIND_PLAYER
    val hasArtwork: Boolean get() = variants.isNotEmpty()

    companion object {
        const val KIND_CREATURE = "creature"
        const val KIND_PLAYER = "player"
    }
}
