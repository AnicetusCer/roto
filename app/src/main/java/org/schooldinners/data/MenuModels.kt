package org.schooldinners.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class MenuData(
    @SerialName("schema_version") val schemaVersion: String,
    @SerialName("school_name") val schoolName: String,
    val notes: List<String> = emptyList(),
    val cycle: MenuCycle
)

@Serializable
data class MenuCycle(
    val weeks: List<MenuWeek> = emptyList()
)

@Serializable
data class MenuWeek(
    @SerialName("week_id") val weekId: String,
    @SerialName("week_commencing") val weekCommencing: List<String> = emptyList(),
    val days: WeekDays
)

@Serializable
data class WeekDays(
    val monday: DayMeals? = null,
    val tuesday: DayMeals? = null,
    val wednesday: DayMeals? = null,
    val thursday: DayMeals? = null,
    val friday: DayMeals? = null,
    val saturday: DayMeals? = null,
    val sunday: DayMeals? = null
)

@Serializable
data class DayMeals(
    val main: String,
    @SerialName("alt_hot") val altHot: String,
    @SerialName("deli_option") val deliOption: String,
    val dessert: String
)

object MenuJsonParser {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parse(rawJson: String): MenuData =
        json.decodeFromString(MenuData.serializer(), rawJson)

    fun parseOrNull(rawJson: String): MenuData? =
        runCatching { parse(rawJson) }.getOrNull()
}
