package app.cue.launcher.designsystem.glyph

import app.cue.launcher.feature.home.HA_GLYPH
import app.cue.launcher.feature.home.SYSTEM_ACTION_GLYPHS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File

class MdiIndexAssetTest {

    @Test
    fun `the index is read in full`() {
        assertEquals(MDI_7_4_47_GLYPHS, glyphs.names.size)
    }

    @TestFactory
    fun `action glyphs are in the index`(): List<DynamicTest> =
        (SYSTEM_ACTION_GLYPHS.values + HA_GLYPH).map { name ->
            DynamicTest.dynamicTest(name) { assertNotNull(glyphs.glyphOf(name), name) }
        }

    @TestFactory
    fun `UI glyphs are in the index`(): List<DynamicTest> =

        listOf("magnify", "close").map { name ->
            DynamicTest.dynamicTest(name) { assertNotNull(glyphs.glyphOf(name), name) }
        }

    @Test
    fun `codepoints lie within the private zone`() {
        val outside = glyphs.names.filter { name ->
            val codePoint = glyphs.codePointOf(name) ?: 0
            codePoint !in PRIVATE_USE_A
        }

        assertTrue(outside.isEmpty()) { "outside the private zone: ${outside.take(5)}" }
    }

    private companion object {

        const val MDI_7_4_47_GLYPHS = 7447

        val PRIVATE_USE_A = 0xF0000..0xFFFFD

        val INDEX: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/assets/$MDI_INDEX_ASSET") }
            .first { it.isFile }

        val glyphs: MdiGlyphs = INDEX.useLines { parseMdiGlyphs(it) }
    }
}
