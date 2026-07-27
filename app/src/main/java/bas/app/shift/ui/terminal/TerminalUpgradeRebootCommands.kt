package bas.app.shift.ui.terminal

import android.content.Context
import bas.app.shift.helpers.WikipediaHelper

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

        // Получаем случайные страницы из Wikipedia
        WikipediaHelper.getRandomPages(
            onSuccess = { startPage, finishPage ->
                val upgradeText = """
                    «Шесть кликов» — вики-серфинг для мозгов

                    Вы открываете одну страницу Википедии (стартовую), и знаете статью которая должна получиться в итоге (конечная). У вас есть максимум шесть переходов по ссылкам, чтобы добраться от стартовой статьи до итоговой.

                    СТАРТОВАЯ СТРАНИЦА:
                    Название: ${startPage.title}
                    Ссылка: ${startPage.fullUrl}

                    ЦЕЛЕВАЯ СТРАНИЦА:
                    Название: ${finishPage.title}
                    Ссылка: ${finishPage.fullUrl}

                    Время на попытку не ограничено.

                    Для завершения задачи используйте команду:
                    USER.UPGRADE.END <название_статьи_1> <название_статьи_2> ... <название_статьи_N>

                    При успехе - уровень шума снижается на 2 уровня.
                """.trimIndent()

                adapter.addTyping(upgradeText)
                activity.saveResponseToHistory(upgradeText)

                // Отмечаем использование команды
                WikipediaHelper.markUpgradeUsed(activity)

                // Активируем сессию UPGRADE
                isUpgradeSessionActive = true
                // Сохраняем флаг активной сессии в преференсы
                val prefs = activity.getSharedPreferences("terminal_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("upgrade_session_active", true).apply()
            },
            onError = { error ->
                val errorMsg = "Ошибка получения страниц Wikipedia: $error"
                adapter.addTyping(errorMsg)
                activity.saveResponseToHistory(errorMsg)
            }
        )


        activity.smoothScrollToBottom()
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

        // Парсим аргументы команды
        val parts = fullCommand.split(" ")
        if (parts.size < 2) {
            val errorMsg = "Ошибка: Необходимо указать названия статей. Формат: USER.UPGRADE.END <статья1> <статья2> ..."
            adapter.addTyping(errorMsg)
            activity.saveResponseToHistory(errorMsg)
            activity.smoothScrollToBottom()
            return
        }

        val articles = parts.drop(1) // Убираем "USER.UPGRADE.END"

        // Проверяем количество статей (максимум 6)
        if (articles.size > 6) {
            val errorMsg = "Ошибка: Максимум 6 статей в пути. Указано: ${articles.size}"
            adapter.addTyping(errorMsg)
            activity.saveResponseToHistory(errorMsg)
            activity.smoothScrollToBottom()
            return
        }

        val successMsg = """
            Поздравляем! Вы успешно прошли путь из ${articles.size} статей:
            ${articles.joinToString(" → ")}

            Уровень шума снижен на 2 уровня.
        """.trimIndent()

        adapter.addTyping(successMsg)
        activity.saveResponseToHistory(successMsg)

        // Снижаем шум
        activity.adjustNoiseAndUpdateGlobal(-2.0)

        // Завершаем сессию UPGRADE
        isUpgradeSessionActive = false
        // Сбрасываем флаг активной сессии в преференсах
        val prefs = activity.getSharedPreferences("terminal_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("upgrade_session_active", false).apply()

        // Отправляем команду в MG чат
        //sendToMg()

        activity.smoothScrollToBottom()
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
        activity.adjustNoiseAndUpdateGlobal(-1.0)

        // Завершаем сессию REBOOT
        isRebootSessionActive = false
        val prefs = activity.getSharedPreferences("terminal_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("reboot_session_active", false).apply()

        activity.smoothScrollToBottom()
    }
}
