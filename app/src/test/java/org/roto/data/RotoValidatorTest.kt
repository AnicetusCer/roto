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
}
