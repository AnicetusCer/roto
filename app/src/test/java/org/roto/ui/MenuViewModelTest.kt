package org.roto.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuViewModelTest {

    @Test
    fun `buildPastedRotaFileName uses first available rota number`() {
        val existing = setOf(
            "rota_1.json",
            "rota_2.json",
            "notes.txt",
            "rota_4.json"
        )

        assertEquals("rota_3.json", buildPastedRotaFileName(existing))
    }

    @Test
    fun `buildPastedRotaFileName starts at rota one`() {
        assertEquals("rota_1.json", buildPastedRotaFileName(emptySet()))
    }
}
