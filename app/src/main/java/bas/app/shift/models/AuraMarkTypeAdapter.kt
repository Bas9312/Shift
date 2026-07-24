package bas.app.shift.models

import com.google.gson.*
import java.lang.reflect.Type

/**
 * Маппит AuraMarkType с сервера через fromServerValue (неизвестное значение → MAGIC_DISCIPLINE),
 * а не через стандартный enum-десериализатор Gson, который для незнакомого значения вернул бы null.
 * Раньше метка с неизвестным сервером типом молча пропадала с холста ауры (markType был null).
 */
class AuraMarkTypeAdapter : JsonDeserializer<AuraMarkType>, JsonSerializer<AuraMarkType> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): AuraMarkType =
        AuraMarkType.fromServerValue(json.asString)

    override fun serialize(src: AuraMarkType, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
        JsonPrimitive(src.serverValue)
}
