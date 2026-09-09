package bas.app.shift.helpers

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import bas.app.shift.api.RetrofitClient
import bas.app.shift.models.WikipediaPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * «Шесть кликов» — вики-серфинг из `USER.UPGRADE`.
 *
 * Раньше загадка была парой из двух независимых случайных статей, а `USER.UPGRADE.END`
 * принимал любой текст. Обе половины ломались друг об друга: пройти случайную пару почти
 * никогда нельзя, поэтому проверять было и нечего.
 *
 * Теперь загадка собирается прогулкой по настоящим ссылкам — путь заведомо существует,
 * потому что мы сами по нему прошли, — а ответ игрока проверяется по тем же ссылкам.
 */
object WikipediaHelper {
    private const val PREFS_NAME = "wikipedia_upgrade"
    private const val KEY_LAST_UPGRADE_TIME = "last_upgrade_time"
    private const val KEY_START = "puzzle_start"
    private const val KEY_START_URL = "puzzle_start_url"
    private const val KEY_FINISH = "puzzle_finish"
    private const val KEY_FINISH_URL = "puzzle_finish_url"
    private const val UPGRADE_COOLDOWN_HOURS = 4

    /** Правило игры: не больше шести переходов по ссылкам. */
    const val MAX_HOPS = 6

    /**
     * Длина прогулки при генерации. Меньше шести — чтобы у игрока был запас: он может
     * пойти не тем путём, каким шли мы, и всё равно уложиться.
     */
    private val WALK_LENGTH = 3..5

    /** Сколько раз пробуем собрать загадку, если прогулка упёрлась в статью без ссылок. */
    private const val BUILD_ATTEMPTS = 4

    /** Ограничение Wikipedia API: не больше 50 названий в `titles`/`pltitles` за раз. */
    private const val TITLES_PER_REQUEST = 50

    data class Puzzle(
        val start: String,
        val startUrl: String,
        val finish: String,
        val finishUrl: String,
    )

    /** Чем закончилась проверка пути. */
    sealed class Verdict {
        /** [canonicalPath] — тот же путь настоящими названиями статей, без повторов. */
        data class Ok(val canonicalPath: List<String>) : Verdict()

        /** Такой статьи в Википедии нет — скорее всего опечатка. */
        data class NoSuchArticle(val title: String) : Verdict()

        /** Со страницы [from] ссылки на [to] нет. */
        data class BrokenHop(val from: String, val to: String) : Verdict()

        /** Не дозвонились до Википедии — путь не отвергнут, просто не проверен. */
        data class Offline(val reason: String) : Verdict()
    }

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- кулдаун ------------------------------------------------------------

    fun canUseUpgrade(context: Context): Boolean = getTimeUntilNextUpgrade(context) == 0L

    fun markUpgradeUsed(context: Context) {
        getPrefs(context).edit().putLong(KEY_LAST_UPGRADE_TIME, System.currentTimeMillis()).apply()
    }

    fun getTimeUntilNextUpgrade(context: Context): Long {
        val lastUpgradeTime = getPrefs(context).getLong(KEY_LAST_UPGRADE_TIME, 0)
        val cooldownMs = UPGRADE_COOLDOWN_HOURS * 60 * 60 * 1000L
        val timePassed = System.currentTimeMillis() - lastUpgradeTime
        return if (timePassed >= cooldownMs) 0 else cooldownMs - timePassed
    }

    // --- загадка между перезапусками ----------------------------------------

    fun savePuzzle(context: Context, puzzle: Puzzle) {
        getPrefs(context).edit()
            .putString(KEY_START, puzzle.start)
            .putString(KEY_START_URL, puzzle.startUrl)
            .putString(KEY_FINISH, puzzle.finish)
            .putString(KEY_FINISH_URL, puzzle.finishUrl)
            .apply()
    }

    fun loadPuzzle(context: Context): Puzzle? {
        val prefs = getPrefs(context)
        val start = prefs.getString(KEY_START, null) ?: return null
        val finish = prefs.getString(KEY_FINISH, null) ?: return null
        return Puzzle(
            start = start,
            startUrl = prefs.getString(KEY_START_URL, null) ?: articleUrl(start),
            finish = finish,
            finishUrl = prefs.getString(KEY_FINISH_URL, null) ?: articleUrl(finish),
        )
    }

