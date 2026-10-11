package app.cue.launcher.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpecularRakeTest {

    @Test
    fun `no light before the card starts moving and none once it has landed`() {
        assertEquals(0f, rakeStrength(0f))
        assertEquals(0f, rakeStrength(1f))
    }

    @Test
    fun `the rake is brightest halfway through the arrival`() {
        assertEquals(Tokens.Motion.Depth.RAKE_PEAK, rakeStrength(0.5f))
        assertTrue(rakeStrength(0.25f) < rakeStrength(0.5f))
        assertTrue(rakeStrength(0.75f) < rakeStrength(0.5f))
    }

    @Test
    fun `the band enters off one edge and leaves past the other`() {
        assertTrue(rakeCentreFraction(0f) < 0f)
        assertTrue(rakeCentreFraction(1f) > 1f)
    }

    @Test
    fun `the band only ever travels forwards`() {
        assertTrue(rakeCentreFraction(0.3f) < rakeCentreFraction(0.6f))
    }

    @Test
    fun `overscroll cannot push the light back on`() {
        assertEquals(0f, rakeStrength(1.4f))
        assertEquals(0f, rakeStrength(-0.4f))
    }

    // The card is read through the page's own region until it takes over its pane, so the arrival
    // costs nothing: it withholds the pane rather than widening the blur that draws it.
    @Test
    fun `an arriving card has not taken over its pane yet and a landed one has`() {
        assertEquals(0f, cardArrivalDepth(0f, index = 0, count = 3).alpha)
        assertEquals(1f, cardArrivalDepth(1f, index = 0, count = 3).alpha)
    }

    @Test
    fun `the arrival never widens the blur`() {
        assertEquals(1f, cardArrivalDepth(0f, index = 0, count = 3).blurScale)
        assertEquals(1f, cardArrivalDepth(0.5f, index = 0, count = 3).blurScale)
    }

    @Test
    fun `the pane is withheld until the card is nearly landed`() {
        val reveal = Tokens.Motion.Depth.CARD_PANE_REVEAL
        assertEquals(0f, cardPaneReveal(reveal))
        assertTrue(cardPaneReveal(reveal + 0.01f) > 0f)
        assertEquals(1f, cardPaneReveal(1f))
    }

    @Test
    fun `overscroll cannot push the pane past either end`() {
        assertEquals(0f, cardPaneReveal(-0.4f))
        assertEquals(1f, cardPaneReveal(1.4f))
    }
}
