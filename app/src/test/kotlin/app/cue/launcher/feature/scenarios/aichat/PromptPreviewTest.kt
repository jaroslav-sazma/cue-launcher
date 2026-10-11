package app.cue.launcher.feature.scenarios.aichat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PromptPreviewTest {

    @Test
    fun `a chunk ends with its paragraph`() {
        assertEquals(
            listOf("# Title\n", "First line\nsecond line\n", "Last"),
            previewChunks("# Title\n\nFirst line\nsecond line\n\nLast"),
        )
    }

    @Test
    fun `a long block is split by lines`() {
        val block = (1..7).joinToString("\n") { "line $it" }

        val chunks = previewChunks(block, maxLines = 3)

        assertEquals(listOf(3, 3, 1), chunks.map { it.lines().size })
    }

    @Test
    fun `the chunks joined by line breaks give the text back exactly`() {
        val long = (1..100).joinToString("\n") { if (it % 9 == 0) "" else "x$it" }

        listOf("", "a", "a\n", "\n\na\n\n\nb\n", long).forEach {
            assertEquals(it, previewChunks(it, maxLines = 4).joinToString("\n"), it)
        }
    }

    @Test
    fun `no chunk holds more lines than allowed`() {
        val brief = (1..1000).joinToString("\n") { "inventory line $it" }

        assertTrue(previewChunks(brief, maxLines = 40).all { it.lines().size <= 40 })
    }
}
