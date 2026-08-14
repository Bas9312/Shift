package bas.app.shift.helpers

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkErrorsTest {

    @Test
    fun http_knownCodes_returnSpecificMessages() {
        assertEquals("Неверный запрос", NetworkErrors.http(400))
        assertEquals("Ошибка авторизации", NetworkErrors.http(401))
        assertEquals("Доступ запрещён", NetworkErrors.http(403))
        assertEquals("Не найдено", NetworkErrors.http(404))
    }

    @Test
    fun http_5xxRange_returnsServerErrorMessage() {
        assertEquals("Ошибка сервера, попробуйте позже", NetworkErrors.http(500))
        assertEquals("Ошибка сервера, попробуйте позже", NetworkErrors.http(503))
        assertEquals("Ошибка сервера, попробуйте позже", NetworkErrors.http(599))
    }

    @Test
    fun http_unknownCode_fallsBackToGenericMessageWithCode() {
        assertEquals("Ошибка (418)", NetworkErrors.http(418))
        assertEquals("Ошибка (600)", NetworkErrors.http(600))
    }

    @Test
    fun network_unknownHostException_mapsToNoConnectionMessage() {
        assertEquals("Нет связи с сервером", NetworkErrors.network(UnknownHostException()))
    }

    @Test
    fun network_socketTimeoutException_mapsToTimeoutMessage() {
        assertEquals("Превышено время ожидания", NetworkErrors.network(SocketTimeoutException()))
    }

    @Test
    fun network_unknownServiceException_mapsToBlockedHttpMessage() {
        assertEquals("Ошибка сети: HTTP запросы заблокированы", NetworkErrors.network(UnknownServiceException()))
    }

    @Test
    fun network_wrappedExceptionMatchedByMessageText_stillMapsCorrectly() {
        // Некоторые OkHttp-исключения оборачивают причину в другой тип, но сохраняют
        // имя исходного класса в тексте сообщения — маппинг должен ловить и это.
        val wrapped = RuntimeException("Failed to connect: java.net.UnknownHostException: shift96.ru")
        assertEquals("Нет связи с сервером", NetworkErrors.network(wrapped))
    }

    @Test
    fun network_nullOrUnrecognizedThrowable_fallsBackToGenericMessage() {
        assertEquals("Ошибка сети: неизвестная", NetworkErrors.network(null))
        assertEquals("Ошибка сети: boom", NetworkErrors.network(RuntimeException("boom")))
    }
}
