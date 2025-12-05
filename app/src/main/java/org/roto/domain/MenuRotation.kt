package org.roto.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import java.time.temporal.ChronoUnit
import java.util.Locale
import org.roto.data.CycleData
import org.roto.data.DayDefinition
import org.roto.data.OverrideDay
import org.roto.data.RotoData
import org.roto.data.SlotItem
import org.roto.data.WeekEntry

data class SlotEntry(
    val label: String,
    val text: String,
    val tags: List<String>
)

enum class DayDataSource { ROTATION, OVERRIDE }

data class DayResult(
    val date: LocalDate,
    val dayOfWeek: DayOfWeek,
    val formattedDate: String,
    val slots: List<SlotEntry>,
    val notes: List<String>,
    val specialEvents: List<String>,
    val globalNotes: List<String>,
    val isClosed: Boolean,
    val closedReason: String?,
    val weekId: String?,
    val weekCommencing: LocalDate?,
    val source: DayDataSource
)

private val displayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d MMM yyyy", Locale.getDefault())

fun getMenuForDate(rotaData: RotoData, targetDate: LocalDate): DayResult? {
    val mondayOfWeek = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    val matchingWeek = findMatchingWeek(rotaData.cycle, mondayOfWeek)

    val baseDay = matchingWeek?.resolveDayDefinition(targetDate)
    val overrideDay = rotaData.overrides[targetDate.toString()]
    val mappedSpecialEvents = rotaData.specialEvents[targetDate.toString()]

    val resolved = resolveDay(
        base = baseDay,
        override = overrideDay,
        week = matchingWeek,
        mondayOfWeek = mondayOfWeek,
        mappedSpecialEvents = mappedSpecialEvents
    ) ?: return null

    return DayResult(
        date = targetDate,
        dayOfWeek = targetDate.dayOfWeek,
        formattedDate = displayFormatter.format(targetDate),
        slots = resolved.slots,
        notes = resolved.notes,
        specialEvents = resolved.specialEvents,
        globalNotes = rotaData.notes,
        isClosed = resolved.isClosed,
        closedReason = resolved.reason,
        weekId = resolved.weekId,
        weekCommencing = resolved.weekCommencing,
        source = resolved.source
    )
}

private fun findMatchingWeek(cycle: CycleData, mondayOfWeek: LocalDate): WeekEntry? {
    cycle.weeks.firstOrNull { week ->
        week.weekCommencing.any { dateString ->
            parseIsoDateOrNull(dateString) == mondayOfWeek
        }
    }?.let { return it }

    val repeat = cycle.repeat ?: return null
    val weeks = cycle.weeks
    if (weeks.isEmpty()) return null

    val anchorDate = parseIsoDateOrNull(repeat.startDate)?.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        ?: return null

    val weeksBetween = ChronoUnit.WEEKS.between(anchorDate, mondayOfWeek)
    if (weeksBetween < 0) return null

    val anchorIndex = repeat.startWeekId?.let { id ->
        weeks.indexOfFirst { it.weekId == id }
    }?.takeIf { it >= 0 } ?: 0

    val weekCount = weeks.size
    val offset = (weeksBetween % weekCount.toLong()).toInt()
    val normalizedIndex = (anchorIndex + offset) % weekCount
    return weeks[normalizedIndex]
}

private data class ResolvedDay(
    val slots: List<SlotEntry>,
    val notes: List<String>,
    val specialEvents: List<String>,
    val isClosed: Boolean,
    val reason: String?,
    val weekId: String?,
    val weekCommencing: LocalDate?,
    val source: DayDataSource
)

private fun resolveDay(
    base: DayDefinition?,
    override: OverrideDay?,
    week: WeekEntry?,
    mondayOfWeek: LocalDate,
    mappedSpecialEvents: List<String>?
): ResolvedDay? {
    if (override == null && base == null && mappedSpecialEvents.isNullOrEmpty()) return null

    val isClosed = override?.closed == true
    val reason = override?.reason
    val selectedSlots = when {
        isClosed -> emptyList()
        override?.slots != null -> override.slots.map { it.toSlotEntry() }
        base != null -> base.slots.map { it.toSlotEntry() }
        else -> emptyList()
    }

    val selectedNotes = buildList {
        base?.notes?.let { addAll(it) }
        override?.notes?.let { addAll(it) }
    }

    val specialEvents = buildList {
        override?.specialEvent?.takeIf { it.isNotBlank() }?.let { add(it) }
        base?.specialEvent?.takeIf { it.isNotBlank() }?.let { add(it) }
        mappedSpecialEvents?.let { addAll(it.filter { msg -> msg.isNotBlank() }) }
    }.distinct()

    val weekId = week?.weekId
    val weekCommencing = week?.let { mondayOfWeek }

    val source = if (override != null) DayDataSource.OVERRIDE else DayDataSource.ROTATION

    return ResolvedDay(
        slots = selectedSlots,
        notes = selectedNotes,
        specialEvents = specialEvents,
        isClosed = isClosed,
        reason = reason,
        weekId = weekId,
        weekCommencing = weekCommencing,
        source = source
    )
}

private fun WeekEntry.resolveDayDefinition(targetDate: LocalDate): DayDefinition? {
    val weekdayKey = targetDate.dayOfWeek.name.lowercase(Locale.ROOT)
    return days[weekdayKey]
        ?: days[targetDate.toString()]
}

private fun SlotItem.toSlotEntry(): SlotEntry =
    SlotEntry(
        label = label,
        text = text,
        tags = tags
    )

private fun parseIsoDateOrNull(value: String): LocalDate? =
    try {
        LocalDate.parse(value)
    } catch (e: DateTimeParseException) {
        null
    }
