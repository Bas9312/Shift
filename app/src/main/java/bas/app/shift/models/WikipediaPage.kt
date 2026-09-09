package bas.app.shift.models

import com.google.gson.annotations.SerializedName

/**
 * Одна страница в ответе Wikipedia API.
 *
 * Почти всё nullable намеренно: один и тот же формат ответа приходит на разные запросы.
 * У страницы-цели ссылки может не быть `pageid` (красная ссылка — тогда стоит `missing`),
 * у страницы из `generator=random` не бывает `links`, и так далее. Gson молча кладёт null
 * в non-null поле Kotlin, и падает потом в неожиданном месте, поэтому лучше честно.
 */
data class WikipediaPage(
    @SerializedName("pageid") val pageId: Int? = null,
    @SerializedName("ns") val namespace: Int? = null,
    @SerializedName("title") val title: String = "",
    @SerializedName("fullurl") val fullUrl: String? = null,
    @SerializedName("missing") val missing: Boolean = false,
    @SerializedName("pageprops") val pageProps: PageProps? = null,
    /** Исходящие ссылки (`prop=links`). */
    @SerializedName("links") val links: List<WikipediaTitleRef>? = null,
    /** Редиректы, ведущие НА эту страницу (`prop=redirects`). */
    @SerializedName("redirects") val redirects: List<WikipediaTitleRef>? = null,
)

data class PageProps(
    @SerializedName("disambiguation") val disambiguation: String? = null
)

/** Ссылка или редирект — из полезного только название. */
data class WikipediaTitleRef(
    @SerializedName("title") val title: String = ""
)

/**
 * Пара «как спросили» → «как называется на самом деле». Wikipedia отдаёт такие пары
 * в `normalized` (поправила регистр и подчёркивания) и в `redirects` (перекинула на цель).
 */
data class WikipediaTitleMap(
    @SerializedName("from") val from: String = "",
    @SerializedName("to") val to: String = "",
)

data class WikipediaQuery(
    @SerializedName("pages") val pages: List<WikipediaPage>? = null,
    @SerializedName("normalized") val normalized: List<WikipediaTitleMap>? = null,
    @SerializedName("redirects") val redirects: List<WikipediaTitleMap>? = null,
)

data class WikipediaResponse(
    @SerializedName("query") val query: WikipediaQuery? = null
)
