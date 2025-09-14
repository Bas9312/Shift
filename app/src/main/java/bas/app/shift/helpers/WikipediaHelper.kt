package bas.app.shift.helpers

import android.content.Context
import android.content.SharedPreferences
import bas.app.shift.api.RetrofitClient
import bas.app.shift.models.WikipediaPage
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object WikipediaHelper {
    private const val PREFS_NAME = "wikipedia_upgrade"
    private const val KEY_LAST_UPGRADE_TIME = "last_upgrade_time"
    private const val UPGRADE_COOLDOWN_HOURS = 4
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun canUseUpgrade(context: Context): Boolean {
        val prefs = getPrefs(context)
        val lastUpgradeTime = prefs.getLong(KEY_LAST_UPGRADE_TIME, 0)
        val currentTime = System.currentTimeMillis()
        val cooldownMs = UPGRADE_COOLDOWN_HOURS * 60 * 60 * 1000L
        
        return (currentTime - lastUpgradeTime) >= cooldownMs
    }
    
    fun markUpgradeUsed(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().putLong(KEY_LAST_UPGRADE_TIME, System.currentTimeMillis()).apply()
    }
    
    fun getTimeUntilNextUpgrade(context: Context): Long {
        val prefs = getPrefs(context)
        val lastUpgradeTime = prefs.getLong(KEY_LAST_UPGRADE_TIME, 0)
        val currentTime = System.currentTimeMillis()
        val cooldownMs = UPGRADE_COOLDOWN_HOURS * 60 * 60 * 1000L
        
        val timePassed = currentTime - lastUpgradeTime
        return if (timePassed >= cooldownMs) 0 else cooldownMs - timePassed
    }
    
    fun getRandomPages(
        onSuccess: (startPage: WikipediaPage, finishPage: WikipediaPage) -> Unit,
        onError: (String) -> Unit
    ) {
        RetrofitClient.wikipediaApi.getRandomPages()
            .enqueue(object : Callback<bas.app.shift.models.WikipediaResponse> {
                override fun onResponse(
                    call: Call<bas.app.shift.models.WikipediaResponse>,
                    response: Response<bas.app.shift.models.WikipediaResponse>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        val wikipediaResponse = response.body()!!
                        val pages = wikipediaResponse.query.pages
                        
                        // Фильтруем страницы без дизамбигов
                        val validPages = pages.filter { page ->
                            page.pageProps?.disambiguation == null
                        }
                        
                        if (validPages.size >= 2) {
                            val startPage = validPages[0]
                            val finishPage = validPages[1]
                            onSuccess(startPage, finishPage)
                        } else {
                            // Если попались дизамбиги, делаем повторный запрос
                            getRandomPages(onSuccess, onError)
                        }
                    } else {
                        onError("Ошибка получения страниц: ${response.code()}")
                    }
                }
                
                override fun onFailure(call: Call<bas.app.shift.models.WikipediaResponse>, t: Throwable) {
                    onError("Ошибка сети: ${t.message}")
                }
            })
    }
}
