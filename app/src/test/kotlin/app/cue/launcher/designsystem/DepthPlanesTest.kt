package app.cue.launcher.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DepthPlanesTest {

    @Test
    fun `home sits untouched until the swipe starts`() {
        assertEquals(1f, homeRecedeScale(0f))
        assertEquals(1f, homeRecedeAlpha(0f))
    }

    @Test
    fun `a finished swipe leaves home at its receded depth`() {
        assertEquals(Tokens.Motion.Depth.HOME_SCALE, homeRecedeScale(1f))
        assertEquals(Tokens.Motion.Depth.HOME_ALPHA, homeRecedeAlpha(1f))
    }

    @Test
    fun `the panel arrives at full size and opacity`() {
        assertEquals(Tokens.Motion.Depth.PANEL_SCALE, panelEmergeScale(0f))
        assertEquals(1f, panelEmergeScale(1f))
        assertEquals(1f, panelEmergeAlpha(1f))
    }

    // The two planes travel the same depth in opposite directions, so they are at exactly the same
    // distance at the halfway point and swap which one is nearer on either side of it.
    @Test
    fun `the planes cross over halfway through the swipe`() {
        assertEquals(homeRecedeScale(0.5f), panelEmergeScale(0.5f))
        assertTrue(panelEmergeScale(0.4f) < homeRecedeScale(0.4f))
        assertTrue(panelEmergeScale(0.6f) > homeRecedeScale(0.6f))
    }

    @Test
    fun `overscroll past either end holds the planes at their bounds`() {
        assertEquals(1f, homeRecedeScale(-0.3f))
        assertEquals(Tokens.Motion.Depth.HOME_SCALE, homeRecedeScale(1.3f))
        assertEquals(Tokens.Motion.Depth.PANEL_SCALE, panelEmergeScale(-0.3f))
        assertEquals(1f, panelEmergeScale(1.3f))
    }

    @Test
    fun `home is in focus at rest and out of focus once it has left`() {
        assertEquals(1f, homeDefocus(0f))
        assertEquals(Tokens.Motion.Depth.HOME_DEFOCUS, homeDefocus(1f))
        assertTrue(homeDefocus(0.5f) > 1f)
    }

    @Test
    fun `home sinks as it recedes and starts level`() {
        assertEquals(0f, homeSink(0f, sinkPx = 40f))
        assertEquals(40f, homeSink(1f, sinkPx = 40f))
    }

    @Test
    fun `the panel closes its extra travel exactly as the swipe settles`() {
        assertEquals(-60f, panelLead(0f, leadPx = 60f))
        // A delta, because the expression lands on negative zero, which is not equal to 0f.
        assertEquals(0f, panelLead(1f, leadPx = 60f), 0f)
    }

    @Test
    fun `the near plane outruns the far one`() {
        // Mid-swipe the panel is still catching up, which is what reads as parallax.
        assertTrue(panelLead(0.5f, leadPx = 60f) < 0f)
    }

    @Test
    fun `the first card leads and the last one trails`() {
        assertTrue(cardStaggerProgress(0.4f, index = 0, count = 4) > cardStaggerProgress(0.4f, index = 3, count = 4))
    }

    @Test
    fun `every card has landed once the swipe settles`() {
        repeat(4) { index ->
            assertEquals(1f, cardStaggerProgress(1f, index = index, count = 4))
        }
    }

    @Test
    fun `the last card only starts once the stagger is spent`() {
        val stagger = Tokens.Motion.Depth.CARD_STAGGER
        assertEquals(0f, cardStaggerProgress(stagger, index = 3, count = 4))
        assertTrue(cardStaggerProgress(stagger + 0.01f, index = 3, count = 4) > 0f)
    }

    @Test
    fun `a lone card carries no stagger`() {
        assertEquals(0f, cardStaggerProgress(0f, index = 0, count = 1))
        assertTrue(cardStaggerProgress(0.1f, index = 0, count = 1) > 0f)
    }
}
