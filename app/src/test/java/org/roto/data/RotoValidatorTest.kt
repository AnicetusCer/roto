package org.roto.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RotoValidatorTest {

    @Test
    fun `detects missing week data`() {
        val rota = RotoData(
            schemaVersion = "0.3",
            rotaName = "Test",
            notes = emptyList(),
            cycle = CycleData(
                weeks = listOf(
                    WeekEntry(
                        weekId = "Week 1",
                        weekCommencing = listOf("2025-01-06"),
                        days = mapOf(
                            "monday" to DayDefinition(
                                slots = emptyList()
                            )
                        )
                    )
                )
            ),
            overrides = emptyMap()
        )

        val issues = RotoValidator.validate(rota)

        assertEquals(1, issues.size)
        assertTrue(issues.first().contains("Week 1 → monday has no slots"))
    }

    @Test
    fun `detects repeat week mismatch`() {
        val rota = RotoData(
            schemaVersion = "0.3",
            rotaName = "Repeat mismatch",
            notes = emptyList(),
            cycle = CycleData(
                repeat = CycleRepeat(startDate = "2025-09-01", startWeekId = "Week X"),
                weeks = listOf(
                    WeekEntry(
                        weekId = "Week 1",
                        weekCommencing = listOf("2025-09-01"),
                        days = mapOf(
                            "monday" to DayDefinition(
                                slots = listOf(
                                    SlotItem(label = "Slot", text = "Something")
                                )
                            )
                        )
                    )
                )
            ),
            overrides = emptyMap()
        )

        val issues = RotoValidator.validate(rota)

        assertTrue(issues.any { it.contains("cycle.repeat.start_week_id") })
    }

    @Test
    fun `detects invalid dates and day keys`() {
        val rota = RotoData(
            schemaVersion = "0.3",
            rotaName = "Bad dates",
            notes = emptyList(),
            cycle = CycleData(
                repeat = CycleRepeat(startDate = "2025-09-02", startWeekId = "Week 1"),
                weeks = listOf(
                    WeekEntry(
                        weekId = "Week 1",
                        weekCommencing = listOf("2025-09-02", "not-a-date"),
                        days = mapOf(
                            "funday" to DayDefinition(
                                slots = listOf(SlotItem(label = "Slot", text = "Something"))
                            )
                        )
                    )
                )
            ),
            overrides = mapOf(
                "tomorrow" to OverrideDay(closed = true, reason = "Closed")
            )
        )

        val issues = RotoValidator.validate(rota)

        assertTrue(issues.any { it.contains("week_commencing \"2025-09-02\" must be a Monday") })
        assertTrue(issues.any { it.contains("week_commencing \"not-a-date\"") })
        assertTrue(issues.any { it.contains("day key \"funday\"") })
        assertTrue(issues.any { it.contains("cycle.repeat.start_date \"2025-09-02\" must be a Monday") })
        assertTrue(issues.any { it.contains("Override key \"tomorrow\"") })
    }

    @Test
    fun `accepts iso date day keys`() {
        val rota = RotoData(
            schemaVersion = "0.3",
            rotaName = "Date keyed day",
            notes = emptyList(),
            cycle = CycleData(
                weeks = listOf(
                    WeekEntry(
                        weekId = "Week 1",
                        weekCommencing = listOf("2025-09-01"),
                        days = mapOf(
                            "2025-09-03" to DayDefinition(
                                slots = listOf(SlotItem(label = "Event", text = "Sports day"))
                            )
                        )
                    )
                )
            ),
            overrides = emptyMap()
        )

        val issues = RotoValidator.validate(rota)

        assertTrue(issues.isEmpty())
    }
}
