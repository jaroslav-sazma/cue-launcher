package app.cue.launcher.feature.home

import kotlin.math.hypot
import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test
    fun `a long press then a drag to the left never triggers the action`() {
        assertFalse(fires(Sample(450, 0f), Sample(500, -60f), Sample(550, -200f), Sample(600, -400f)))
    }

    @Test
    fun `a finger that stays still past the timeout and then moves is a long press`() {
        assertFalse(fires(Sample(600, -200f), Sample(650, -400f)))
    }

    @Test
    fun `a quick swipe to the left triggers the action`() {
        assertTrue(fires(Sample(20, -40f), Sample(40, -140f), Sample(60, -260f)))
    }

    @Test
    fun `a slow swipe that left the slop before the timeout triggers even after the timeout`() {
        assertTrue(fires(Sample(200, -30f), Sample(450, -90f), Sample(900, -150f)))
    }

    @Test
    fun `jitter within the slop during the hold still counts as a long press`() {
        assertFalse(
            fires(Sample(100, -10f), Sample(250, 8f), Sample(380, -15f), Sample(420, -60f), Sample(500, -300f)),
        )
    }

    @Test
    fun `the slop boundary itself is still part of the hold`() {
        assertEquals(
            SwipeHold.UNDECIDED,
            PageGesture.swipeHold(SLOP, elapsedMillis = 100, SLOP, TIMEOUT),
        )
    }

    private data class Sample(val elapsedMillis: Long, val travelXPx: Float, val travelYPx: Float = 0f)

    private fun fires(vararg samples: Sample): Boolean {
        var hold = SwipeHold.UNDECIDED
        return samples.any { sample ->
            if (hold == SwipeHold.UNDECIDED) {
                val distance = hypot(sample.travelXPx, sample.travelYPx)
                hold = PageGesture.swipeHold(distance, sample.elapsedMillis, SLOP, TIMEOUT)
            }
            hold == SwipeHold.SWIPE &&
                PageGesture.triggersAction(sample.travelXPx, sample.travelYPx, thresholdPx = 130f)
        }
    }

    private companion object {
        const val SLOP = 21f
        const val TIMEOUT = 400L
    }
}
