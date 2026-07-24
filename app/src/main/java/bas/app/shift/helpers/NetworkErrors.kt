package bas.app.shift.helpers

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException

/**
 * Единое место для человекочитаемых сетевых ошибок. Раньше маппинг кодов/исключений
 * был скопирован по десятку экранов (местами с разным текстом). Теперь — консистентно.
 */
object NetworkErrors {

    /** Сообщение по HTTP-коду ответа. */
    fun http(code: Int): String = when (code) {
        400 -> "Неверный запрос"
        401 -> "Ошибка авторизации"
        403 -> "Доступ запрещён"
        404 -> "Не найдено"
        in 500..599 -> "Ошибка сервера, попробуйте позже"
        else -> "Ошибка ($code)"
    }

    /** Сообщение по исключению сетевого вызова. */
    fun network(t: Throwable?): String = when {
        t is UnknownServiceException || t?.message?.contains("UnknownServiceException") == true ->
            "Ошибка сети: HTTP запросы заблокированы"
        t is UnknownHostException || t?.message?.contains("UnknownHostException") == true ->
            "Нет связи с сервером"
        t is SocketTimeoutException || t?.message?.contains("SocketTimeoutException") == true ->
            "Превышено время ожидания"
        else -> "Ошибка сети: ${t?.message ?: "неизвестная"}"
    }
}
