package bas.app.shift.helpers

import android.content.Context
import bas.app.shift.api.RetrofitClient
import bas.app.shift.models.FamiliarCatalogResponse
import bas.app.shift.models.FamiliarInfo
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Каталог фамильяров. Раньше список жил хардкодом в models/FamiliarData, теперь приезжает
 * с сервера — новый фамильяр можно добавить без релиза APK.
 *
 * Источники по убыванию свежести: память -> файл в filesDir -> бутстрап из assets.
 * Сеть только обновляет кеш, показ никогда её не ждёт: в поле связи может не быть,
 * а имя фамильяра игрок должен видеть всегда.
 */
object FamiliarCatalog {

    private const val CACHE_FILE = "familiars.json"
    private const val BOOTSTRAP_ASSET = "familiars_bootstrap.json"

    private val gson = Gson()

    @Volatile
    private var catalog: FamiliarCatalogResponse? = null

    /** Текущий снимок каталога. Только память, безопасно дёргать из UI. */
    fun snapshot(): FamiliarCatalogResponse? = catalog

    fun get(id: String?): FamiliarInfo? {
        if (id.isNullOrEmpty()) return null
        return catalog?.familiars?.firstOrNull { it.id == id }
    }

    /** Имя для показа. Неизвестный id отдаём как есть — лучше id на экране, чем пустота. */
    fun getName(id: String?): String {
        if (id.isNullOrEmpty()) return ""
        return get(id)?.name ?: id
    }

    /** Что предлагать в выборе фамильяра: только видимые, плюс пункт «нет фамильяра» сверху. */
    fun listed(): List<FamiliarInfo> =
        catalog?.familiars?.filter { it.isListed }?.sortedBy { it.sortOrder } ?: emptyList()

    /**
     * Синхронная загрузка из локальных источников. Вызывается один раз из ShiftApplication:
     * файл маленький (единицы килобайт), зато дальше весь UI читает только память и
     * не ловит гонку «экран отрисовался раньше, чем каталог доехал».
     */
    fun loadLocal(context: Context) {
        if (catalog != null) return
        catalog = readCacheFile(context) ?: readBootstrap(context)
        LogHelper.d("FamiliarCatalog: локальная загрузка, записей=${catalog?.familiars?.size ?: 0}")
    }

    /**
     * Обновление с сервера. Ошибки не пробрасываем: неудачное обновление — не повод
     * ломать экран, останется предыдущий каталог.
     */
    suspend fun refresh(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = RetrofitClient.familiarApi.getFamiliars()
            val body = response.body()
            if (!response.isSuccessful || body == null || body.familiars.isEmpty()) {
                LogHelper.e("FamiliarCatalog: обновление не удалось, код=${response.code()}")
                return@withContext false
            }
            catalog = body
            runCatching { cacheFile(context).writeText(gson.toJson(body)) }
                .onFailure { LogHelper.e("FamiliarCatalog: не смог записать кеш: ${it.message}") }
            LogHelper.d("FamiliarCatalog: обновлён, версия=${body.version}, записей=${body.familiars.size}")
            true
        } catch (e: Exception) {
            LogHelper.e("FamiliarCatalog: ошибка обновления: ${e.message}")
            false
        }
    }

    private fun cacheFile(context: Context) = File(context.filesDir, CACHE_FILE)

    private fun readCacheFile(context: Context): FamiliarCatalogResponse? = try {
        val file = cacheFile(context)
        if (file.exists()) parse(file.readText()) else null
    } catch (e: Exception) {
        LogHelper.e("FamiliarCatalog: кеш не читается: ${e.message}")
        null
    }

    private fun readBootstrap(context: Context): FamiliarCatalogResponse? = try {
        parse(context.assets.open(BOOTSTRAP_ASSET).bufferedReader().use { it.readText() })
    } catch (e: Exception) {
        LogHelper.e("FamiliarCatalog: бутстрап не читается: ${e.message}")
        null
    }

    private fun parse(json: String): FamiliarCatalogResponse? =
        gson.fromJson(json, FamiliarCatalogResponse::class.java)
            ?.takeIf { it.familiars.isNotEmpty() && it.baseUrl.isNotEmpty() }
}
