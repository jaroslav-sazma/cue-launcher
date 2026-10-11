package app.cue.launcher.feature.drawer

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DrawerGestureTest {

    @Nested
    @DisplayName("The bottom edge zone belongs to the system Home gesture")
    inner class SystemGestureZone {

        @Test
        fun `a swipe from the middle of the home screen opens the drawer`() {
            assertFalse(
                DrawerGesture.startsInSystemGestureZone(
                    startY = 1000f,
                    containerHeightPx = 2400f,
                    bottomInsetPx = 48f,
                ),
            )
        }

        @Test
        fun `a swipe from the bottom edge zone is ignored`() {
            assertTrue(
                DrawerGesture.startsInSystemGestureZone(
                    startY = 2380f,
                    containerHeightPx = 2400f,
                    bottomInsetPx = 48f,
                ),
            )
        }

        @Test
        fun `the zone boundary is the first pixel of the inset`() {
            assertTrue(
                DrawerGesture.startsInSystemGestureZone(
                    startY = 2352f,
                    containerHeightPx = 2400f,
                    bottomInsetPx = 48f,
                ),
            )
            assertFalse(
                DrawerGesture.startsInSystemGestureZone(
                    startY = 2351f,
                    containerHeightPx = 2400f,
                    bottomInsetPx = 48f,
                ),
            )
        }

        @Test
        fun `without a gesture inset (3-button navigation) the whole area stays sensitive`() {
            assertFalse(
                DrawerGesture.startsInSystemGestureZone(
                    startY = 2399f,
                    containerHeightPx = 2400f,
                    bottomInsetPx = 0f,
                ),
            )
        }
    }

    @Nested
    @DisplayName("Drag distance threshold")
    inner class Travel {

        @Test
        fun `a swipe up past the threshold opens`() {
            assertTrue(DrawerGesture.opensDrawer(travelPx = -150f, thresholdPx = 130f))
        }

        @Test
        fun `a short swipe up does not open`() {
            assertFalse(DrawerGesture.opensDrawer(travelPx = -120f, thresholdPx = 130f))
        }

        @Test
        fun `a swipe down does not open the drawer`() {
            assertFalse(DrawerGesture.opensDrawer(travelPx = 300f, thresholdPx = 130f))
        }

        @Test
        fun `a swipe down past the threshold closes`() {
            assertTrue(DrawerGesture.closesDrawer(travelPx = 130f, thresholdPx = 130f))
        }

        @Test
        fun `a swipe up does not close the drawer`() {
            assertFalse(DrawerGesture.closesDrawer(travelPx = -300f, thresholdPx = 130f))
        }
    }
}
