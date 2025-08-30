package bas.app.shift.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import bas.app.shift.models.AuraTypeAdapter
import bas.app.shift.models.AuraProblemTypeAdapter
import com.google.gson.GsonBuilder

object RetrofitClient {
    private const val BASE_URL = "http://shift96.ru/" // Основной URL
    private const val CHAT_BASE_URL = "http://91.184.253.175/" // URL для чата с фамильяром

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = GsonBuilder()
        .registerTypeAdapter(bas.app.shift.models.AuraType::class.java, AuraTypeAdapter())
        .registerTypeAdapter(bas.app.shift.models.AuraProblemType::class.java, AuraProblemTypeAdapter())
        .create()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    private val chatRetrofit = Retrofit.Builder()
        .baseUrl(CHAT_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val shiftApi: ShiftApi = retrofit.create(ShiftApi::class.java)
    val auraApi: AuraApi = retrofit.create(AuraApi::class.java)
    val userProfileApi: UserProfileApi = retrofit.create(UserProfileApi::class.java)
    val artifactApi: ArtifactApi = retrofit.create(ArtifactApi::class.java)
    val chatApi: ChatApi = chatRetrofit.create(ChatApi::class.java)
}