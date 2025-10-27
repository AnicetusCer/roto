package org.schooldinners.data

import java.io.File
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalPathApi::class)
class MenuRepositoryTest {

    private val tempDir: File = createTempDirectory("menu_repo_test").toFile()

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `readFirstExisting returns first readable file`() {
        val missing = File(tempDir, "missing.json")
        val first = File(tempDir, "first.json").apply { writeText("first-content") }
        val second = File(tempDir, "second.json").apply { writeText("second-content") }

        val result = readFirstExisting(listOf(missing, first, second))

        assertEquals("first-content", result)
    }

    @Test
    fun `readFirstExisting returns null when no candidates`() {
        val missing = File(tempDir, "missing.json")
        val result = readFirstExisting(listOf(missing))

        assertNull(result)
    }
}
