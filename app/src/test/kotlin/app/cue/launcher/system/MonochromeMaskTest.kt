package app.cue.launcher.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MonochromeMaskTest {

    private fun icon(side: Int, background: Int, shape: Int, detail: Int? = null): IntArray {
        val pixels = IntArray(side * side) { background }
        val inset = side / 4
        for (y in inset until side - inset) {
            for (x in inset until side - inset) pixels[y * side + x] = shape
        }
        if (detail != null) {
            val center = side / 2
            pixels[center * side + center] = detail
        }
        return pixels
    }

    private fun alphaAt(mask: IntArray, side: Int, x: Int, y: Int) = mask[y * side + x]

    @Test
    fun `a light drawing on a dark background gives a silhouette`() {
        val side = 32
        val mask = monochromeAlpha(icon(side, background = 20, shape = 240), edgeSampleCount = side * 2)

        assertEquals(0, alphaAt(mask, side, 1, 0), "the background must disappear")
        assertEquals(255, alphaAt(mask, side, side / 2, side / 2), "the drawing must stay fully opaque")
    }

    @Test
    fun `a dark drawing on a light background flips the polarity`() {
        val side = 32
        val mask = monochromeAlpha(icon(side, background = 240, shape = 20), edgeSampleCount = side * 2)

        assertEquals(0, alphaAt(mask, side, 1, 0), "the background must disappear even when it is light")
        assertEquals(255, alphaAt(mask, side, side / 2, side / 2))
    }

    @Test
    fun `a dark detail inside the drawing stays a hole`() {
        val side = 32
        val pixels = icon(side, background = 147, shape = 246, detail = 137)
        val mask = monochromeAlpha(pixels, edgeSampleCount = side * 2)

        val body = alphaAt(mask, side, side / 2 - 3, side / 2)
        val lens = alphaAt(mask, side, side / 2, side / 2)
        assertEquals(255, body)
        assertTrue(lens < body / 2, "the lens ($lens) must be noticeably more transparent than the body ($body)")
    }

    @Test
    fun `a faint halftone falls to zero`() {
        val side = 32
        val pixels = icon(side, background = 0, shape = 255)

        for (x in 0 until side) pixels[(side / 4 - 1) * side + x] = 40
        val mask = monochromeAlpha(pixels, edgeSampleCount = side * 2)

        assertTrue(alphaAt(mask, side, side / 2, side / 4 - 1) < 20, "the shadow must almost disappear")
    }

    @Test
    fun `a flat single-color source stays unchanged`() {
        val flat = IntArray(16) { 100 }

        assertTrue(monochromeAlpha(flat, edgeSampleCount = 4).all { it == 100 })
    }
}
