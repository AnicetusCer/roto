package org.roto.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RotoJsonParserTest {

    private fun readAsset(name: String): String {
        val candidates = listOf(
            File("src/main/assets/$name"),
            File("app/src/main/assets/$name")
        )
        val path = candidates.firstOrNull(File::exists)
            ?: error("Expected asset not found in src/main/assets or app/src/main/assets for $name")
        return path.readText()
    }

    private val menuSample by lazy { readAsset("sample_rotas/Simple_School_Menu_Infinite.json") }

    @Test
    fun `parse returns rota data with weeks notes and slots`() {
        val rotaData = RotoJsonParser.parse(menuSample)

        assertEquals("0.3", rotaData.schemaVersion)
        assertEquals("Springfield Primary Sample Menu", rotaData.rotaName)
        assertEquals(2, rotaData.notes.size)
        assertNotNull(rotaData.cycle.repeat)
        assertEquals("Week 1", rotaData.cycle.repeat?.startWeekId)
        assertEquals("2025-10-27", rotaData.cycle.repeat?.startDate)

        val weeks = rotaData.cycle.weeks
        assertEquals(1, weeks.size)

        val weekOne = weeks.first()
        assertEquals("Week 1", weekOne.weekId)
        assertEquals(listOf("2025-10-27"), weekOne.weekCommencing)
        val monday = requireNotNull(weekOne.days["monday"])
        assertEquals("Main", monday.slots.first().label)
        assertTrue(monday.slots.first().text.contains("Chicken pasta bake"))
    }

    @Test
    fun `parse school menu rota`() {
        val rota = RotoJsonParser.parse(menuSample)

        assertEquals("Springfield Primary Sample Menu", rota.rotaName)
        assertEquals(1, rota.cycle.weeks.size)
        assertNotNull(rota.cycle.repeat)

        val friday = rota.cycle.weeks.first().days["friday"] ?: error("Expected Friday entry in sample rota")
        assertTrue(friday.slots.any { it.text.contains("pizza", ignoreCase = true) })
    }

    @Test
    fun `parse handles repeat block`() {
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
