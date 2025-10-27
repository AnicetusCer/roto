package org.schooldinners.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import org.schooldinners.data.DayMeals
import org.schooldinners.data.MenuData

data class DayMenuResult(
    val date: LocalDate,
    val dayOfWeek: DayOfWeek,
    val menu: DayMeals,
    val weekId: String,
    val weekCommencing: LocalDate,
    val notes: List<String>
)

fun getMenuForDate(menuData: MenuData, targetDate: LocalDate): DayMenuResult? {
    val mondayOfWeek = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    val matchingWeek = menuData.cycle.weeks.firstOrNull { week ->
        week.weekCommencing.any { dateString ->
            parseIsoDateOrNull(dateString) == mondayOfWeek
        }
    } ?: return null

    val dayMeals = when (targetDate.dayOfWeek) {
        DayOfWeek.MONDAY -> matchingWeek.days.monday
        DayOfWeek.TUESDAY -> matchingWeek.days.tuesday
        DayOfWeek.WEDNESDAY -> matchingWeek.days.wednesday
        DayOfWeek.THURSDAY -> matchingWeek.days.thursday
        DayOfWeek.FRIDAY -> matchingWeek.days.friday
        DayOfWeek.SATURDAY -> matchingWeek.days.saturday
        DayOfWeek.SUNDAY -> matchingWeek.days.sunday
    } ?: return null

    return DayMenuResult(
        date = targetDate,
        dayOfWeek = targetDate.dayOfWeek,
        menu = dayMeals,
        weekId = matchingWeek.weekId,
        weekCommencing = mondayOfWeek,
        notes = menuData.notes
    )
}

private fun parseIsoDateOrNull(value: String): LocalDate? =
    try {
        LocalDate.parse(value)
    } catch (e: DateTimeParseException) {
        null
    }
