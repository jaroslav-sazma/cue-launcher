package app.cue.launcher.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class BackPreviewTest {

    @Test
    fun `an unstarted gesture moves nothing`() {
        assertEquals(0f, backPreviewTravel(0f))
    }

    @Test
    fun `a completed gesture is a full preview`() {
        assertEquals(1f, backPreviewTravel(1f))
    }

    @ParameterizedTest(name = "progress {0} is out of range → it gets clamped")
    @ValueSource(floats = [-1f, -0.001f])
    fun `negative progress is clamped to zero`(progress: Float) {
        assertEquals(0f, backPreviewTravel(progress))
    }

    @ParameterizedTest(name = "progress {0} is out of range → it gets clamped")
    @ValueSource(floats = [1.001f, 5f])
    fun `progress above one is clamped to one`(progress: Float) {
        assertEquals(1f, backPreviewTravel(progress))
    }

    @Test
    fun `the preview grows with the drag`() {
        val steps = (0..10).map { step -> backPreviewTravel(step / 10f) }
        steps.zipWithNext { previous, next ->
            assertTrue(next > previous, "the preview must grow: $previous → $next")
        }
    }

    @Test
    fun `the start of the drag is faster than the end`() {

        val start = backPreviewTravel(0.1f) - backPreviewTravel(0f)
        val end = backPreviewTravel(1f) - backPreviewTravel(0.9f)
        assertTrue(start > end, "the start $start should be stronger than the end $end")
    }

    @Test
    fun `half the drag already shows most of the preview`() {
        assertEquals(0.75f, backPreviewTravel(0.5f))
    }
}
