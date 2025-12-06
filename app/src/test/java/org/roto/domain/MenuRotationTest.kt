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

    private fun readAsset(name: String): String {
        val candidates = listOf(
            File("src/main/assets/$name"),
            File("app/src/main/assets/$name")
        )
        val path = candidates.firstOrNull(File::exists)
            ?: error("Expected rota asset for $name")
        return path.readText()
    }

    @Before
    fun loadRota() {
        rotaData = RotoJsonParser.parse(readAsset("sample_rotas/Simple_School_Menu_Infinite.json"))
    }

    @Test
    fun `returns week one monday rota slots`() {
        val result = getMenuForDate(rotaData, LocalDate.parse("2025-11-03"))

        assertNotNull(result)
        result!!
        assertEquals("Week 1", result.weekId)
        assertEquals(DayDataSource.ROTATION, result.source)
        assertEquals("Main", result.slots.first().label)
        assertTrue(result.slots.first().text.contains("Chicken pasta bake"))
    }

    @Test
    fun `repeats rota for future friday`() {
        val result = getMenuForDate(rotaData, LocalDate.parse("2025-11-07"))

        assertNotNull(result)
        result!!
        assertEquals("Week 1", result.weekId)
        assertEquals(DayDataSource.ROTATION, result.source)
        assertTrue(result.slots.any { it.text.contains("pizza", ignoreCase = true) })
    }

    @Test
    fun `returns null for dates before rotation starts`() {
        val result = getMenuForDate(rotaData, LocalDate.parse("2025-10-25")) // Saturday before anchor

        assertNull(result)
    }

    @Test
    fun `loops rota beyond explicit anchors`() {
        val repeatingJson = """
            {
              "schema_version": "0.3",
              "rota_name": "Looping Example",
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
