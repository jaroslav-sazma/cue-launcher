package app.cue.launcher.feature.home

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.engine.QueueEntry
import app.cue.launcher.engine.scenario
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StackPagingTest {

    private fun pages(vararg ids: String): List<QueueEntry> = ids.map { id ->
        QueueEntry(
            scenario = scenario(id, size = TileSize.GRID_2X2),
            activation = Activation(scenarioId = id, since = 1_000L),
        )
    }

    private val threshold = 48f

    @Test
    fun `an upward drag goes deeper and a downward drag goes back`() {
        assertEquals(1, stackStep(travelPx = -48f, thresholdPx = threshold))
        assertEquals(-1, stackStep(travelPx = 48f, thresholdPx = threshold))
    }

    @Test
    fun `a drag below the threshold does not switch the content`() {
        assertEquals(0, stackStep(travelPx = -47.9f, thresholdPx = threshold))
        assertEquals(0, stackStep(travelPx = 0f, thresholdPx = threshold))
        assertEquals(0, stackStep(travelPx = 47.9f, thresholdPx = threshold))
    }

    @Test
    fun `the index is found by scene id`() {
        val stack = pages("vrch", "druhy", "treti")

        assertEquals(0, stackIndexOf(stack, "vrch"))
        assertEquals(2, stackIndexOf(stack, "treti"))
    }

    @Test
    fun `a vanished scenario returns the tile to the top of the queue`() {
        val stack = pages("vrch", "druhy")

        assertEquals(0, stackIndexOf(stack, selectedId = "uz-neexistuje"))
        assertEquals(0, stackIndexOf(stack, selectedId = null))
    }

    @Test
    fun `the stack does not cycle`() {
        assertEquals(0, stackPageAt(index = 0, step = -1, pageCount = 3))
        assertEquals(2, stackPageAt(index = 2, step = 1, pageCount = 3))
        assertEquals(1, stackPageAt(index = 0, step = 1, pageCount = 3))
        assertEquals(0, stackPageAt(index = 0, step = 1, pageCount = 0))
    }

    @Test
    fun `less content than dots shows a dot for each`() {
        val dots = stackDotWindow(pageCount = 2, activeIndex = 1, maxDots = 3)

        assertEquals(0..1, dots)
        assertEquals(0, stackPlusCount(pageCount = 2, dotCount = dots.count()))
    }

    @Test
    fun `depth beyond the dots collapses into plus N`() {
        val dots = stackDotWindow(pageCount = 7, activeIndex = 0, maxDots = 3)

        assertEquals(0..2, dots)
        assertEquals(4, stackPlusCount(pageCount = 7, dotCount = dots.count()))
    }

    @Test
    fun `the dot window shifts only past the last visible content`() {
        assertEquals(0..2, stackDotWindow(pageCount = 7, activeIndex = 2, maxDots = 3))
        assertEquals(1..3, stackDotWindow(pageCount = 7, activeIndex = 3, maxDots = 3))
        assertEquals(4..6, stackDotWindow(pageCount = 7, activeIndex = 6, maxDots = 3))
    }

    @Test
    fun `an empty stack has no dots`() {
        assertTrue(stackDotWindow(pageCount = 0, activeIndex = 0, maxDots = 3).isEmpty())
        assertEquals(0, stackPlusCount(pageCount = 0, dotCount = 0))
    }

    @Test
    fun `the dot count never exceeds the token`() {
        val dots = stackDotWindow(
            pageCount = 20,
            activeIndex = 11,
            maxDots = Tokens.Indicator.Stack.MAX_DOTS,
        )

        assertEquals(Tokens.Indicator.Stack.MAX_DOTS, dots.count())
        assertTrue(11 in dots)
    }
}
