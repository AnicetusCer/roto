package org.roto.domain

import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.roto.data.RotoJsonParser
import org.roto.domain.DayDataSource

class MenuRotationTest {

    private lateinit var rotaJson: String

    @Before
    fun loadRota() {
        val path = File("src/main/assets/wetherby_st_james_n3_nov25_menu.json")
        require(path.exists()) { "Expected rota asset at ${path.absolutePath}" }
        rotaJson = path.readText()
    }

    @Test
    fun `returns week one monday rota slots`() {
        val rota = RotoJsonParser.parse(rotaJson)
        val result = getMenuForDate(rota, LocalDate.parse("2025-11-03"))

        assertNotNull(result)
        result!!
        assertEquals("Week 1", result.weekId)
        assertEquals(DayDataSource.ROTATION, result.source)
        assertEquals("Option 1", result.slots.first().label)
        assertTrue(result.slots.first().text.contains("Chicken Pie"))
    }

    @Test
    fun `returns week two friday rota`() {
        val rota = RotoJsonParser.parse(rotaJson)
        val result = getMenuForDate(rota, LocalDate.parse("2025-12-05"))

        assertNotNull(result)
        result!!
        assertEquals("Week 2", result.weekId)
        assertEquals(DayDataSource.ROTATION, result.source)
        assertTrue(result.slots.any { it.text.contains("Crunchy Salmon Bites") || it.text.contains("Fish Fingers") })
    }

    @Test
    fun `returns null for weekend without menu`() {
        val rota = RotoJsonParser.parse(rotaJson)
        val result = getMenuForDate(rota, LocalDate.parse("2025-11-08")) // Saturday

        assertNull(result)
    }

    @Test
    fun `returns null when monday is not registered`() {
        val rota = RotoJsonParser.parse(rotaJson)
        val result = getMenuForDate(rota, LocalDate.parse("2025-10-20"))

        assertNull(result)
    }
}
