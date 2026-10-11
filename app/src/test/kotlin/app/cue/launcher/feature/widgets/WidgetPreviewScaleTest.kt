package app.cue.launcher.feature.widgets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WidgetPreviewScaleTest {

    private val tolerance = 0.0001f

    @Test
    fun `a smaller preview is not enlarged`() {
        assertEquals(1f, previewScale(100f, 50f, 400f, 200f), tolerance)
    }

    @Test
    fun `a wider preview is limited by width`() {
        assertEquals(0.5f, previewScale(800f, 100f, 400f, 200f), tolerance)
    }

    @Test
    fun `a taller preview is limited by height`() {
        assertEquals(0.25f, previewScale(100f, 800f, 400f, 200f), tolerance)
    }

    @Test
    fun `the stricter axis decides`() {
        assertEquals(0.25f, previewScale(800f, 800f, 400f, 200f), tolerance)
    }

    @Test
    fun `nonsensical dimensions leave the scale at 1`() {
        assertEquals(1f, previewScale(0f, 100f, 400f, 200f), tolerance)
        assertEquals(1f, previewScale(100f, 100f, 0f, 200f), tolerance)
        assertEquals(1f, previewScale(100f, -1f, 400f, 200f), tolerance)
    }
}
