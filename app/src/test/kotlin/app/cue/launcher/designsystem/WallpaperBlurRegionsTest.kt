package app.cue.launcher.designsystem

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class WallpaperBlurRegionsTest {

    @Test
    fun `the region's corners have eight equal values`() {
        val radii = blurRegionCornerRadii(24f)
        assertEquals(8, radii.size)
        assertArrayEquals(FloatArray(8) { 24f }, radii)
    }

    @Test
    fun `a zero radius stays zero`() {
        assertArrayEquals(FloatArray(8), blurRegionCornerRadii(0f))
    }

    @Test
    fun `a scrim with a lower zIndex is drawn before the blocks`() {
        val bento = region(zIndex = 0f, blurRadius = 20f)
        val overlay = region(zIndex = -1f, blurRadius = 2f)
        assertEquals(listOf(overlay, bento), listOf(bento, overlay).sortedForDraw())
    }

    @Test
    fun `an equal zIndex keeps insertion order`() {
        val bento = region(zIndex = 0f, blurRadius = 20f)
        val dock = region(zIndex = 0f, blurRadius = 48f)
        assertEquals(listOf(bento, dock), listOf(bento, dock).sortedForDraw())
    }

    // Geometry belongs to the graphics layer alone: boundsInWindow() already carries it, so a depth
    // that repeated it here would drag the blur out from under the colour sitting on it.
    @Test
    fun `depth never moves or resizes the region`() {
        val placed = region()
        val moved = placed.withDepth(BlurRegionDepth(alpha = 0.3f, blurScale = 4f))
        assertEquals(placed.left, moved.left)
        assertEquals(placed.top, moved.top)
        assertEquals(placed.right, moved.right)
        assertEquals(placed.bottom, moved.bottom)
        assertEquals(placed.cornerRadius, moved.cornerRadius)
    }

    @Test
    fun `a receding plane dims its glass`() {
        assertEquals(0.5f, region().withDepth(BlurRegionDepth(alpha = 0.5f)).alpha)
    }

    @Test
    fun `depth multiplies the blur so an arriving card is out of focus`() {
        assertEquals(60f, region(blurRadius = 20f).withDepth(BlurRegionDepth(blurScale = 3f)).blurRadius)
    }

    @Test
    fun `stacking the card's depth inside the page's multiplies both`() {
        val stacked = region(blurRadius = 10f)
            .withDepth(BlurRegionDepth(blurScale = 3f))
            .withDepth(BlurRegionDepth(alpha = 0.5f, blurScale = 2f))
        assertEquals(60f, stacked.blurRadius)
        assertEquals(0.5f, stacked.alpha)
    }

    @Test
    fun `a flat plane leaves the region exactly as it was placed`() {
        val placed = region()
        assertSame(placed, placed.withDepth(BlurRegionDepth.NONE))
    }

    // A region costs a pass over its area whether or not any of it reaches the screen.
    @Test
    fun `a region nobody can see is not worth drawing`() {
        assertNull(region().withDepth(BlurRegionDepth(alpha = 0f)).worthDrawing())
        assertNull(region(blurRadius = 0.4f).worthDrawing())
        assertNotNull(region().worthDrawing())
    }

    @Test
    fun `a region an empty element left behind is not worth drawing either`() {
        assertNull(region().copy(right = 0f).worthDrawing())
        assertNull(region().copy(bottom = 0f).worthDrawing())
    }

    @Test
    fun `a region still faintly there is kept`() {
        assertNotNull(region().withDepth(BlurRegionDepth(alpha = 0.02f)).worthDrawing())
    }

    @Test
    fun `differences nobody can tell apart settle on the same region`() {
        val wobbling = region(blurRadius = 20.4f).withDepth(BlurRegionDepth(alpha = 0.500001f))
        val settled = region(blurRadius = 19.9f).withDepth(BlurRegionDepth(alpha = 0.5f))
        assertEquals(settled.worthDrawing(), wobbling.worthDrawing())
    }

    @Test
    fun `nothing about that moves the region`() {
        val placed = region(blurRadius = 20.4f)
        val drawn = placed.worthDrawing()!!
        assertEquals(placed.left, drawn.left)
        assertEquals(placed.top, drawn.top)
        assertEquals(placed.right, drawn.right)
        assertEquals(placed.bottom, drawn.bottom)
        assertEquals(placed.cornerRadius, drawn.cornerRadius)
        assertEquals(placed.zIndex, drawn.zIndex)
    }

    private fun region(
        cornerRadius: Float = 24f,
        blurRadius: Float = 20f,
        zIndex: Float = 0f,
    ) = WallpaperBlurRegion(
        left = 0f,
        top = 0f,
        right = 100f,
        bottom = 100f,
        cornerRadius = cornerRadius,
        blurRadius = blurRadius,
        zIndex = zIndex,
    )
}
