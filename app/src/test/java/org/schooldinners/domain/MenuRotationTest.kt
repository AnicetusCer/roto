package org.schooldinners.domain

import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.schooldinners.data.MenuJsonParser

class MenuRotationTest {

    private lateinit var menuJson: String

    @Before
    fun loadMenu() {
        val path = File("src/main/assets/wetherby_st_james_n3_nov25_menu.json")
        require(path.exists()) { "Expected menu asset at ${path.absolutePath}" }
        menuJson = path.readText()
    }

    @Test
    fun `returns week one monday menu`() {
        val menu = MenuJsonParser.parse(menuJson)
        val result = getMenuForDate(menu, LocalDate.parse("2025-11-03"))

        assertNotNull(result)
        result!!
        assertEquals("Week 1", result.weekId)
        assertEquals("Chicken Pie with Puff Pastry Crust, Mashed Potato, Broccoli, Carrots & Gravy", result.menu.main)
        assertEquals("Melon Slices & Home Baked Shortbread", result.menu.dessert)
    }

    @Test
    fun `returns week two friday menu`() {
        val menu = MenuJsonParser.parse(menuJson)
        val result = getMenuForDate(menu, LocalDate.parse("2025-12-05"))

        assertNotNull(result)
        result!!
        assertEquals("Week 2", result.weekId)
        assertEquals(
            "Crunchy Salmon Bites or Fish Fingers (H) with chips, tomato ketchup, sweetcorn & green beans",
            result.menu.main
        )
        assertEquals("Chocolate Brownie with Fresh Fruit Wedges", result.menu.dessert)
    }

    @Test
    fun `returns null for weekend without menu`() {
        val menu = MenuJsonParser.parse(menuJson)
        val result = getMenuForDate(menu, LocalDate.parse("2025-11-08")) // Saturday

        assertNull(result)
    }

    @Test
    fun `returns null when monday is not registered`() {
        val menu = MenuJsonParser.parse(menuJson)
        val result = getMenuForDate(menu, LocalDate.parse("2025-10-20"))

        assertNull(result)
    }
}
