package bas.app.shift.models

import com.google.gson.annotations.SerializedName

data class WikipediaPage(
    @SerializedName("pageid") val pageId: Int,
    @SerializedName("ns") val namespace: Int,
    @SerializedName("title") val title: String,
    @SerializedName("fullurl") val fullUrl: String,
    @SerializedName("pageprops") val pageProps: PageProps? = null
)

data class PageProps(
    @SerializedName("disambiguation") val disambiguation: String? = null
)

data class WikipediaQuery(
    val pages: List<WikipediaPage>
)

data class WikipediaResponse(
    val query: WikipediaQuery
)
