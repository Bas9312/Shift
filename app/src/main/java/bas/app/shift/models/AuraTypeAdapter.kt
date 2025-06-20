package bas.app.shift.models

import com.google.gson.*
import java.lang.reflect.Type

class AuraTypeAdapter : JsonDeserializer<AuraType>, JsonSerializer<AuraType> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): AuraType {
        return AuraType.fromServerValue(json.asString)
    }

    override fun serialize(src: AuraType, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return JsonPrimitive(src.serverValue)
    }
} 