package bas.app.shift.models

import com.google.gson.*
import java.lang.reflect.Type

class AuraProblemTypeAdapter : JsonDeserializer<AuraProblemType>, JsonSerializer<AuraProblemType> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): AuraProblemType {
        return AuraProblemType.fromServerValue(json.asString)
    }

    override fun serialize(src: AuraProblemType, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return JsonPrimitive(src.serverValue)
    }
} 