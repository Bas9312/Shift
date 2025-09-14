package bas.app.shift.api

import bas.app.shift.models.WikipediaResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface WikipediaApi {
    @GET("w/api.php")
    fun getRandomPages(
        @Query("action") action: String = "query",
        @Query("generator") generator: String = "random",
        @Query("grnnamespace") grnNamespace: String = "0",
        @Query("grnfilterredir") grnFilterRedir: String = "nonredirects",
        @Query("grnlimit") grnLimit: String = "2",
        @Query("prop") prop: String = "info|pageprops",
        @Query("inprop") inProp: String = "url",
        @Query("format") format: String = "json",
        @Query("formatversion") formatVersion: String = "2",
        @Query("origin") origin: String = "*"
    ): Call<WikipediaResponse>
}
