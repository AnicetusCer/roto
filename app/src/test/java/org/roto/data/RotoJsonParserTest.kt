package org.roto.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RotoJsonParserTest {

    private fun readAsset(name: String): String {
        val path = File("src/main/assets/$name")
        require(path.exists()) { "Expected asset not found at ${path.absolutePath}" }
        return path.readText()
    }

    private val menuSample by lazy { readAsset("sample_rotas/School_Menu_Rota_with_Closure_and_Theme_Day.json") }
    private val stJamesSample by lazy { readAsset("sample_rotas/StJamesMenu-Nov-v3.json") }

    @Test
    fun `parse returns rota data with weeks notes and slots`() {
        val rotaData = RotoJsonParser.parse(menuSample)

        assertEquals("0.3", rotaData.schemaVersion)
        assertEquals("Wetherby St James C of E Primary", rotaData.rotaName)
        assertEquals(3, rotaData.notes.size)
        assertNull(rotaData.cycle.repeat)

        val weeks = rotaData.cycle.weeks
        assertEquals(3, weeks.size)

        val weekOne = weeks.first()
        assertEquals("Week ONE", weekOne.weekId)
        assertEquals(listOf("2025-11-03", "2025-12-15", "2026-01-05", "2026-01-26"), weekOne.weekCommencing)
        val monday = requireNotNull(weekOne.days["monday"])
        assertEquals("Mains", monday.slots.first().label)
        assertTrue(monday.slots.first().text.contains("Margherita Pizza"))
    }

    @Test
    fun `parse st james rota`() {
        val rota = RotoJsonParser.parse(stJamesSample)

        assertEquals("Wetherby St James C of E Primary", rota.rotaName)
        assertEquals(3, rota.cycle.weeks.size)
        assertNull(rota.cycle.repeat)

        val friday = rota.cycle.weeks[2].days["friday"] ?: error("Expected Friday entry in week 3")
        assertTrue(friday.slots.any { it.text.contains("Fish Fingers", ignoreCase = true) })
    }

    @Test
    fun `parse handles repeat block`() {
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
                    "days": {
                      "monday": {
                        "slots": [
                          { "label": "Duty", "text": "Team A" }
                        ]
                      }
                    }
                  },
                  {
                    "week_id": "Week 2",
                    "week_commencing": [],
                    "days": {
                      "monday": {
                        "slots": [
                          { "label": "Duty", "text": "Team B" }
                        ]
                      }
                    }
                  }
                ]
              },
              "overrides": {}
            }
        """.trimIndent()

        val rota = RotoJsonParser.parse(repeatingJson)

        assertNotNull(rota.cycle.repeat)
        assertEquals("Week 1", rota.cycle.repeat?.startWeekId)
        assertEquals(2, rota.cycle.weeks.size)
    }

    @Test
    fun `parseOrNull returns null for invalid json`() {
        val result = RotoJsonParser.parseOrNull("not-json")
        assertNull(result)
    }
}
