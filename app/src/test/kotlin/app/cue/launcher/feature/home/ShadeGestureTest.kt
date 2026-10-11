package app.cue.launcher.feature.home

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ShadeGestureTest {

    @Nested
    @DisplayName("Only a downward drag past the threshold pulls the panel out")
    inner class Direction {

        @Test
        fun `a downward drag past the threshold pulls the panel out`() {
            assertTrue(opensShade(travelXPx = 0f, travelYPx = 200f))
        }

        @Test
        fun `an upward drag belongs to the drawer`() {
            assertFalse(opensShade(travelXPx = 0f, travelYPx = -200f))
        }

        @Test
        fun `a short downward drag is not yet enough`() {
            assertFalse(opensShade(travelXPx = 0f, travelYPx = 100f))
        }

        @Test
        @DisplayName("Boundary: a distance exactly at the threshold already pulls the panel out")
        fun `a distance exactly at the threshold pulls it out`() {
            assertTrue(opensShade(travelXPx = 0f, travelYPx = 144f))
            assertFalse(opensShade(travelXPx = 0f, travelYPx = 143f))
        }

        @Test
        fun `a predominantly horizontal drag belongs to paging`() {
            assertFalse(opensShade(travelXPx = 300f, travelYPx = 200f))
        }

        @Test
        fun `a drag exactly along the diagonal does not pull the panel out`() {
            assertFalse(opensShade(travelXPx = 200f, travelYPx = 200f))
        }

        @Test
        fun `a diagonal drag with a downward bias pulls the panel out`() {
            assertTrue(opensShade(travelXPx = 80f, travelYPx = 200f))
        }

        private fun opensShade(travelXPx: Float, travelYPx: Float): Boolean =
            ShadeGesture.opensShade(travelXPx, travelYPx, thresholdPx = 144f)
    }

    @Nested
    @DisplayName("Edge zones belong to system gestures")
    inner class SystemZones {

        @Test
        fun `a drag from the middle of the home screen pulls the panel out`() {
            assertFalse(startsInSystemGestureZone(startY = 1200f))
        }

        @Test
        fun `a drag from the top edge is claimed by the status panel`() {
            assertFalse(startsInSystemGestureZone(startY = 50f))
            assertTrue(startsInSystemGestureZone(startY = 20f))
        }

        @Test
        fun `a drag from the bottom edge belongs to the Home gesture`() {
            assertTrue(startsInSystemGestureZone(startY = 2350f))
        }

        @Test
        @DisplayName("Zone boundary: the last pixel of the inset still belongs to the system")
        fun `the zone boundary is the last pixel of the inset`() {
            assertTrue(startsInSystemGestureZone(startY = 47f))
            assertFalse(startsInSystemGestureZone(startY = 48f))
            assertFalse(startsInSystemGestureZone(startY = 2339f))
            assertTrue(startsInSystemGestureZone(startY = 2340f))
        }

        @Test
        fun `without a gesture inset (3-button navigation), the whole height is sensitive`() {
            assertFalse(
                ShadeGesture.startsInSystemGestureZone(
                    startY = 0f,
                    containerHeightPx = 2400f,
                    topInsetPx = 0f,
                    bottomInsetPx = 0f,
                ),
            )
        }

        private fun startsInSystemGestureZone(startY: Float): Boolean =
            ShadeGesture.startsInSystemGestureZone(
                startY = startY,
                containerHeightPx = 2400f,
                topInsetPx = 48f,
                bottomInsetPx = 60f,
            )
    }
}
