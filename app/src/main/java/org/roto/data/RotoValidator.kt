package org.roto.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

object RotoValidator {

    fun validate(rotaData: RotoData): List<String> {
        val issues = mutableListOf<String>()

        if (rotaData.rotaName.isBlank()) {
            issues += "rota_name is missing or blank."
        }

        val weeks = rotaData.cycle.weeks
        if (weeks.isEmpty()) {
            issues += "No weeks defined in the rota."
        }

        weeks.forEachIndexed { index, week ->
            val displayWeek = week.weekId.ifBlank { "Week ${index + 1}" }
            if (week.weekId.isBlank()) {
                issues += "$displayWeek is missing a week_id."
            }
            if (week.days.isEmpty()) {
                issues += "$displayWeek has no days."
            }
            week.weekCommencing.forEach { date ->
                val parsed = parseIsoDateOrNull(date)
                when {
                    parsed == null -> issues += "$displayWeek week_commencing \"$date\" is not a valid ISO date (YYYY-MM-DD)."
                    parsed.dayOfWeek != DayOfWeek.MONDAY -> issues += "$displayWeek week_commencing \"$date\" must be a Monday."
                }
            }
            week.days.forEach { (dayKey, dayDef) ->
                if (!isValidDayKey(dayKey)) {
                    issues += "$displayWeek day key \"$dayKey\" must be a weekday name or an ISO date (YYYY-MM-DD)."
                }
                if (dayDef.slots.isEmpty()) {
                    issues += "$displayWeek → $dayKey has no slots."
                }
                dayDef.slots.forEachIndexed { slotIndex, slot ->
                    val slotName = "$displayWeek → $dayKey slot ${slotIndex + 1}"
                    if (slot.label.isBlank()) {
                        issues += "$slotName is missing a label."
                    }
                    if (slot.text.isBlank()) {
                        issues += "$slotName is missing its description."
                    }
                }
            }
        }

        rotaData.cycle.repeat?.let { repeat ->
            val parsedStart = parseIsoDateOrNull(repeat.startDate)
            when {
                parsedStart == null -> issues += "cycle.repeat.start_date \"${repeat.startDate}\" is not a valid ISO date (YYYY-MM-DD)."
                parsedStart.dayOfWeek != DayOfWeek.MONDAY -> issues += "cycle.repeat.start_date \"${repeat.startDate}\" must be a Monday."
            }
            val weekIds = weeks.map { it.weekId }.toSet()
            if (repeat.startWeekId != null && repeat.startWeekId !in weekIds) {
                issues += "cycle.repeat.start_week_id \"${repeat.startWeekId}\" does not match any week_id."
            }
        }

        rotaData.overrides.forEach { (date, override) ->
            if (parseIsoDateOrNull(date) == null) {
                issues += "Override key \"$date\" is not a valid ISO date (YYYY-MM-DD)."
            }
            val hasContent = override.closed == true ||
                !override.reason.isNullOrBlank() ||
                !override.specialEvent.isNullOrBlank() ||
                !override.notes.isEmpty() ||
                !(override.slots.isNullOrEmpty())
            if (!hasContent) {
                issues += "Override $date does not include closed=true, slots, reason, special_event, or notes."
            }
        }

        rotaData.specialEvents.forEach { (date, messages) ->
            val parsed = parseIsoDateOrNull(date)
            if (parsed == null) {
                issues += "special_events key \"$date\" is not a valid ISO date (YYYY-MM-DD)."
            }
            if (messages.isEmpty()) {
                issues += "special_events \"$date\" has no messages."
            }
            messages.forEach { msg ->
                if (msg.isBlank()) {
                    issues += "special_events \"$date\" contains an empty message."
                }
            }
        }

        return issues
    }

    private fun isValidDayKey(dayKey: String): Boolean {
        val normalized = dayKey.lowercase(Locale.ROOT)
        if (normalized in validWeekdayKeys) return true
        return parseIsoDateOrNull(dayKey) != null
    }

    private fun parseIsoDateOrNull(value: String): LocalDate? =
        try {
            LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            null
        }

    private val validWeekdayKeys = setOf(
        "monday",
        "tuesday",
        "wednesday",
        "thursday",
        "friday",
        "saturday",
        "sunday"
    )
}
