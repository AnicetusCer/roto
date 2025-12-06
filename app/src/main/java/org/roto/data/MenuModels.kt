package org.roto.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RotoData(
    @SerialName("schema_version") val schemaVersion: String,
    @SerialName("rota_name")
    @JsonNames("school_name")
    val rotaName: String,
    val notes: List<String> = emptyList(),
    val cycle: CycleData,
    @SerialName("special_events")
    @Serializable(with = SpecialEventsSerializer::class)
    val specialEvents: Map<String, List<String>> = emptyMap(),
    val overrides: Map<String, OverrideDay> = emptyMap()
)

@Serializable
data class CycleData(
    val weeks: List<WeekEntry> = emptyList(),
    val repeat: CycleRepeat? = null
)

@Serializable
data class CycleRepeat(
    @SerialName("start_date") val startDate: String,
    @SerialName("start_week_id") val startWeekId: String? = null
)

@Serializable
data class WeekEntry(
    @SerialName("week_id") val weekId: String,
    @SerialName("week_commencing") val weekCommencing: List<String> = emptyList(),
    val days: Map<String, DayDefinition> = emptyMap()
)

@Serializable
data class DayDefinition(
    @SerialName("special_event") val specialEvent: String? = null,
    val slots: List<SlotItem> = emptyList(),
    val notes: List<String> = emptyList()
)

@Serializable
data class SlotItem(
    val label: String,
    val text: String,
    val tags: List<String> = emptyList()
)

@Serializable
data class OverrideDay(
    val closed: Boolean? = null,
    val reason: String? = null,
    @SerialName("special_event") val specialEvent: String? = null,
    val slots: List<SlotItem>? = null,
    val notes: List<String> = emptyList()
)

object RotoJsonParser {
    private const val SUPPORTED_SCHEMA_VERSION = "0.3"
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parse(rawJson: String): RotoData {
        val data = json.decodeFromString(RotoData.serializer(), rawJson)
        if (data.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw IllegalArgumentException("Unsupported schema_version: ${data.schemaVersion}. Expected $SUPPORTED_SCHEMA_VERSION.")
        }
        return data
    }

    fun parseOrNull(rawJson: String): RotoData? =
        runCatching { parse(rawJson) }.getOrNull()
}

/**
 * Accepts either a single string or an array of strings per date key, normalising to List<String>.
 */
object SpecialEventsSerializer : KSerializer<Map<String, List<String>>> {
    private val delegate = MapSerializer(String.serializer(), SpecialEventValueSerializer)

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Map<String, List<String>>) {
        delegate.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): Map<String, List<String>> {
        return delegate.deserialize(decoder).mapValues { (_, v) ->
            v.filter { it.isNotBlank() }
        }.filterValues { it.isNotEmpty() }
    }
}

private object SpecialEventValueSerializer : KSerializer<List<String>> {
    private val listSerializer = ListSerializer(String.serializer())

    override val descriptor: SerialDescriptor = listSerializer.descriptor

    override fun serialize(encoder: Encoder, value: List<String>) {
        val jsonEncoder = encoder as? JsonEncoder
        if (jsonEncoder != null) {
            jsonEncoder.encodeJsonElement(
                if (value.size == 1) JsonPrimitive(value.first()) else JsonArray(value.map { JsonPrimitive(it) })
            )
        } else {
            listSerializer.serialize(encoder, value)
        }
    }

    override fun deserialize(decoder: Decoder): List<String> {
        val jsonDecoder = decoder as? JsonDecoder ?: return emptyList()
        val element: JsonElement = jsonDecoder.decodeJsonElement()
        return when (element) {
            is JsonArray -> element.mapNotNull { it as? JsonPrimitive }
                .mapNotNull { runCatching { it.content }.getOrNull()?.trim() }
            is JsonPrimitive -> runCatching { element.content }.getOrNull()?.trim()?.let { listOf(it) } ?: emptyList()
            else -> emptyList()
        }
    }
}
