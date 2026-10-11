package app.cue.launcher.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PanelScrollbarTest {

    @Test
    @DisplayName("Content that fits has no scrollbar")
    fun `short content does not draw a thumb`() {
        assertNull(thumb(contentPx = 800f, scrollOffsetPx = 0f))
        assertNull(thumb(contentPx = 1000f, scrollOffsetPx = 0f))
    }

    @Test
    fun `nothing is drawn before the first measurement`() {
        assertNull(thumb(viewportPx = 0f, contentPx = 0f, scrollOffsetPx = 0f))
        assertNull(thumb(trackLengthPx = 0f, contentPx = 4000f, scrollOffsetPx = 0f))
    }

    @Test
    fun `the thumb length is the ratio of the visible part`() {

        val geometry = visibleThumb(contentPx = 4000f, scrollOffsetPx = 0f)
        assertEquals(240f, geometry.lengthPx)
        assertEquals(0f, geometry.offsetPx)
    }

    @Test
    fun `a fully scrolled column has the thumb at the end of the track`() {
        val geometry = visibleThumb(contentPx = 4000f, scrollOffsetPx = 3000f)
        assertEquals(720f, geometry.offsetPx)
    }

    @Test
    fun `halfway through scrolling the thumb is halfway through the remaining track`() {
        val geometry = visibleThumb(contentPx = 4000f, scrollOffsetPx = 1500f)
        assertEquals(360f, geometry.offsetPx)
    }

    @Test
    @DisplayName("The thumb has a lower bound so it does not vanish on a long column")
    fun `long content does not shrink the thumb below the minimum`() {
        assertEquals(MIN_THUMB_PX, visibleThumb(contentPx = 96_000f, scrollOffsetPx = 0f).lengthPx)
    }

    @Test
    fun `the minimum never exceeds the track`() {
        val geometry = visibleThumb(trackLengthPx = 40f, contentPx = 96_000f, scrollOffsetPx = 0f)
        assertEquals(40f, geometry.lengthPx)
    }

    @Test
    fun `an out-of-range position is clamped`() {

        assertEquals(0f, visibleThumb(contentPx = 4000f, scrollOffsetPx = -500f).offsetPx)
        assertEquals(720f, visibleThumb(contentPx = 4000f, scrollOffsetPx = 9000f).offsetPx)
    }

    private fun visibleThumb(
        trackLengthPx: Float = 960f,
        viewportPx: Float = 1000f,
        contentPx: Float,
        scrollOffsetPx: Float,
    ): ScrollbarThumb = requireNotNull(
        thumb(
            trackLengthPx = trackLengthPx,
            viewportPx = viewportPx,
            contentPx = contentPx,
            scrollOffsetPx = scrollOffsetPx,
        ),
    ) { "the thumb should be drawn" }

    private fun thumb(
        trackLengthPx: Float = 960f,
        viewportPx: Float = 1000f,
        contentPx: Float,
        scrollOffsetPx: Float,
    ): ScrollbarThumb? = scrollbarThumb(
        trackLengthPx = trackLengthPx,
        viewportPx = viewportPx,
        contentPx = contentPx,
        scrollOffsetPx = scrollOffsetPx,
        minLengthPx = MIN_THUMB_PX,
    )

    private companion object {

        const val MIN_THUMB_PX = 72f
    }
}
