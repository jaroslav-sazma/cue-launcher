package app.cue.launcher.feature.scenarios.builder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GlyphSearchTest {

    @Test
    fun `an empty query returns the whole index alphabetically`() {
        val index = glyphIndexOf(NAMES)

        assertEquals(NAMES.sorted(), index.search(""))
        assertEquals(NAMES.sorted(), index.search("   "))
        assertEquals(NAMES.sorted(), index.names)
    }

    @Test
    fun `order is exact then prefix then word`() {
        val found = glyphIndexOf(NAMES).search("gate")

        assertEquals(listOf("gate", "gate-open", "gate-alert", "boom-gate"), found.take(FOUR))
    }

    @Test
    fun `equal quality ranks the shorter name first`() {
        val found = glyphIndexOf(listOf("volume-off-outline", "volume-off", "volume-high")).search("volume-o")

        assertEquals(listOf("volume-off", "volume-off-outline"), found.take(2))
    }

    @Test
    fun `the query tolerates an mdi prefix and uppercase letters`() {
        val index = glyphIndexOf(NAMES)

        assertEquals("gate", index.search("mdi:gate").first())
        assertEquals("gate", index.search("MDI:Gate").first())
    }

    @Test
    fun `a space substitutes for a hyphen`() {
        val index = glyphIndexOf(NAMES)

        assertEquals("washing-machine", index.search("washing machine").first())
        assertEquals("washing-machine", index.search("washing-machine").first())
    }

    @Test
    fun `a typo still finds the glyph`() {
        assertEquals("washing-machine", glyphIndexOf(NAMES).search("wahsing").first())
    }

    @Test
    fun `a word in the middle of the name is found`() {
        assertTrue(glyphIndexOf(NAMES).search("machine").contains("washing-machine"))
    }

    @Test
    fun `nothing matching returns empty`() {
        assertEquals(emptyList<String>(), glyphIndexOf(NAMES).search("zzzzzz"))
    }

    @Test
    fun `an empty index has nothing to offer`() {
        val empty = glyphIndexOf(emptyList())
        assertEquals(emptyList<String>(), empty.search("gate"))
        assertEquals(emptyList<String>(), empty.names)
    }

    private companion object {

        val NAMES = listOf(
            "gate",
            "gate-open",
            "gate-alert",
            "boom-gate",
            "washing-machine",
            "volume-off",
            "home-assistant",
        )

        const val FOUR = 4
    }
}
