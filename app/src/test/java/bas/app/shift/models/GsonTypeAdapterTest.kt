package bas.app.shift.models

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import java.lang.reflect.Type
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

// Adapters never call into the context (no nested (de)serialization), so a
// throwing stub is enough to prove that and catches it immediately if that changes.
private val unusedDeserializationContext = object : JsonDeserializationContext {
    override fun <T : Any?> deserialize(json: JsonElement?, typeOfT: Type?): T =
        throw UnsupportedOperationException("not expected to be called")
}
private val unusedSerializationContext = object : JsonSerializationContext {
    override fun serialize(src: Any?): JsonElement = throw UnsupportedOperationException("not expected to be called")
    override fun serialize(src: Any?, typeOfSrc: Type?): JsonElement = throw UnsupportedOperationException("not expected to be called")
}

class GsonTypeAdapterTest {

    // --- AuraMarkTypeAdapter ---
    // Regression: an unknown server value used to deserialize to a null markType via Gson's
    // default enum adapter, silently dropping the mark from the aura canvas (see class kdoc).

    @Test
    fun auraMarkTypeAdapter_deserialize_knownValue_returnsMatchingConstant() {
        val adapter = AuraMarkTypeAdapter()
        assertEquals(
            AuraMarkType.CURSE,
            adapter.deserialize(JsonPrimitive("CURSE"), AuraMarkType::class.java, unusedDeserializationContext)
        )
    }

    @Test
    fun auraMarkTypeAdapter_deserialize_unknownValue_fallsBackInsteadOfNull() {
        val adapter = AuraMarkTypeAdapter()
        assertEquals(
            AuraMarkType.MAGIC_DISCIPLINE,
            adapter.deserialize(JsonPrimitive("NOT_A_REAL_MARK"), AuraMarkType::class.java, unusedDeserializationContext)
        )
    }

    @Test
    fun auraMarkTypeAdapter_serialize_writesServerValue() {
        val adapter = AuraMarkTypeAdapter()
        assertEquals(
            JsonPrimitive("FAMILIAR_LINK"),
            adapter.serialize(AuraMarkType.FAMILIAR_LINK, AuraMarkType::class.java, unusedSerializationContext)
        )
    }

    // --- AuraProblemTypeAdapter ---

    @Test
    fun auraProblemTypeAdapter_deserialize_knownValue_returnsMatchingConstant() {
        val adapter = AuraProblemTypeAdapter()
        assertEquals(
            AuraProblemType.PARASITE,
            adapter.deserialize(JsonPrimitive("PARASITE"), AuraProblemType::class.java, unusedDeserializationContext)
        )
    }

    @Test
    fun auraProblemTypeAdapter_deserialize_unknownValue_fallsBackToOther() {
        val adapter = AuraProblemTypeAdapter()
        assertEquals(
            AuraProblemType.OTHER,
            adapter.deserialize(JsonPrimitive("NOT_A_REAL_PROBLEM"), AuraProblemType::class.java, unusedDeserializationContext)
        )
    }

    @Test
    fun auraProblemTypeAdapter_serialize_writesServerValue() {
        val adapter = AuraProblemTypeAdapter()
        assertEquals(
            JsonPrimitive("HOLE"),
            adapter.serialize(AuraProblemType.HOLE, AuraProblemType::class.java, unusedSerializationContext)
        )
    }

    // --- AuraTypeAdapter ---

    @Test
    fun auraTypeAdapter_deserialize_knownValue_returnsMatchingConstant() {
        val adapter = AuraTypeAdapter()
        assertEquals(
            AuraType.DEMON,
            adapter.deserialize(JsonPrimitive("demon"), AuraType::class.java, unusedDeserializationContext)
        )
    }

    @Test
    fun auraTypeAdapter_deserialize_unknownValue_fallsBackToOther() {
        val adapter = AuraTypeAdapter()
        assertEquals(
            AuraType.OTHER,
            adapter.deserialize(JsonPrimitive("something_new"), AuraType::class.java, unusedDeserializationContext)
        )
    }

    @Test
    fun auraTypeAdapter_serialize_writesServerValue() {
        val adapter = AuraTypeAdapter()
        assertEquals(
            JsonPrimitive("human"),
            adapter.serialize(AuraType.HUMAN, AuraType::class.java, unusedSerializationContext)
        )
    }

    // --- LocalTimeAdapter ---
    // Regression: without this adapter Gson reflects into LocalTime's private fields, which
    // throws on newer Android versions and used to blow up the whole terminal history load
    // (see class kdoc / TerminalHistoryHelper.loadHistory).

    @Test
    fun localTimeAdapter_roundTrip_preservesValue() {
        val adapter = LocalTimeAdapter()
        val original = LocalTime.of(13, 45, 30, 123_000_000)

        val serialized = adapter.serialize(original, LocalTime::class.java, unusedSerializationContext)
        val deserialized = adapter.deserialize(serialized, LocalTime::class.java, unusedDeserializationContext)

        assertEquals(original, deserialized)
    }

    @Test
    fun localTimeAdapter_deserialize_malformedString_fallsBackToMidnight() {
        val adapter = LocalTimeAdapter()
        assertEquals(
            LocalTime.MIDNIGHT,
            adapter.deserialize(JsonPrimitive("not-a-time"), LocalTime::class.java, unusedDeserializationContext)
        )
    }

    @Test
    fun localTimeAdapter_deserialize_emptyString_fallsBackToMidnight() {
        val adapter = LocalTimeAdapter()
        assertEquals(
            LocalTime.MIDNIGHT,
            adapter.deserialize(JsonPrimitive(""), LocalTime::class.java, unusedDeserializationContext)
        )
    }
}
