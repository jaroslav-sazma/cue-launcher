package app.cue.launcher.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PageIndicatorTest {

    @Test
    fun `the dot of its own page is fully active`() {
        assertEquals(1f, pageDotActiveness(page = 0, position = 0f))
        assertEquals(1f, pageDotActiveness(page = 1, position = 1f))
    }

    @Test
    fun `a distant dot is fully inactive`() {
        assertEquals(0f, pageDotActiveness(page = 0, position = 1f))
        assertEquals(0f, pageDotActiveness(page = 2, position = 0f))
    }

    @Test
    fun `mid-drag the activeness splits between neighbors`() {
        assertEquals(0.75f, pageDotActiveness(page = 0, position = 0.25f))
        assertEquals(0.25f, pageDotActiveness(page = 1, position = 0.25f))
    }

    @Test
    fun `overscrolling past an edge page only dims the dot, never switches it`() {
        assertEquals(0.75f, pageDotActiveness(page = 0, position = -0.25f))
        assertEquals(0f, pageDotActiveness(page = 1, position = -0.25f))
    }

    @Test
    fun `half a swipe off the home page already finishes the morph`() {
        val position = pageIndicatorPosition(position = 0.5f, pageCount = 2)
        assertEquals(1f, pageDotActiveness(page = 0, position = position))
        assertEquals(0f, pageDotActiveness(page = 1, position = position))
    }

    @Test
    fun `a quarter of a swipe is halfway through the morph`() {
        assertEquals(0.5f, pageIndicatorPosition(position = 0.75f, pageCount = 2))
    }

    @Test
    fun `the second half of a swipe holds the morph where it ended`() {
        assertEquals(0f, pageIndicatorPosition(position = 0.25f, pageCount = 2))
    }

    @Test
    fun `a settled page keeps its own position`() {
        assertEquals(0f, pageIndicatorPosition(position = 0f, pageCount = 2))
        assertEquals(1f, pageIndicatorPosition(position = 1f, pageCount = 2))
    }

    @Test
    fun `overscroll is left untouched so the edge dot still dims`() {
        assertEquals(-0.25f, pageIndicatorPosition(position = -0.25f, pageCount = 2))
        assertEquals(1.25f, pageIndicatorPosition(position = 1.25f, pageCount = 2))
    }

    @Test
    fun `a settled pager lets the indicator hide`() {
        assertFalse(pageIndicatorPinned(scrollInProgress = false, pageOffsetFraction = 0f))
    }

    @Test
    fun `a scrolling pager keeps the indicator visible`() {
        assertTrue(pageIndicatorPinned(scrollInProgress = true, pageOffsetFraction = 0f))
    }

    @Test
    fun `an offset without a scroll in progress still keeps the indicator visible`() {
        assertTrue(pageIndicatorPinned(scrollInProgress = false, pageOffsetFraction = -0.2f))
    }
}
