package app.cue.launcher.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class AppIconGeometryTest {

    @Test
    fun `an adaptive icon layer is drawn with margin on both sides`() {
        val bounds = adaptiveIconBounds(sizePx = 174, extraInsetFraction = 0.25f)

        assertEquals(-44, bounds.left)
        assertEquals(-44, bounds.top)
        assertEquals(218, bounds.right)
        assertEquals(218, bounds.bottom)
    }

    @ParameterizedTest(name = "an icon of {0} px stays symmetric on both sides")
    @ValueSource(ints = [58, 87, 116, 145, 174])
    fun `the visible part fills the target square`(sizePx: Int) {
        val bounds = adaptiveIconBounds(sizePx, extraInsetFraction = 0.25f)
        val inset = -bounds.left

        assertEquals(inset, bounds.right - sizePx, "the inset must be the same on both sides")

        assertEquals(sizePx, bounds.width - 2 * inset)
    }

    @Test
    fun `a finished drawing fills the whole square`() {
        val bounds = fullBleedIconBounds(sizePx = 174)

        assertEquals(0, bounds.left)
        assertEquals(174, bounds.right)
        assertEquals(174, bounds.width)
    }

    @Test
    fun `a legacy icon takes 70 percent of the square and is centered`() {
        val bounds = legacyIconBounds(sizePx = 174, intrinsicWidth = 48, intrinsicHeight = 48)

        assertEquals(122, bounds.width)
        assertEquals(122, bounds.height)
        assertEquals(26, bounds.left)
        assertEquals(174 - bounds.right, bounds.left, "the margins must be the same on both sides")
        assertEquals(174 - bounds.bottom, bounds.top)
    }

    @Test
    fun `a legacy icon keeps its aspect ratio`() {
        val wide = legacyIconBounds(sizePx = 174, intrinsicWidth = 96, intrinsicHeight = 48)
        assertEquals(122, wide.width)
        assertEquals(61, wide.height)

        val tall = legacyIconBounds(sizePx = 174, intrinsicWidth = 48, intrinsicHeight = 96)
        assertEquals(61, tall.width)
        assertEquals(122, tall.height)
    }

    @Test
    fun `a legacy icon without known dimensions is square`() {
        val bounds = legacyIconBounds(sizePx = 174, intrinsicWidth = -1, intrinsicHeight = -1)

        assertEquals(bounds.width, bounds.height)
        assertEquals(122, bounds.width)
    }

    @Test
    fun `a shrunk legacy drawing fits inside the mask`() {
        val sizePx = 174
        val bounds = legacyIconBounds(sizePx, intrinsicWidth = 48, intrinsicHeight = 48)
        val radius = sizePx * 0.33f
        val maskBite = radius - radius / kotlin.math.sqrt(2f)

        assertTrue(
            bounds.left > maskBite,
            "the margin ${bounds.left} px must exceed the mask bite ${maskBite.toInt()} px",
        )
    }
}
