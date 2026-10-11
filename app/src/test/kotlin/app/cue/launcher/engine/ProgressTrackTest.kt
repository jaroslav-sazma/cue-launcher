package app.cue.launcher.engine

import app.cue.launcher.data.scenario.SemanticStyle
import app.cue.launcher.data.scenario.TileProgress
import app.cue.launcher.data.scenario.TileProgressPoint
import app.cue.launcher.data.scenario.TileProgressSegment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProgressTrackTest {

    @Test
    fun `with no progress there is nothing to draw`() {
        assertNull(progressTrack(null))
    }

    @Test
    fun `progress without a scale is dropped`() {
        assertNull(progressTrack(TileProgress(value = 3, max = 0)))
    }

    @Test
    fun `determinate progress without segments has one filled and one remaining band`() {
        val track = progressTrack(TileProgress(value = 25, max = 100))!!

        assertEquals(0.25f, track.fraction)
        assertEquals(25, track.percent)
        assertEquals(
            listOf(
                ProgressBand(start = 0f, end = 0.25f, filled = true),
                ProgressBand(start = 0.25f, end = 1f, filled = false),
            ),
            track.bands,
        )
    }

    @Test
    fun `finished progress has no remaining band`() {
        val track = progressTrack(TileProgress(value = 100, max = 100))!!

        assertEquals(listOf(ProgressBand(0f, 1f, filled = true)), track.bands)
    }

    @Test
    fun `zero progress has no filled band`() {
        val track = progressTrack(TileProgress(value = 0, max = 100))!!

        assertEquals(listOf(ProgressBand(0f, 1f, filled = false)), track.bands)
    }

    @Test
    fun `a value outside the scale is clamped`() {
        assertEquals(1f, progressTrack(TileProgress(value = 500, max = 100))!!.fraction)
        assertEquals(0f, progressTrack(TileProgress(value = -5, max = 100))!!.fraction)
    }

    @Test
    fun `the scale is set by the sum of the segments`() {
        val progress = TileProgress(
            value = 15,
            max = 1,
            segments = listOf(TileProgressSegment(10), TileProgressSegment(30)),
        )

        val track = progressTrack(progress)!!

        assertEquals(0.375f, track.fraction)
    }

    @Test
    fun `a segment under the progress splits into two bands`() {
        val progress = TileProgress(
            value = 30,
            max = 100,
            segments = listOf(
                TileProgressSegment(50, SemanticStyle.SAFE),
                TileProgressSegment(50, SemanticStyle.CAUTION),
            ),
        )

        val track = progressTrack(progress)!!

        assertEquals(
            listOf(
                ProgressBand(0f, 0.3f, SemanticStyle.SAFE, filled = true),
                ProgressBand(0.3f, 0.5f, SemanticStyle.SAFE, filled = false),
                ProgressBand(0.5f, 1f, SemanticStyle.CAUTION, filled = false),
            ),
            track.bands,
        )
    }

    @Test
    fun `progress on a segment edge does not split`() {
        val progress = TileProgress(
            value = 50,
            max = 100,
            segments = listOf(TileProgressSegment(50), TileProgressSegment(50)),
        )

        val track = progressTrack(progress)!!

        assertEquals(
            listOf(
                ProgressBand(0f, 0.5f, filled = true),
                ProgressBand(0.5f, 1f, filled = false),
            ),
            track.bands,
        )
    }

    @Test
    fun `a zero-length segment is skipped`() {
        val progress = TileProgress(
            value = 0,
            max = 10,
            segments = listOf(TileProgressSegment(0), TileProgressSegment(10, SemanticStyle.INFO)),
        )

        val track = progressTrack(progress)!!

        assertEquals(listOf(ProgressBand(0f, 1f, SemanticStyle.INFO)), track.bands)
    }

    @Test
    fun `zero-length segments let max decide`() {
        val progress = TileProgress(value = 5, max = 10, segments = listOf(TileProgressSegment(0)))

        val track = progressTrack(progress)!!

        assertEquals(0.5f, track.fraction)
        assertEquals(2, track.bands.size)
    }

    @Test
    fun `points get a fractional position`() {
        val progress = TileProgress(
            value = 0,
            max = 20,
            points = listOf(
                TileProgressPoint(5, SemanticStyle.INFO),
                TileProgressPoint(20, SemanticStyle.DANGER),
            ),
        )

        val track = progressTrack(progress)!!

        assertEquals(
            listOf(ProgressMark(0.25f, SemanticStyle.INFO), ProgressMark(1f, SemanticStyle.DANGER)),
            track.marks,
        )
    }

    @Test
    fun `a point outside the track is dropped`() {
        val progress = TileProgress(
            value = 0,
            max = 10,
            points = listOf(TileProgressPoint(-1), TileProgressPoint(11), TileProgressPoint(10)),
        )

        assertEquals(listOf(ProgressMark(1f)), progressTrack(progress)!!.marks)
    }

    @Test
    fun `indeterminate progress fills nothing`() {
        val progress = TileProgress(value = 40, max = 100, indeterminate = true)

        val track = progressTrack(progress)!!

        assertNull(track.fraction)
        assertNull(track.percent)
        assertTrue(track.bands.none { it.filled })
    }

    @Test
    fun `indeterminate progress without a scale is the whole track`() {
        val track = progressTrack(TileProgress(indeterminate = true))!!

        assertNull(track.fraction)
        assertEquals(listOf(ProgressBand(0f, 1f)), track.bands)
    }
}
