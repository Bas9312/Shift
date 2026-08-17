package bas.app.shift.api

import bas.app.shift.models.FamiliarCatalogResponse
import retrofit2.Response
import retrofit2.http.GET

interface FamiliarApi {
    /**
     * Абсолютный URL, а не путь от baseUrl: каталог и статика фамильяров работают по https,
     * тогда как общий RetrofitClient.BASE_URL пока http.
     */
    @GET("https://shift96.ru/familiars_api/api/v1/familiars")
    suspend fun getFamiliars(): Response<FamiliarCatalogResponse>
}
