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

    private val messagesRetrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val shiftApi: ShiftApi = retrofit.create(ShiftApi::class.java)
    val auraApi: AuraApi = retrofit.create(AuraApi::class.java)
    val userProfileApi: UserProfileApi = retrofit.create(UserProfileApi::class.java)
    val artifactApi: ArtifactApi = retrofit.create(ArtifactApi::class.java)
    val effectApi: EffectApi = retrofit.create(EffectApi::class.java)
    val noiseApi: NoiseApi = retrofit.create(NoiseApi::class.java)
    val chatApi: ChatApi = chatRetrofit.create(ChatApi::class.java)
    val messagesApi: MessagesApi = messagesRetrofit.create(MessagesApi::class.java)
    
    // Wikipedia API с отдельным OkHttpClient и User-Agent
    private val wikipediaOkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "ShiftApp/1.0 (https://shift96.ru; support@shift96.ru) okhttp")
                .build()
            chain.proceed(request)
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val wikipediaRetrofit = Retrofit.Builder()
        .baseUrl("https://ru.wikipedia.org/")
        .client(wikipediaOkHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val wikipediaApi: WikipediaApi = wikipediaRetrofit.create(WikipediaApi::class.java)
}