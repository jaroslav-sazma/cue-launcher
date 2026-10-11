package app.cue.launcher.feature.home

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeActionGestureTest {

    @Test
    fun `a swipe left past the threshold triggers the action`() {
        assertTrue(PageGesture.triggersAction(travelXPx = -200f, travelYPx = 30f, thresholdPx = 130f))
    }

    @Test
    fun `a short swipe does not trigger the action`() {
        assertFalse(PageGesture.triggersAction(travelXPx = -120f, travelYPx = 10f, thresholdPx = 130f))
    }

    @Test
    fun `a swipe right does not trigger the action`() {
        assertFalse(PageGesture.triggersAction(travelXPx = 300f, travelYPx = 10f, thresholdPx = 130f))
    }

    @Test
    fun `a mostly vertical swipe does not trigger the action`() {
        assertFalse(PageGesture.triggersAction(travelXPx = -200f, travelYPx = -260f, thresholdPx = 130f))
        assertFalse(PageGesture.triggersAction(travelXPx = -200f, travelYPx = 260f, thresholdPx = 130f))
    }

    @Test
    fun `the threshold boundary triggers the action`() {
        assertTrue(PageGesture.triggersAction(travelXPx = -130f, travelYPx = 0f, thresholdPx = 130f))
    }

    @Test
    fun `a swipe from the edge is ignored`() {
        assertTrue(
            PageGesture.startsInEdgeZone(
                startX = 10f,
                containerWidthPx = 1080f,
                leftInsetPx = 40f,
                rightInsetPx = 40f,
            ),
        )
    }
}
