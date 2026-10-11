package app.cue.launcher.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GlassOverscrollTest {

    @Test
    fun `a surface at its end has not moved`() {
        assertEquals(0f, glassOverscrollTravel(rawPx = 0f, limitPx = 56f))
    }

    @Test
    fun `the first pixels past the end are followed almost exactly`() {
        assertEquals(4f, glassOverscrollTravel(rawPx = 4f, limitPx = 56f), 0.2f)
    }

    @Test
    fun `dragging further gives less and less`() {
        val limit = 56f
        val near = glassOverscrollTravel(rawPx = 60f, limitPx = limit) -
            glassOverscrollTravel(rawPx = 40f, limitPx = limit)
        val far = glassOverscrollTravel(rawPx = 160f, limitPx = limit) -
            glassOverscrollTravel(rawPx = 140f, limitPx = limit)
        assertTrue(far < near)
    }

    @Test
    fun `no drag can push the surface past its limit`() {
        assertTrue(glassOverscrollTravel(rawPx = 200f, limitPx = 56f) < 56f)
        assertEquals(56f, glassOverscrollTravel(rawPx = 10_000f, limitPx = 56f))
    }

    @Test
    fun `the other end behaves the same way mirrored`() {
        assertEquals(
            -glassOverscrollTravel(rawPx = 80f, limitPx = 56f),
            glassOverscrollTravel(rawPx = -80f, limitPx = 56f),
        )
    }

    // Reduced motion leaves the overscroll absorbing the drag without showing anything for it.
    @Test
    fun `a surface that is not allowed to travel stays put`() {
        assertEquals(0f, glassOverscrollTravel(rawPx = 400f, limitPx = 0f))
    }

    @Test
    fun `a surface at its end pays nothing back`() {
        assertEquals(0f, glassOverscrollRelax(rawPx = 0f, delta = 30f))
    }

    @Test
    fun `dragging further out pays nothing back either`() {
        assertEquals(0f, glassOverscrollRelax(rawPx = 20f, delta = 30f))
        assertEquals(0f, glassOverscrollRelax(rawPx = -20f, delta = -30f))
    }

    // The scroll behind must not start moving while the surface is still out of place.
    @Test
    fun `a drag back is spent entirely on the overscroll while it lasts`() {
        assertEquals(-12f, glassOverscrollRelax(rawPx = 40f, delta = -12f))
        assertEquals(12f, glassOverscrollRelax(rawPx = -40f, delta = 12f))
    }

    @Test
    fun `a drag past the overscroll pays off exactly what was standing there`() {
        assertEquals(-40f, glassOverscrollRelax(rawPx = 40f, delta = -60f))
        assertEquals(40f, glassOverscrollRelax(rawPx = -40f, delta = 60f))
    }

    // Otherwise the payment would carry the surface out the far side and it would never settle.
    @Test
    fun `paying off never overshoots into the other direction`() {
        val raw = 40f
        assertEquals(0f, raw + glassOverscrollRelax(raw, delta = -400f))
    }
}
