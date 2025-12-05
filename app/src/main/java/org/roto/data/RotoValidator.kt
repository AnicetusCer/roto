package org.roto.data

object RotoValidator {

    fun validate(rotaData: RotoData): List<String> {
        val issues = mutableListOf<String>()

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
            week.days.forEach { (dayKey, dayDef) ->
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
            val weekIds = weeks.map { it.weekId }.toSet()
            if (repeat.startWeekId != null && repeat.startWeekId !in weekIds) {
                issues += "cycle.repeat.start_week_id \"${repeat.startWeekId}\" does not match any week_id."
            }
        }

        rotaData.overrides.forEach { (date, override) ->
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
            val parsed = runCatching { java.time.LocalDate.parse(date) }.getOrNull()
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
}
