package app.cue.launcher.feature.drawer.vault

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AzIndexTest {

    @Test
    fun `the column splits into equal bands`() {

        assertEquals(0, azLetterAt(y = 0f, heightPx = 400f, count = 4))
        assertEquals(0, azLetterAt(y = 99f, heightPx = 400f, count = 4))
        assertEquals(1, azLetterAt(y = 100f, heightPx = 400f, count = 4))
        assertEquals(3, azLetterAt(y = 399f, heightPx = 400f, count = 4))
    }

    @Test
    fun `a touch above or below the column clamps to the edge letter`() {
        assertEquals(0, azLetterAt(y = -50f, heightPx = 400f, count = 4))
        assertEquals(3, azLetterAt(y = 400f, heightPx = 400f, count = 4))
        assertEquals(3, azLetterAt(y = 4000f, heightPx = 400f, count = 4))
    }

    @Test
    fun `with no letters and no height there is nothing to jump to`() {
        assertEquals(NO_LETTER, azLetterAt(y = 10f, heightPx = 400f, count = 0))
        assertEquals(NO_LETTER, azLetterAt(y = 10f, heightPx = 0f, count = 4))
    }

    @Test
    fun `a column shorter than the limit reports in full`() {
        val span = azExclusionSpan(heightPx = 500f, limitPx = 600f)

        assertEquals(0f, span.start)
        assertEquals(500f, span.endInclusive)
    }

    @Test
    fun `a longer column reports only its bottom window`() {
        val span = azExclusionSpan(heightPx = 1800f, limitPx = 600f)

        assertEquals(1200f, span.start)
        assertEquals(1800f, span.endInclusive)
    }

    @Test
    fun `an unmeasured column does not produce a negative rectangle`() {
        val span = azExclusionSpan(heightPx = 0f, limitPx = 600f)

        assertEquals(0f, span.start)
        assertEquals(0f, span.endInclusive)
    }
}
