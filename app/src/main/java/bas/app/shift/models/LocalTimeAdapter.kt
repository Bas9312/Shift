package bas.app.shift.models

import com.google.gson.*
import java.lang.reflect.Type
import java.time.LocalTime

/**
 * Сериализует java.time.LocalTime как строку ISO ("HH:mm:ss.SSS") и обратно.
 * Без этого адаптера Gson пытается лезть в приватные поля LocalTime через рефлексию,
 * что на новых версиях Android бросает исключение и роняет ВСЮ загрузку истории терминала
 * (см. TerminalHistoryHelper.loadHistory) → история молча обнулялась.
 */
class LocalTimeAdapter : JsonSerializer<LocalTime>, JsonDeserializer<LocalTime> {
    override fun serialize(src: LocalTime, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
        JsonPrimitive(src.toString())

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): LocalTime =
        try {
            LocalTime.parse(json.asString)
        } catch (e: Exception) {
            LocalTime.MIDNIGHT
        }
}
