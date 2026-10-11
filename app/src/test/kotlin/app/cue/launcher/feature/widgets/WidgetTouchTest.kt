package app.cue.launcher.feature.widgets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class WidgetTouchTest {

    @Nested
    @DisplayName("The decision made at the start of a drag")
    inner class Decision {

        @Test
        fun `below the touch slop nothing is decided yet`() {
            val arbitration = arbitration()
            assertFalse(arbitration.onDrag(deltaXPx = 10f, deltaYPx = 10f, canScrollUp = true, canScrollDown = true))
            assertEquals(WidgetTouchOwner.UNDECIDED, arbitration.owner)
        }

        @Test
        fun `a horizontal drag belongs to the pager even over a scrollable widget`() {
            val arbitration = arbitration()
            assertTrue(arbitration.onDrag(deltaXPx = -40f, deltaYPx = 5f, canScrollUp = true, canScrollDown = true))
            assertEquals(WidgetTouchOwner.ANCESTOR, arbitration.owner)
        }

        @Test
        fun `a drag exactly on the diagonal is treated as vertical`() {
            val arbitration = arbitration()
            arbitration.onDrag(deltaXPx = 30f, deltaYPx = 30f, canScrollUp = true, canScrollDown = true)
            assertEquals(WidgetTouchOwner.WIDGET, arbitration.owner)
        }

        @Test
        fun `a vertical drag over a non-scrollable widget belongs to the panel`() {
            val arbitration = arbitration()
            assertTrue(arbitration.onDrag(deltaXPx = 0f, deltaYPx = 40f, canScrollUp = false, canScrollDown = false))
            assertEquals(WidgetTouchOwner.ANCESTOR, arbitration.owner)
        }

        @Test
        fun `a downward drag scrolls the widget up`() {
            val down = arbitration()
            down.onDrag(deltaXPx = 0f, deltaYPx = 40f, canScrollUp = true, canScrollDown = false)
            assertEquals(WidgetTouchOwner.WIDGET, down.owner)

            val up = arbitration()
            assertTrue(up.onDrag(deltaXPx = 0f, deltaYPx = -40f, canScrollUp = true, canScrollDown = false))
            assertEquals(WidgetTouchOwner.ANCESTOR, up.owner)
        }

        @Test
        fun `an upward drag scrolls the widget down`() {
            val arbitration = arbitration()
            assertFalse(arbitration.onDrag(deltaXPx = 0f, deltaYPx = -40f, canScrollUp = false, canScrollDown = true))
            assertEquals(WidgetTouchOwner.WIDGET, arbitration.owner)
        }

        @Test
        fun `the sum of the movement decides, not a single event`() {
            val arbitration = arbitration()
            repeat(4) {
                arbitration.onDrag(deltaXPx = 8f, deltaYPx = 1f, canScrollUp = true, canScrollDown = true)
            }
            assertEquals(WidgetTouchOwner.ANCESTOR, arbitration.owner)
        }
    }

    @Nested
    @DisplayName("Handing over the panel when a widget reaches the end")
    inner class Handover {

        @Test
        fun `a scrolling widget keeps holding the drag`() {
            val arbitration = scrollingWidget()
            repeat(5) {
                assertFalse(
                    arbitration.onDrag(deltaXPx = 0f, deltaYPx = -20f, canScrollUp = true, canScrollDown = true),
                )
            }
            assertEquals(WidgetTouchOwner.WIDGET, arbitration.owner)
        }

        @Test
        fun `at the end of the content the rest of the drag goes to the panel`() {
            val arbitration = scrollingWidget()

            assertFalse(arbitration.onDrag(deltaXPx = 0f, deltaYPx = -10f, canScrollUp = true, canScrollDown = false))
            assertEquals(WidgetTouchOwner.WIDGET, arbitration.owner)
            assertTrue(arbitration.onDrag(deltaXPx = 0f, deltaYPx = -20f, canScrollUp = true, canScrollDown = false))
            assertEquals(WidgetTouchOwner.ANCESTOR, arbitration.owner)
        }

        @Test
        fun `a jitter at the ownership boundary does not flip it`() {
            val arbitration = scrollingWidget()
            repeat(10) {
                assertFalse(
                    arbitration.onDrag(deltaXPx = 0f, deltaYPx = -2f, canScrollUp = true, canScrollDown = false),
                )
                assertFalse(
                    arbitration.onDrag(deltaXPx = 0f, deltaYPx = 2f, canScrollUp = true, canScrollDown = true),
                )
            }
            assertEquals(WidgetTouchOwner.WIDGET, arbitration.owner)
        }

        @Test
        fun `the handover is reported only once`() {
            val arbitration = scrollingWidget()
            assertTrue(arbitration.onDrag(deltaXPx = 0f, deltaYPx = -40f, canScrollUp = true, canScrollDown = false))
            repeat(3) {
                assertFalse(
                    arbitration.onDrag(deltaXPx = 0f, deltaYPx = -40f, canScrollUp = true, canScrollDown = false),
                )
            }
            assertEquals(WidgetTouchOwner.ANCESTOR, arbitration.owner)
        }

        @Test
        fun `a drag back toward the widget does not hand it back`() {
            val arbitration = arbitration()
            arbitration.onDrag(deltaXPx = 40f, deltaYPx = 0f, canScrollUp = true, canScrollDown = true)
            arbitration.onDrag(deltaXPx = 0f, deltaYPx = -40f, canScrollUp = true, canScrollDown = true)
            assertEquals(WidgetTouchOwner.ANCESTOR, arbitration.owner)
        }

        private fun scrollingWidget() = arbitration().apply {
            onDrag(deltaXPx = 0f, deltaYPx = -30f, canScrollUp = true, canScrollDown = true)
            check(owner == WidgetTouchOwner.WIDGET)
        }
    }

    // The pager and the stack of a bento tile both wait above a widget, and which of them the
    // handover means is the axis the drag ran along.
    @Nested
    @DisplayName("Which ancestor the drag is handed to")
    inner class Axis {

        @Test
        fun `a drag across goes to the pager, not to the stack`() {
            val arbitration = arbitration()
            arbitration.onDrag(deltaXPx = -40f, deltaYPx = 5f, canScrollUp = false, canScrollDown = false)
            assertEquals(WidgetTouchOwner.ANCESTOR, arbitration.owner)
            assertFalse(arbitration.vertical)
        }

        @Test
        fun `a drag along over a widget that does not scroll goes to the stack`() {
            val arbitration = arbitration()
            arbitration.onDrag(deltaXPx = 5f, deltaYPx = -40f, canScrollUp = false, canScrollDown = false)
            assertEquals(WidgetTouchOwner.ANCESTOR, arbitration.owner)
            assertTrue(arbitration.vertical)
        }

        @Test
        fun `a widget scrolled to its end hands the rest of the drag along`() {
            val arbitration = arbitration()
            arbitration.onDrag(deltaXPx = 0f, deltaYPx = -30f, canScrollUp = true, canScrollDown = true)
            check(arbitration.owner == WidgetTouchOwner.WIDGET)

            assertTrue(arbitration.onDrag(deltaXPx = 0f, deltaYPx = -40f, canScrollUp = true, canScrollDown = false))
            assertTrue(arbitration.vertical)
        }
    }

    private fun arbitration() = WidgetTouchArbitration(slopPx = 24f)
}
