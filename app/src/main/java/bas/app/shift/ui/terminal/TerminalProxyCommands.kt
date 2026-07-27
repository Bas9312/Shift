package bas.app.shift.ui.terminal

import bas.app.shift.api.RetrofitClient
import bas.app.shift.helpers.NetworkErrors
import bas.app.shift.helpers.NoiseHelper
import bas.app.shift.helpers.NoiseManager
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.models.NoiseAdjustRequest
import bas.app.shift.models.NoiseAdjustResponse
import bas.app.shift.models.NoiseState
import bas.app.shift.models.User
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Обработчики команд `SHIFT.PROXY.*` и `CROSS.LINK`, вынесены из [TerminalActivity]
 * (механический перенос без изменения логики).
 */
class TerminalProxyCommands(
    private val activity: TerminalActivity,
    private val adapter: ConsoleAdapter,
    private val noiseManager: NoiseManager,
) {

    fun handleProxyDeployCommand(fullCommand: String) {
        val executingMsg = "Выполняю: SHIFT.PROXY.DEPLOY"
        adapter.addTyping(executingMsg)
        activity.saveResponseToHistory(executingMsg)

        // Проверяем, есть ли уже Proxy эффект
        val hasProxyEffect = noiseManager.hasProxyEffect()

        if (hasProxyEffect) {
            val errorMsg = "Ошибка: Proxy узел уже развернут. Повторная активация невозможна."
            adapter.addTyping(errorMsg)
            activity.saveResponseToHistory(errorMsg)
            activity.smoothScrollToBottom()
            return
        }

        // Парсим параметр node
        val parts = fullCommand.split(" ")
        if (parts.size < 2) {
            val errorMsg = "Ошибка: Не указан параметр <node>. Используйте: SHIFT.PROXY.DEPLOY <node>"
            adapter.addTyping(errorMsg)
            activity.saveResponseToHistory(errorMsg)
            activity.smoothScrollToBottom()
            return
        }

        val nodeName = parts[1]

        val processMsg = "Разворачиваю Proxy узел '$nodeName'..."
        adapter.addTyping(processMsg)
        activity.saveResponseToHistory(processMsg)

        // Добавляем шум за развертывание узла (+2)
        val currentUserId = UserPrefsHelper.getUserId(activity) ?: return
        activity.adjustNoiseAndUpdateGlobal(2.0)

        // Применяем Proxy эффект
        noiseManager.applyProxyEffect(currentUserId)

        // Сбрасываем шум на Proxy узле (уменьшаем на 10)
        val proxyUserId = "${currentUserId}_Proxy"
        val resetMsg = "Сбрасываю шум на Proxy узле..."
        adapter.addTyping(resetMsg)
        activity.saveResponseToHistory(resetMsg)

        // Отправляем запрос на уменьшение шума на Proxy узле
        val request = NoiseAdjustRequest(delta = -10.0)
        RetrofitClient.noiseApi.adjustUserNoise(proxyUserId, request)
            .enqueue(object : Callback<NoiseAdjustResponse> {
                override fun onResponse(call: Call<NoiseAdjustResponse>, response: Response<NoiseAdjustResponse>) {
                    if (response.isSuccessful) {
                        val resetSuccessMsg = "Шум на Proxy узле сброшен."
                        adapter.addTyping(resetSuccessMsg)
                        activity.saveResponseToHistory(resetSuccessMsg)
                    } else {
                        val resetErrorMsg = "Предупреждение: не удалось сбросить шум на Proxy узле (${NetworkErrors.http(response.code())})"
                        adapter.addTyping(resetErrorMsg)
                        activity.saveResponseToHistory(resetErrorMsg)
                    }

                    // Показываем сообщение об успешном развертывании
                    showProxyDeploySuccess(nodeName, currentUserId)
                }

                override fun onFailure(call: Call<NoiseAdjustResponse>, t: Throwable) {
                    val resetErrorMsg = "Предупреждение: не удалось сбросить шум на Proxy узле (${NetworkErrors.network(t)})"
                    adapter.addTyping(resetErrorMsg)
                    activity.saveResponseToHistory(resetErrorMsg)

                    // Показываем сообщение об успешном развертывании
                    showProxyDeploySuccess(nodeName, currentUserId)
                }
            })
    }

    private fun showProxyDeploySuccess(nodeName: String, currentUserId: String) {
        val successMsg = """
            === PROXY УЗЕЛ РАЗВЕРНУТ ===

            Узел '$nodeName' успешно развернут и активен.
            Эффект "Узел Proxy установлен и работает" применен.

            Теперь при выполнении команд, генерирующих положительный шум,
            шум будет автоматически делиться пополам между вашим ID
            и ID узла (${currentUserId}_Proxy).

            Узел будет активен 24 часа, после чего эффект автоматически истечет.
        """.trimIndent()

        adapter.addTyping(successMsg)
        activity.saveResponseToHistory(successMsg)

        activity.smoothScrollToBottom()
    }

    fun handleCrossLinkCommand(fullCommand: String) {
        val executingMsg = "Выполняю: CROSS.LINK"
        adapter.addTyping(executingMsg)
        activity.saveResponseToHistory(executingMsg)

        // Проверяем, есть ли уже Cross-Link эффект
        val hasCrossLinkEffect = noiseManager.hasCrossLinkEffect()

        if (hasCrossLinkEffect) {
            val errorMsg = "Ошибка: Cross-Link связь уже установлена. Сначала разорвите существующую связь."
            adapter.addTyping(errorMsg)
            activity.saveResponseToHistory(errorMsg)
            activity.smoothScrollToBottom()
            return
        }

        // Парсим параметр partner_id
        val parts = fullCommand.split(" ")
        if (parts.size < 2) {
            val errorMsg = "Ошибка: Не указан параметр <partner_id>. Используйте: CROSS.LINK <partner_id>"
            adapter.addTyping(errorMsg)
            activity.saveResponseToHistory(errorMsg)
            activity.smoothScrollToBottom()
            return
        }

        val partnerId = parts[1]

        val processMsg = "Инициирую связку с партнером '$partnerId'..."
        adapter.addTyping(processMsg)
        activity.saveResponseToHistory(processMsg)

        // Получаем профиль партнера
        RetrofitClient.userProfileApi.getUserProfile(partnerId)
            .enqueue(object : Callback<User> {
                override fun onResponse(call: Call<User>, response: Response<User>) {
                    if (response.isSuccessful && response.body() != null) {
                        val partnerProfile = response.body()!!
                        val currentUserId = UserPrefsHelper.getUserId(activity) ?: return
                        val currentUserProfile = UserPrefsHelper.getUserData(activity) ?: return

                        // Применяем Cross-Link эффект для обоих пользователей
                        noiseManager.applyCrossLinkEffect(
                            currentUserId,
                            partnerId,
                            currentUserProfile.characterName,
                            partnerProfile.characterName
                        )

                        val successMsg = """
                            === CROSS-LINK СВЯЗЬ УСТАНОВЛЕНА ===

                            Связь с ${partnerProfile.characterName} успешно установлена.
                            Эффект "Связь с ${partnerProfile.characterName} установлена, шум делится пополам" применен.

                            Теперь при выполнении команд, генерирующих положительный шум,
                            шум будет автоматически делиться пополам между вами
                            и вашим партнером.

                            Связь будет активна 24 часа, после чего эффект автоматически истечет.
                        """.trimIndent()

                        adapter.addTyping(successMsg)
                        activity.saveResponseToHistory(successMsg)
                    } else {
                        val errorMsg = if (response.code() == 404) {
                            "Ошибка: Партнер с ID '$partnerId' не найден"
                        } else {
                            "Ошибка: Партнер с ID '$partnerId' не найден (${NetworkErrors.http(response.code())})"
                        }
                        adapter.addTyping(errorMsg)
                        activity.saveResponseToHistory(errorMsg)
                    }
                    activity.smoothScrollToBottom()
                }

                override fun onFailure(call: Call<User>, t: Throwable) {
                    val errorMsg = "Ошибка: Не удалось найти партнера '$partnerId' (${NetworkErrors.network(t)})"
                    adapter.addTyping(errorMsg)
                    activity.saveResponseToHistory(errorMsg)
                    activity.smoothScrollToBottom()
                }
            })

        // Отправляем команду в MG чат
        activity.sendToMg()
    }

    fun handleProxyStatusCommand() {
        val executingMsg = "Выполняю: SHIFT.PROXY.STATUS"
        adapter.addTyping(executingMsg)
        activity.saveResponseToHistory(executingMsg)

        // Проверяем, есть ли активный Proxy эффект
        val hasProxyEffect = noiseManager.hasProxyEffect()

        if (!hasProxyEffect) {
            val errorMsg = "Ошибка: Proxy узел не развернут. Используйте SHIFT.PROXY.DEPLOY для развертывания узла."
            adapter.addTyping(errorMsg)
            activity.saveResponseToHistory(errorMsg)
            activity.smoothScrollToBottom()
            return
        }

        val processMsg = "Проверяю статус Proxy узла..."
        adapter.addTyping(processMsg)
        activity.saveResponseToHistory(processMsg)

        val currentUserId = UserPrefsHelper.getUserId(activity) ?: return
        val proxyUserId = "${currentUserId}_Proxy"

        // Запрашиваем шум Proxy узла
        RetrofitClient.noiseApi.getUserNoise(proxyUserId)
            .enqueue(object : Callback<NoiseState> {
                override fun onResponse(call: Call<NoiseState>, response: Response<NoiseState>) {
                    if (response.isSuccessful && response.body() != null) {
                        val noiseState = response.body()!!
                        val localLevel = NoiseHelper.getNoiseLevel(noiseState.localNoise)
                        val resultMsg = """
                            === СТАТУС PROXY УЗЛА ===

                            ID узла: $proxyUserId
                            Локальный уровень шума узла: $localLevel
                            Локальное значение шума узла: ${String.format("%.2f", noiseState.localNoise)}

                            Proxy узел активен и функционирует.
                            Шум автоматически распределяется между основным
                            пользователем и узлом при выполнении команд.
                        """.trimIndent()

                        adapter.addTyping(resultMsg)
                        activity.saveResponseToHistory(resultMsg)
                    } else {
                        val errorMsg = "Ошибка получения данных Proxy узла: ${NetworkErrors.http(response.code())}"
                        adapter.addTyping(errorMsg)
                        activity.saveResponseToHistory(errorMsg)
                    }
                    activity.smoothScrollToBottom()
                }

                override fun onFailure(call: Call<NoiseState>, t: Throwable) {
                    val errorMsg = "Ошибка подключения к Proxy узлу: ${NetworkErrors.network(t)}"
                    adapter.addTyping(errorMsg)
                    activity.saveResponseToHistory(errorMsg)
                    activity.smoothScrollToBottom()
                }
            })
    }
}
