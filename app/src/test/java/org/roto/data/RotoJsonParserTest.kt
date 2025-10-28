package org.roto.data

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.io.File

class RotoJsonParserTest {

    private fun readAsset(name: String): String {
        val path = File("src/main/assets/$name")
        require(path.exists()) { "Expected asset not found at ${path.absolutePath}" }
        return path.readText()
    }

    private val sampleJson by lazy { readAsset("sample_menu.json") }

    @Test
    fun `parse returns rota data with weeks notes and slots`() {
        val rotaData = RotoJsonParser.parse(sampleJson)

        assertEquals("0.3", rotaData.schemaVersion)
        assertEquals("Example Primary School", rotaData.rotaName)
        assertEquals(2, rotaData.notes.size)
        assertNotNull(rotaData.cycle.repeat)
        assertEquals("2025-11-03", rotaData.cycle.repeat?.startDate)

        val weeks = rotaData.cycle.weeks
        assertEquals(2, weeks.size)

        val weekOne = weeks.first()
        assertEquals("Week 1", weekOne.weekId)
        assertEquals(listOf("2025-11-03", "2025-11-24"), weekOne.weekCommencing)
        val monday = requireNotNull(weekOne.days["monday"])
        assertEquals(4, monday.slots.size)
        assertEquals("Option 1", monday.slots.first().label)
        assertTrue(monday.slots.first().text.contains("Chicken Pie"))
    }

    @Test
    fun `parseOrNull returns null for invalid json`() {
        val result = RotoJsonParser.parseOrNull("not-json")
        assertNull(result)
    }

    @Test
    fun `parse november rota`() {
        val raw = readAsset("wetherby_st_james_n3_nov25_menu.json")
        val rota = RotoJsonParser.parse(raw)

        assertEquals("0.3", rota.schemaVersion)
        assertEquals("Wetherby St James C of E Primary", rota.rotaName)
        assertEquals(3, rota.cycle.weeks.size)
        assertEquals("2025-11-03", rota.cycle.repeat?.startDate)

        val weekOne = rota.cycle.weeks.first()
        assertEquals("Week 1", weekOne.weekId)
        val monday = requireNotNull(weekOne.days["monday"])
        assertEquals("Option 1", monday.slots.first().label)
        assertTrue(monday.slots.any { it.text.contains("Chicken Pie") })

        val weekThreeFriday = rota.cycle.weeks[2].days["friday"] ?: error("Expected Friday slots for week three")
        assertTrue(weekThreeFriday.slots.any { it.text.contains("Fish Fingers") })
    }
}