    fun clearPuzzle(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_START).remove(KEY_START_URL)
            .remove(KEY_FINISH).remove(KEY_FINISH_URL)
            .apply()
    }

    // --- сборка загадки ------------------------------------------------------

    /**
     * Берёт случайную статью и уходит от неё по случайным ссылкам на [WALK_LENGTH] шагов.
     * Куда пришли — то и цель. Путь длиной в эти же шаги существует по построению.
     *
     * Возвращает null, если Википедия недоступна или прогулка так и не завелась.
     */
    suspend fun buildPuzzle(): Puzzle? = withContext(Dispatchers.IO) {
        repeat(BUILD_ATTEMPTS) {
            val walked = try {
                walk(WALK_LENGTH.random())
            } catch (e: Exception) {
                return@withContext null
            }
            // Два перехода — минимум, ниже которого загадка вырождается в «найди ссылку».
            if (walked.size >= 3) {
                val start = walked.first()
                val finish = walked.last()
                return@withContext Puzzle(
                    start = start.title,
                    startUrl = start.fullUrl ?: articleUrl(start.title),
                    finish = finish.title,
                    finishUrl = finish.fullUrl ?: articleUrl(finish.title),
                )
            }
        }
        null
    }

    /** Одна прогулка: список пройденных страниц, начиная со случайной стартовой. */
    private suspend fun walk(steps: Int): List<WikipediaPage> {
        val start = RetrofitClient.wikipediaApi.randomPages()
            .query?.pages.orEmpty()
            .firstOrNull { it.isUsableArticle() } ?: return emptyList()

        val path = mutableListOf(start)
        val visited = mutableSetOf(start.title)
        repeat(steps) {
            val next = RetrofitClient.wikipediaApi.outgoingLinks(path.last().title)
                .query?.pages.orEmpty()
                .filter { it.isUsableArticle() && it.title !in visited }
                .randomOrNull() ?: return path
            visited += next.title
            path += next
        }
        return path
    }

    /** Существующая статья основного пространства имён, не страница значений. */
    private fun WikipediaPage.isUsableArticle(): Boolean =
        !missing && pageId != null && namespace == 0 &&
            pageProps?.disambiguation == null && title.isNotBlank()

    // --- проверка ответа игрока ---------------------------------------------

    /**
     * Проверяет, что [typed] — настоящая цепочка ссылок: каждая следующая статья
     * достижима со страницы предыдущей одним кликом.
     *
     * Названия приводятся к каноническим, а на каждом шаге учитываются ещё и редиректы:
     * на странице ссылка может быть написана коротким именем, а игрок вводит то, что
     * увидел после перехода.
     */
    suspend fun verifyPath(typed: List<String>): Verdict = withContext(Dispatchers.IO) {
        try {
            val unique = typed.distinct().take(TITLES_PER_REQUEST)
            val resolved = RetrofitClient.wikipediaApi.resolveTitles(unique.joinToString("|"))
            val query = resolved.query ?: return@withContext Verdict.Offline("пустой ответ Википедии")

            // «Как ввели» → «как называется на самом деле»: сначала правка регистра
            // и подчёркиваний, потом переход по редиректу.
            val normalized = query.normalized.orEmpty().associate { it.from to it.to }
            val redirected = query.redirects.orEmpty().associate { it.from to it.to }
            fun canonicalOf(title: String): String {
                val afterNormalize = normalized[title] ?: title
                return redirected[afterNormalize] ?: afterNormalize
            }

            val pageByTitle = query.pages.orEmpty().associateBy { it.title }
            val canonicalPath = mutableListOf<String>()
            for (title in typed) {
                val canonical = canonicalOf(title)
                val page = pageByTitle[canonical]
                if (page == null || page.missing || page.pageId == null) {
                    return@withContext Verdict.NoSuchArticle(title)
                }
                // Игрок мог перечислить одну и ту же статью дважды — это не переход.
                if (canonicalPath.lastOrNull() != canonical) canonicalPath += canonical
            }
            if (canonicalPath.size < 2) return@withContext Verdict.Ok(canonicalPath)

            // Для каждой цели — она сама плюс редиректы, которыми на неё можно сослаться.
            val variantsOf = canonicalPath.associateWith { title ->
                val redirects = pageByTitle[title]?.redirects.orEmpty().map { it.title }
                (listOf(title) + redirects).distinct().take(TITLES_PER_REQUEST)
            }

            val hops = canonicalPath.zipWithNext()
            val checked = coroutineScope {
                hops.map { (from, to) ->
                    async {
                        val links = RetrofitClient.wikipediaApi
                            .linksTo(from, variantsOf.getValue(to).joinToString("|"))
                            .query?.pages.orEmpty()
                            .flatMap { it.links.orEmpty() }
                        links.isNotEmpty()
                    }
                }.awaitAll()
            }

            val brokenAt = checked.indexOfFirst { !it }
            if (brokenAt >= 0) {
                val (from, to) = hops[brokenAt]
                return@withContext Verdict.BrokenHop(from, to)
            }
            Verdict.Ok(canonicalPath)
        } catch (e: Exception) {
            Verdict.Offline(NetworkErrors.network(e))
        }
    }

    // --- разбор того, что ввёл игрок ----------------------------------------

    /**
     * Достаёт цепочку статей из аргументов команды.
     *
     * Разделители — запятая, точка с запятой, вертикальная черта и стрелки. Пробел
     * разделителем быть не может: названия статей сплошь многословные, и старый
     * split(" ") резал «Пуп Земли» на две несуществующие статьи.
     *
     * Ссылку на Википедию тоже понимаем: игрок скорее скопирует адрес из браузера,
     * чем станет перенабирать название руками.
     */
    fun parsePath(arguments: String): List<String> =
        arguments.split(',', ';', '|', '>', '→', '»')
            .map { titleFrom(it.trim()) }
            .filter { it.isNotBlank() }

    private fun titleFrom(token: String): String {
        val raw = if (token.startsWith("http://") || token.startsWith("https://")) {
            runCatching { Uri.parse(token).lastPathSegment.orEmpty() }
                .getOrDefault("")
                .let { Uri.decode(it) ?: it }
        } else {
            token
        }
        return raw.replace('_', ' ').trim()
    }

    private fun articleUrl(title: String): String =
        "https://ru.wikipedia.org/wiki/" + Uri.encode(title.replace(' ', '_'), "/:()")
}
