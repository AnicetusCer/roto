package org.schooldinners.data

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import java.io.File

class MenuJsonParserTest {

    private fun readAsset(name: String): String {
        val path = File("src/main/assets/$name")
        require(path.exists()) { "Expected asset not found at ${path.absolutePath}" }
        return path.readText()
    }

    private val sampleJson by lazy { readAsset("sample_menu.json") }

    @Test
    fun `parse returns menu data with weeks and notes`() {
        val menuData = MenuJsonParser.parse(sampleJson)

        assertEquals("0.2", menuData.schemaVersion)
        assertEquals("Example Primary School", menuData.schoolName)
        assertEquals(2, menuData.notes.size)

        val weeks = menuData.cycle.weeks
        assertEquals(2, weeks.size)

        val weekOne = weeks.first()
        assertEquals("Week 1", weekOne.weekId)
        assertEquals(listOf("2025-11-03", "2025-11-24"), weekOne.weekCommencing)
        assertNotNull(weekOne.days.monday)
        assertEquals("Chicken Pie, Mash, Veg, Gravy", weekOne.days.monday?.main)
    }

    @Test
    fun `parseOrNull returns null for invalid json`() {
        val result = MenuJsonParser.parseOrNull("not-json")
        assertNull(result)
    }

    @Test
    fun `parse november menu`() {
        val raw = readAsset("wetherby_st_james_n3_nov25_menu.json")
        val menu = MenuJsonParser.parse(raw)

        assertEquals("0.2", menu.schemaVersion)
        assertEquals("Wetherby St James C of E Primary", menu.schoolName)
        assertEquals(3, menu.cycle.weeks.size)

        val weekOne = menu.cycle.weeks.first()
        assertEquals("Week 1", weekOne.weekId)
        val weekOneMonday = weekOne.days.monday ?: error("Expected Monday menu for week one")
        assertEquals("Chicken Pie with Puff Pastry Crust, Mashed Potato, Broccoli, Carrots & Gravy", weekOneMonday.main)
        assertEquals("Melon Slices & Home Baked Shortbread", weekOneMonday.dessert)

        val weekThreeFriday = menu.cycle.weeks[2].days.friday ?: error("Expected Friday menu for week three")
        assertEquals("Fish Fingers (H) with chips, peas & tomato ketchup", weekThreeFriday.main)
        assertEquals("Freshly Baked Apple Pie Cookies with Fresh Fruit Wedges", weekThreeFriday.dessert)
    }
}
