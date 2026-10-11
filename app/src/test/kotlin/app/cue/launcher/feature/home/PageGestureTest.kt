package app.cue.launcher.feature.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PageGestureTest {

    @Nested
    @DisplayName("Edge zones belong to the system Back gesture")
    inner class EdgeZone {

        @Test
        fun `a drag from the middle of the screen pages`() {
            assertFalse(startsInEdgeZone(startX = 540f))
        }

        @Test
        fun `a drag from the left edge is not given to the pager`() {
            assertTrue(startsInEdgeZone(startX = 10f))
        }

        @Test
        fun `a drag from the right edge is not given to the pager`() {
            assertTrue(startsInEdgeZone(startX = 1070f))
        }

        @Test
        fun `the zone boundary is the last pixel of the inset`() {
            assertTrue(startsInEdgeZone(startX = 59f))
            assertFalse(startsInEdgeZone(startX = 60f))
            assertTrue(startsInEdgeZone(startX = 1020f))
            assertFalse(startsInEdgeZone(startX = 1019f))
        }

        @Test
        fun `without a gesture inset (3-button navigation) the whole width is sensitive`() {
            assertFalse(
                PageGesture.startsInEdgeZone(
                    startX = 0f,
                    containerWidthPx = 1080f,
                    leftInsetPx = 0f,
                    rightInsetPx = 0f,
                ),
            )
        }

        private fun startsInEdgeZone(startX: Float): Boolean = PageGesture.startsInEdgeZone(
            startX = startX,
            containerWidthPx = 1080f,
            leftInsetPx = 60f,
            rightInsetPx = 60f,
        )
    }

    @Nested
    @DisplayName("Near the edge only a drag with a recognizable horizontal direction counts")
    inner class Direction {

        @Test
        fun `a horizontal drag past the slop is taken by the pager`() {
            assertTrue(PageGesture.blocksPaging(travelXPx = 40f, travelYPx = 5f, slopPx = 24f))
        }

        @Test
        fun `leftward direction counts the same as rightward`() {
            assertTrue(PageGesture.blocksPaging(travelXPx = -40f, travelYPx = -5f, slopPx = 24f))
        }

        @Test
        fun `below the touch slop nothing is decided yet`() {
            assertFalse(PageGesture.blocksPaging(travelXPx = 20f, travelYPx = 0f, slopPx = 24f))
        }

        @Test
        fun `a vertical drag near the edge stays with the drawer`() {
            assertFalse(PageGesture.blocksPaging(travelXPx = 30f, travelYPx = 120f, slopPx = 24f))
        }

        @Test
        fun `a drag exactly on the diagonal belongs to the drawer`() {
            assertFalse(PageGesture.blocksPaging(travelXPx = 60f, travelYPx = 60f, slopPx = 24f))
        }
    }

    @Nested
    @DisplayName("Dragging a shortcut near the edge flips the page (the left edge is the route into the panel dock)")
    inner class DragPaging {

        @Test
        fun `the middle of the screen does not page`() {
            assertEquals(0, step(xPx = 540f))
        }

        @Test
        fun `near the right edge it goes to the next page`() {
            assertEquals(1, step(xPx = 1075f))
        }

        @Test
        fun `near the left edge it goes back`() {
            assertEquals(-1, step(xPx = 5f))
        }

        @Test
        @DisplayName("Strip boundary: a position inside the content does not page yet")
        fun `the strip boundary is the last pixel of the zone`() {
            assertEquals(-1, step(xPx = 59f))
            assertEquals(0, step(xPx = 60f))
            assertEquals(0, step(xPx = 1020f))
            assertEquals(1, step(xPx = 1021f))
        }

        @Test
        fun `nonsensical dimensions do not page`() {
            assertEquals(0, PageGesture.dragPageStep(xPx = 5f, containerWidthPx = 0f, edgeZonePx = 60f))
            assertEquals(0, PageGesture.dragPageStep(xPx = 5f, containerWidthPx = 1080f, edgeZonePx = 0f))
        }

        private fun step(xPx: Float): Int =
            PageGesture.dragPageStep(xPx = xPx, containerWidthPx = 1080f, edgeZonePx = 60f)
    }
}
