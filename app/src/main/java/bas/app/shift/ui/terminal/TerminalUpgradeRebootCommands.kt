package bas.app.shift.ui.terminal

import android.content.Context
import androidx.lifecycle.lifecycleScope
import bas.app.shift.helpers.WikipediaHelper
import kotlinx.coroutines.launch

/**
 * Обработчики команд `USER.UPGRADE.*` и `USER.REBOOT.*`, вынесены из [TerminalActivity]
 * (механический перенос без изменения логики).
 */
class TerminalUpgradeRebootCommands(
    private val activity: TerminalActivity,
    private val adapter: ConsoleAdapter,
) {

    private var isUpgradeSessionActive: Boolean
    private var isRebootSessionActive: Boolean

    init {
        // Восстанавливаем активные сессии UPGRADE/REBOOT из преференсов (если были начаты
        // ранее) — иначе поворот экрана/сворачивание/восстановление процесса сбрасывает
        // in-memory флаг, и USER.REBOOT.END отвечает "нет активной сессии" даже если
        // USER.REBOOT.START был выполнен минуту назад.
        val prefs = activity.getSharedPreferences("terminal_prefs", Context.MODE_PRIVATE)
        isUpgradeSessionActive = prefs.getBoolean("upgrade_session_active", false)
        isRebootSessionActive = prefs.getBoolean("reboot_session_active", false)
    }

    fun handleUpgradeStartCommand() {
        val executingMsg = "Выполняю: USER.UPGRADE.START"
        adapter.addTyping(executingMsg)
        activity.saveResponseToHistory(executingMsg)

        // Проверяем кулдаун
        if (!WikipediaHelper.canUseUpgrade(activity)) {
            val timeUntilNext = WikipediaHelper.getTimeUntilNextUpgrade(activity)
            val hoursLeft = timeUntilNext / (60 * 60 * 1000)
            val minutesLeft = (timeUntilNext % (60 * 60 * 1000)) / (60 * 1000)

            val cooldownMsg = "Команда недоступна. Следующее использование через: ${hoursLeft}ч ${minutesLeft}м"
            adapter.addTyping(cooldownMsg)
            activity.saveResponseToHistory(cooldownMsg)
            activity.smoothScrollToBottom()
            return
        }

        val processMsg = "Команда в процессе выполнения..."
        adapter.addTyping(processMsg)
        activity.saveResponseToHistory(processMsg)
        activity.smoothScrollToBottom()

        // Загадка собирается прогулкой по настоящим ссылкам, а это несколько запросов подряд.
        activity.lifecycleScope.launch {
            val puzzle = WikipediaHelper.buildPuzzle()
            if (puzzle == null) {
                val errorMsg = "Не удалось собрать маршрут: Википедия не отвечает. Попробуй ещё раз."
                adapter.addTyping(errorMsg)
                activity.saveResponseToHistory(errorMsg)
                activity.smoothScrollToBottom()
                return@launch
            }

            val upgradeText = """
                «Шесть кликов» — вики-серфинг для мозгов

                Открой стартовую статью и добирайся до целевой, переходя только по ссылкам внутри статей. Переходов — не больше ${WikipediaHelper.MAX_HOPS}. Путь существует: маршрут собран по настоящим ссылкам.

                СТАРТОВАЯ СТРАНИЦА:
                Название: ${puzzle.start}
                Ссылка: ${puzzle.startUrl}

                ЦЕЛЕВАЯ СТРАНИЦА:
                Название: ${puzzle.finish}
                Ссылка: ${puzzle.finishUrl}

                Время на попытку не ограничено.

                Когда дойдёшь — перечисли статьи, через которые прошёл, ЧЕРЕЗ ЗАПЯТУЮ:
                USER.UPGRADE.END вторая статья, третья статья, ${puzzle.finish}

                Стартовую можно не писать. Вместо названий можно вставлять ссылки из браузера.
                Путь проверяется по настоящим ссылкам Википедии, так что придумать его не выйдет.

                При успехе - уровень шума снижается на 2 уровня.
            """.trimIndent()

            adapter.addTyping(upgradeText)
            activity.saveResponseToHistory(upgradeText)

            WikipediaHelper.savePuzzle(activity, puzzle)
            WikipediaHelper.markUpgradeUsed(activity)

            isUpgradeSessionActive = true
            val prefs = activity.getSharedPreferences("terminal_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("upgrade_session_active", true).apply()

            activity.smoothScrollToBottom()
        }
    }

    fun handleUpgradeEndCommand(fullCommand: String) {
        val executingMsg = "Выполняю: USER.UPGRADE.END"
        adapter.addTyping(executingMsg)
        activity.saveResponseToHistory(executingMsg)

        // Проверяем активную сессию UPGRADE
        if (!isUpgradeSessionActive) {
            val errorMsg = "Ошибка: Нет активной сессии вики-серфинга. Сначала выполните USER.UPGRADE.START"
            adapter.addTyping(errorMsg)
            activity.saveResponseToHistory(errorMsg)
            activity.smoothScrollToBottom()
            return
        }

        val puzzle = WikipediaHelper.loadPuzzle(activity)
        if (puzzle == null) {
            val errorMsg = "Ошибка: маршрут потерян. Начни заново: USER.UPGRADE.START"
            adapter.addTyping(errorMsg)
            activity.saveResponseToHistory(errorMsg)
            activity.smoothScrollToBottom()
            return
        }

        val arguments = fullCommand.drop("USER.UPGRADE.END".length)
        val typed = WikipediaHelper.parsePath(arguments)
        if (typed.isEmpty()) {
            val errorMsg = "Ошибка: перечисли статьи через запятую.\n" +
                "Формат: USER.UPGRADE.END вторая статья, третья статья, ${puzzle.finish}"
            adapter.addTyping(errorMsg)
            activity.saveResponseToHistory(errorMsg)
            activity.smoothScrollToBottom()
            return
        }
        // Отсекаем заведомо длинные списки до похода в сеть: проверка стоит запроса на переход.
        if (typed.size > WikipediaHelper.MAX_HOPS + 2) {
            val errorMsg = "Ошибка: переходов не больше ${WikipediaHelper.MAX_HOPS}, " +
                "а статей перечислено ${typed.size}."
            adapter.addTyping(errorMsg)
            activity.saveResponseToHistory(errorMsg)
            activity.smoothScrollToBottom()
            return
        }

        // Стартовую статью подставляем всегда: игрок мог её не написать или написать
        // редиректом. Одинаковые соседи схлопнутся при приведении к каноническим именам.
        val claimedPath = listOf(puzzle.start) + typed

        val checkingMsg = "Проверяю путь по ссылкам Википедии..."
        adapter.addTyping(checkingMsg)
        activity.saveResponseToHistory(checkingMsg)
        activity.smoothScrollToBottom()

        activity.lifecycleScope.launch {
            val verdict = WikipediaHelper.verifyPath(claimedPath)
            val message = when (verdict) {
                is WikipediaHelper.Verdict.Offline ->
                    "Не удалось проверить путь: ${verdict.reason}\nПопробуй ещё раз, попытка не сгорела."

                is WikipediaHelper.Verdict.NoSuchArticle ->
                    "Такой статьи в Википедии нет: «${verdict.title}».\n" +
                        "Проверь название или вставь ссылку из браузера. Попытка не сгорела."

                is WikipediaHelper.Verdict.BrokenHop ->
                    "Путь не сходится: со страницы «${verdict.from}» нет ссылки на «${verdict.to}».\n" +
                        "Попытка не сгорела — поправь цепочку и повтори."

                is WikipediaHelper.Verdict.Ok -> null
            }
            if (message != null) {
                adapter.addTyping(message)
                activity.saveResponseToHistory(message)
                activity.smoothScrollToBottom()
                return@launch
            }

            val path = (verdict as WikipediaHelper.Verdict.Ok).canonicalPath
            val hops = path.size - 1
            val failure = when {
                path.last() != puzzle.finish ->
                    "Путь настоящий, но заканчивается не там: нужна «${puzzle.finish}», " +
                        "а цепочка приводит в «${path.last()}». Попытка не сгорела."

                hops < 1 ->
                    "В пути нет ни одного перехода. Попытка не сгорела."

                hops > WikipediaHelper.MAX_HOPS ->
                    "Путь настоящий, но длинный: переходов ${hops}, " +
                        "а можно не больше ${WikipediaHelper.MAX_HOPS}. Попытка не сгорела."

                else -> null
            }
            if (failure != null) {
                adapter.addTyping(failure)
                activity.saveResponseToHistory(failure)
                activity.smoothScrollToBottom()
                return@launch
            }

            val successMsg = """
                Поздравляем! Путь проверен — ${hops} ${hopWord(hops)} по ссылкам:
                ${path.joinToString(" → ")}

                Уровень шума снижен на 2 уровня.
            """.trimIndent()

            adapter.addTyping(successMsg)
            activity.saveResponseToHistory(successMsg)

            activity.adjustNoiseAndUpdateGlobal(0.0, "USER.UPGRADE.END")

            isUpgradeSessionActive = false
            val prefs = activity.getSharedPreferences("terminal_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("upgrade_session_active", false).apply()
            WikipediaHelper.clearPuzzle(activity)

            activity.smoothScrollToBottom()
        }
    }

    fun handleRebootStartCommand() {
        val executingMsg = "Выполняю: USER.REBOOT.START"
        adapter.addTyping(executingMsg)
        activity.saveResponseToHistory(executingMsg)

        // Проверяем кулдаун (1 час)
        val prefs = activity.getSharedPreferences("terminal_prefs", Context.MODE_PRIVATE)
        val lastRebootTime = prefs.getLong("last_reboot_time", 0)
        val currentTime = System.currentTimeMillis()
        val oneHour = 60 * 60 * 1000L

        if (currentTime - lastRebootTime < oneHour) {
            val timeLeft = oneHour - (currentTime - lastRebootTime)
            val hoursLeft = timeLeft / (60 * 60 * 1000)
            val minutesLeft = (timeLeft % (60 * 60 * 1000)) / (60 * 1000)

            val cooldownMsg = "Команда недоступна. Следующее использование через: ${hoursLeft}ч ${minutesLeft}м"
            adapter.addTyping(cooldownMsg)
            activity.saveResponseToHistory(cooldownMsg)
            activity.smoothScrollToBottom()
            return
        }

        val rebootText = """
            === ПЕРЕЗАГРУЗКА СИСТЕМЫ ===

            Инициирую осознанный цифровой отдых...

            Система переходит в режим глубокого восстановления.
            Все активные процессы приостановлены.
            Память очищается от временных данных.

            Для завершения перезагрузки используйте команду:
            USER.REBOOT.END

            Время на восстановление должно составлять минимум 5 минут.
            При успешном завершении - уровень шума снижается на 1 уровень.
        """.trimIndent()

        adapter.addTyping(rebootText)
        activity.saveResponseToHistory(rebootText)

        // Активируем сессию REBOOT
        isRebootSessionActive = true
        prefs.edit()
            .putLong("last_reboot_time", currentTime)
            .putBoolean("reboot_session_active", true)
            .apply()

        activity.smoothScrollToBottom()
    }

    fun handleRebootEndCommand() {
        val executingMsg = "Выполняю: USER.REBOOT.END"
        adapter.addTyping(executingMsg)
        activity.saveResponseToHistory(executingMsg)

        // Проверяем активную сессию REBOOT
        if (!isRebootSessionActive) {
            val errorMsg = "Ошибка: Нет активной сессии перезагрузки. Сначала выполните USER.REBOOT.START"
            adapter.addTyping(errorMsg)
            activity.saveResponseToHistory(errorMsg)
            activity.smoothScrollToBottom()
            return
        }

        // По правилам «Перезагрузка» — это пять минут белого шума в наушниках с отложенным
        // телефоном. Раньше END принимался сразу после START, и отдых стоил двух нажатий;
        // теперь таймер настоящий.
        val prefs = activity.getSharedPreferences("terminal_prefs", Context.MODE_PRIVATE)
        val startedAt = prefs.getLong("last_reboot_time", 0L)
        val elapsed = System.currentTimeMillis() - startedAt
        if (startedAt > 0L && elapsed < REBOOT_DURATION_MS) {
            val leftSec = ((REBOOT_DURATION_MS - elapsed) / 1000).toInt()
            val errorMsg = "Перезагрузка ещё идёт: осталось ${leftSec / 60} мин ${leftSec % 60} сек.\n" +
                "Не отвлекайся — белый шум в наушниках, телефон отложен, глаза закрыты."
            adapter.addTyping(errorMsg)
            activity.saveResponseToHistory(errorMsg)
            activity.smoothScrollToBottom()
            return
        }

        val successMsg = """
            === ПЕРЕЗАГРУЗКА ЗАВЕРШЕНА ===

            Система успешно восстановлена.
            Все процессы возобновлены.
            Память оптимизирована.

            Уровень шума снижен на 1 уровень.
            Готов к работе.
        """.trimIndent()

        adapter.addTyping(successMsg)
        activity.saveResponseToHistory(successMsg)

        // Снижаем шум
        activity.adjustNoiseAndUpdateGlobal(0.0, "USER.REBOOT.END")

        // Завершаем сессию REBOOT
        isRebootSessionActive = false
        prefs.edit().putBoolean("reboot_session_active", false).apply()

        activity.smoothScrollToBottom()
    }

    /** «1 переход», «2 перехода», «6 переходов» — иначе в терминале режет глаз. */
    private fun hopWord(hops: Int): String {
        val last2 = hops % 100
        val last1 = hops % 10
        return when {
            last2 in 11..14 -> "переходов"
            last1 == 1 -> "переход"
            last1 in 2..4 -> "перехода"
            else -> "переходов"
        }
    }

    private companion object {
        /** «Перезагрузка» по правилам занимает 5 минут реального отдыха (белый шум, наушники). */
        const val REBOOT_DURATION_MS = 5 * 60 * 1000L
    }
}
