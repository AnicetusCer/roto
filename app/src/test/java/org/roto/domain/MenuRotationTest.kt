package org.roto.domain

import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.roto.data.RotoData
import org.roto.data.RotoJsonParser

class MenuRotationTest {

    private lateinit var rotaData: RotoData

    @Before
    fun loadRota() {
        val path = File("src/main/assets/sample_rotas/School_Menu_Rota_with_Closure_and_Theme_Day.json")
        require(path.exists()) { "Expected rota asset at ${path.absolutePath}" }
        rotaData = RotoJsonParser.parse(path.readText())
    }

    @Test
    fun `returns week one monday rota slots`() {
        val result = getMenuForDate(rotaData, LocalDate.parse("2025-11-03"))

        assertNotNull(result)
        result!!
        assertEquals("Week ONE", result.weekId)
        assertEquals(DayDataSource.ROTATION, result.source)
        assertEquals("Mains", result.slots.first().label)
        assertTrue(result.slots.first().text.contains("Margherita Pizza"))
    }

    @Test
    fun `returns week two friday rota`() {
        val result = getMenuForDate(rotaData, LocalDate.parse("2025-11-14"))

        assertNotNull(result)
        result!!
        assertEquals("Week TWO", result.weekId)
        assertEquals(DayDataSource.ROTATION, result.source)
        assertTrue(result.slots.any { it.text.contains("Fish Fingers") })
    }

    @Test
    fun `returns null for weekend without rota entry`() {
        val result = getMenuForDate(rotaData, LocalDate.parse("2025-11-08")) // Saturday

        assertNull(result)
    }

    @Test
    fun `loops rota beyond explicit anchors`() {
        val repeatingJson = """
            {
              "schema_version": "0.3",
              "school_name": "Looping Example",
              "notes": [],
              "cycle": {
                "repeat": {
                  "start_date": "2025-09-01",
                  "start_week_id": "Week 1"
                },
                "weeks": [
                  {
                    "week_id": "Week 1",
                    "week_commencing": [],
                    "days": { "monday": { "slots": [ { "label": "Duty", "text": "Team A" } ] } }
                  },
                  {
                    "week_id": "Week 2",
                    "week_commencing": [],
                    "days": { "monday": { "slots": [ { "label": "Duty", "text": "Team B" } ] } }
                  }
                ]
              },
              "overrides": {}
            }
        """.trimIndent()

        val loopingRota = RotoJsonParser.parse(repeatingJson)
        val result = getMenuForDate(loopingRota, LocalDate.parse("2026-02-16"))

        assertNotNull(result)
        result!!
        assertEquals("Week 1", result.weekId)
    }

    @Test
    fun `returns null when monday is not registered`() {
        val result = getMenuForDate(rotaData, LocalDate.parse("2025-10-20"))

        assertNull(result)
    }
}
