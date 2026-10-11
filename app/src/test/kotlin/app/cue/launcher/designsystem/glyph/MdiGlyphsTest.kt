package app.cue.launcher.designsystem.glyph

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class MdiGlyphsTest {

    @ParameterizedTest
    @ValueSource(strings = ["gate", "mdi:gate", "mdi-gate", "MDI:Gate", "  mdi:gate  ", "GATE"])
    fun `a glyph name normalizes to its canonical form`(raw: String) {
        assertEquals("gate", mdiGlyphName(raw))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "mdi:", "mdi-"])
    fun `an empty name means no icon set`(raw: String) {
        assertNull(mdiGlyphName(raw))
    }

    @Test
    fun `an unset icon is null`() {
        assertNull(mdiGlyphName(null))
    }

    @Test
    fun `writing to the format carries the mdi prefix`() {
        assertEquals("mdi:gate", mdiIconReference("gate"))
        assertEquals("gate", mdiGlyphName(mdiIconReference("gate")))
    }

    @Test
    fun `the registry translates a name to a codepoint`() {
        val glyphs = parseMdiGlyphs(sequenceOf("gate F0299", "flashlight F0244"))

        assertEquals(0xF0299, glyphs.codePointOf("mdi:gate"))
        assertEquals(0xF0244, glyphs.codePointOf("flashlight"))
        assertEquals(setOf("gate", "flashlight"), glyphs.names)
    }

    @Test
    fun `a glyph is a surrogate pair`() {
        val glyph = parseMdiGlyphs(sequenceOf("gate F0299")).glyphOf("gate")

        assertEquals(2, glyph?.length)
        assertEquals(0xF0299, glyph?.codePointAt(0))
    }

    @Test
    fun `an unknown glyph is not drawn`() {
        val glyphs = parseMdiGlyphs(sequenceOf("gate F0299"))

        assertNull(glyphs.glyphOf("tohle-v-mdi-neni"))
        assertNull(glyphs.glyphOf(null))
    }

    @Test
    fun `unreadable lines are skipped`() {
        val glyphs = parseMdiGlyphs(
            sequenceOf(
                "# hlavička s licencí",
                "",
                "bez-codepointu",
                "spatny-codepoint ZZZZZ",
                " F0299",
                "gate F0299",
            ),
        )

        assertEquals(setOf("gate"), glyphs.names)
    }

    @Test
    fun `an empty registry is empty`() {
        assertTrue(MdiGlyphs.EMPTY.isEmpty)
        assertNull(MdiGlyphs.EMPTY.glyphOf("gate"))
    }
}
