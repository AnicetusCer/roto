package org.roto.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RotoData(
    @SerialName("schema_version") val schemaVersion: String,
    @SerialName("school_name") val rotaName: String,
    val notes: List<String> = emptyList(),
    val cycle: CycleData,
    @SerialName("special_events") val specialEvents: Map<String, String> = emptyMap(),
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
