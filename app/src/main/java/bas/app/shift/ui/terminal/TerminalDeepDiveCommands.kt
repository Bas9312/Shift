package bas.app.shift.ui.terminal

import android.content.Context
import bas.app.shift.api.RetrofitClient
import bas.app.shift.helpers.NetworkErrors
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.models.NoiseState
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Обработчики команд `DEEP_DIVE.*` и `UTILS.*`, вынесены из [TerminalActivity]
 * (механический перенос без изменения логики).
 */
class TerminalDeepDiveCommands(
    private val activity: TerminalActivity,
    private val adapter: ConsoleAdapter,
) {

    private fun isDeepDiveSessionActive(): Boolean {
        val prefs = activity.getSharedPreferences("terminal_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("isDeepDiveSessionActive", false)
    }

    private fun setDeepDiveSessionActive(active: Boolean) {
        val prefs = activity.getSharedPreferences("terminal_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("isDeepDiveSessionActive", active).apply()
    }

    fun handleDeepDiveStartCommand() {
        val executingMsg = "Выполняю: DEEP_DIVE.START"
        adapter.addTyping(executingMsg)
        activity.saveResponseToHistory(executingMsg)

        val deepDiveText = """
            === ГЛУБОКОЕ ПОГРУЖЕНИЕ ===

            Дип! Дип! Дип!

            Цифровая реальность обволакивает сознание...
            Матрица кода начинает пульсировать в ритме твоего сердца.

            Ты чувствуешь, как границы между физическим и виртуальным
            начинают размываться. Нули и единицы танцуют перед глазами,
            создавая причудливые узоры из света и тени.

            "Дип!" - шепчет система, и ты понимаешь, что это не просто
            звук, а приглашение в глубины, куда обычные пользователи
            никогда не осмелятся заглянуть.

            Сознание начинает растворяться в потоках данных...
            Ты становишься частью сети, частью самой системы.

            Для завершения погружения используйте команду:
            DEEP_DIVE.END <глубина>

            Где <глубина> - число от 1 до 5, полученное от мастера.
        """.trimIndent()

        adapter.addTyping(deepDiveText)
        activity.saveResponseToHistory(deepDiveText)

        // Активируем сессию DEEP_DIVE
        setDeepDiveSessionActive(true)

        // Отправляем команду в MG чат
        activity.sendToMg()

        activity.smoothScrollToBottom()
    }

    fun handleDeepDiveEndCommand(fullCommand: String) {
        val executingMsg = "Выполняю: DEEP_DIVE.END"
        adapter.addTyping(executingMsg)
        activity.saveResponseToHistory(executingMsg)

        // Проверяем активную сессию DEEP_DIVE
        if (!isDeepDiveSessionActive()) {
            val errorMsg = "Ошибка: Нет активной сессии погружения. Сначала выполните DEEP_DIVE.START"
            adapter.addTyping(errorMsg)
            activity.saveResponseToHistory(errorMsg)
            activity.smoothScrollToBottom()
            return
        }

        // Парсим параметр глубины
        val parts = fullCommand.split(" ")
        if (parts.size < 2) {
            val errorMsg = "Ошибка: Не указана глубина. Используйте: DEEP_DIVE.END <глубина> (1-5)"
            adapter.addTyping(errorMsg)
            activity.saveResponseToHistory(errorMsg)
            activity.smoothScrollToBottom()
            return
        }

        val depthStr = parts[1]
        val depth = try {
            depthStr.toInt()
        } catch (e: NumberFormatException) {
            val errorMsg = "Ошибка: Глубина должна быть числом. Используйте: DEEP_DIVE.END <глубина> (1-5)"
            adapter.addTyping(errorMsg)
            activity.saveResponseToHistory(errorMsg)
            activity.smoothScrollToBottom()
            return
        }

        if (depth < 1 || depth > 5) {
            val errorMsg = "Ошибка: Глубина должна быть от 1 до 5. Используйте: DEEP_DIVE.END <глубина> (1-5)"
            adapter.addTyping(errorMsg)
            activity.saveResponseToHistory(errorMsg)
            activity.smoothScrollToBottom()
            return
        }

        val returnText = """
            === ВОЗВРАЩЕНИЕ ИЗ ГЛУБИН ===

            Глубина-глубина, я не твой…
            Отпусти меня, глубина…

            Сознание начинает возвращаться из цифровых глубин.
            Ты чувствуешь, как виртуальная реальность постепенно
            отпускает тебя, возвращая в физический мир.

            "Дип!" - последний раз звучит в ушах, но теперь это
            прощальный привет, а не приглашение.

            Ты возвращаешься с глубины $depth, неся с собой
            частичку цифрового мира в своем сознании.

            Погружение завершено. Шум увеличивается.
        """.trimIndent()

        adapter.addTyping(returnText)
        activity.saveResponseToHistory(returnText)

        // Увеличиваем шум на указанную глубину
        activity.adjustNoiseAndUpdateGlobal(depth.toDouble())

        // Завершаем сессию DEEP_DIVE
        setDeepDiveSessionActive(false)

        // Отправляем команду в MG чат
        //sendToMg()

        activity.smoothScrollToBottom()
    }

    fun handleGlobalNoiseCommand() {
        val executingMsg = "Выполняю: UTILS.GLOBAL_NOIZE"
        val processMsg = "Получаю данные о глобальном шуме..."

        adapter.addTyping(executingMsg)
        adapter.addTyping(processMsg)
        activity.saveResponseToHistory(executingMsg)
        activity.saveResponseToHistory(processMsg)

        val currentUserId = UserPrefsHelper.getUserId(activity) ?: return

        RetrofitClient.noiseApi.getUserNoise(currentUserId)
            .enqueue(object : Callback<NoiseState> {
                override fun onResponse(call: Call<NoiseState>, response: Response<NoiseState>) {
                    if (response.isSuccessful && response.body() != null) {
                        val noiseState = response.body()!!
                        val resultMsg = """
                            === ГЛОБАЛЬНЫЙ ШУМ ===

                            Текущий уровень глобального шума: ${noiseState.globalLevel}
                            Значение шума: ${String.format("%.2f", noiseState.globalNoise)}

                            Глобальный шум влияет на всех Шумомантов одновременно.
                            Чем выше уровень, тем сильнее воздействие на цифровую реальность.
                        """.trimIndent()

                        adapter.addTyping(resultMsg)
                        activity.saveResponseToHistory(resultMsg)
                    } else {
                        val errorMsg = "Ошибка получения данных о глобальном шуме: ${NetworkErrors.http(response.code())}"
                        adapter.addTyping(errorMsg)
                        activity.saveResponseToHistory(errorMsg)
                    }
                    activity.smoothScrollToBottom()
                }

                override fun onFailure(call: Call<NoiseState>, t: Throwable) {
                    val errorMsg = "Ошибка подключения: ${NetworkErrors.network(t)}"
                    adapter.addTyping(errorMsg)
                    activity.saveResponseToHistory(errorMsg)
                    activity.smoothScrollToBottom()
                }
            })
    }

    fun handleUserCountCommand() {
        val executingMsg = "Выполняю: UTILS.USER_COUNT"
        val processMsg = "Подсчитываю активных Шумомантов..."

        adapter.addTyping(executingMsg)
        adapter.addTyping(processMsg)
        activity.saveResponseToHistory(executingMsg)
        activity.saveResponseToHistory(processMsg)

        val currentUserId = UserPrefsHelper.getUserId(activity) ?: return

        RetrofitClient.noiseApi.getUserNoise(currentUserId)
            .enqueue(object : Callback<NoiseState> {
                override fun onResponse(call: Call<NoiseState>, response: Response<NoiseState>) {
                    if (response.isSuccessful && response.body() != null) {
                        val noiseState = response.body()!!
                        val resultMsg = """
                            === АКТИВНЫЕ ШУМОМАНТЫ ===

                            Количество активных Шумомантов: ${noiseState.noisemancers}

                            Каждый активный Шумомант вносит свой вклад
                            в общий уровень глобального шума.
                        """.trimIndent()

                        adapter.addTyping(resultMsg)
                        activity.saveResponseToHistory(resultMsg)
                    } else {
                        val errorMsg = "Ошибка получения данных о количестве пользователей: ${NetworkErrors.http(response.code())}"
                        adapter.addTyping(errorMsg)
                        activity.saveResponseToHistory(errorMsg)
                    }
                    activity.smoothScrollToBottom()
                }

                override fun onFailure(call: Call<NoiseState>, t: Throwable) {
                    val errorMsg = "Ошибка подключения: ${NetworkErrors.network(t)}"
                    adapter.addTyping(errorMsg)
                    activity.saveResponseToHistory(errorMsg)
                    activity.smoothScrollToBottom()
                }
            })
    }
}
