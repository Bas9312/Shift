package bas.app.shift.api

import bas.app.shift.models.WikipediaResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Кусочек Wikipedia API, нужный для «Шести кликов».
 *
 * Все методы suspend: загадка собирается прогулкой по ссылкам, а это цепочка запросов,
 * которую на колбэках читать невозможно.
 */
interface WikipediaApi {

    /** Случайные статьи из основного пространства имён — стартовая точка прогулки. */
    @GET("w/api.php")
    suspend fun randomPages(
        @Query("grnlimit") limit: Int = 4,
        @Query("action") action: String = "query",
        @Query("generator") generator: String = "random",
        @Query("grnnamespace") grnNamespace: String = "0",
        @Query("grnfilterredir") grnFilterRedir: String = "nonredirects",
        @Query("prop") prop: String = "info|pageprops",
        @Query("inprop") inProp: String = "url",
        @Query("format") format: String = "json",
        @Query("formatversion") formatVersion: String = "2",
        @Query("origin") origin: String = "*",
    ): WikipediaResponse

    /**
     * Куда со страницы можно уйти по ссылке.
     *
     * `generator=links` возвращает сами страницы-цели, а не просто их названия, поэтому
     * сразу видно, существует статья или это красная ссылка (`missing`), и не дизамбиг ли
     * это. `redirects=1` доводит цели до канонических названий — то есть до тех, которые
     * игрок увидит в адресной строке, когда перейдёт по ссылке.
     */
    @GET("w/api.php")
    suspend fun outgoingLinks(
        @Query("titles") title: String,
        @Query("action") action: String = "query",
        @Query("generator") generator: String = "links",
        @Query("gplnamespace") gplNamespace: String = "0",
        @Query("gpllimit") gplLimit: String = "max",
        @Query("prop") prop: String = "info|pageprops",
        @Query("inprop") inProp: String = "url",
        @Query("redirects") redirects: String = "1",
        @Query("format") format: String = "json",
        @Query("formatversion") formatVersion: String = "2",
        @Query("origin") origin: String = "*",
    ): WikipediaResponse

    /**
     * Канонические названия для того, что ввёл игрок, плюс все редиректы, ведущие на них.
     *
     * Редиректы нужны, потому что на странице ссылка часто написана коротким именем
     * («СССР»), а игрок, перейдя по ней, видит и вводит каноническое
     * («Союз Советских Социалистических Республик»). Без списка редиректов честный путь
     * не засчитывался бы.
     */
    @GET("w/api.php")
    suspend fun resolveTitles(
        @Query("titles") titles: String,
        @Query("action") action: String = "query",
        @Query("prop") prop: String = "info|redirects",
        @Query("inprop") inProp: String = "url",
        @Query("rdnamespace") rdNamespace: String = "0",
        @Query("rdlimit") rdLimit: String = "max",
        @Query("redirects") redirects: String = "1",
        @Query("format") format: String = "json",
        @Query("formatversion") formatVersion: String = "2",
        @Query("origin") origin: String = "*",
    ): WikipediaResponse

    /**
     * Есть ли со страницы `title` ссылка на что-нибудь из `plTitles`.
     *
     * `pltitles` фильтрует список ссылок на сервере, поэтому в ответе приходит либо пусто,
     * либо ровно те названия, которые нас интересуют.
     */
    @GET("w/api.php")
    suspend fun linksTo(
        @Query("titles") title: String,
        @Query("pltitles") plTitles: String,
        @Query("action") action: String = "query",
        @Query("prop") prop: String = "links",
        @Query("plnamespace") plNamespace: String = "0",
        @Query("pllimit") plLimit: String = "max",
        @Query("redirects") redirects: String = "1",
        @Query("format") format: String = "json",
        @Query("formatversion") formatVersion: String = "2",
        @Query("origin") origin: String = "*",
    ): WikipediaResponse
}
