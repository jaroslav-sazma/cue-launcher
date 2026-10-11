package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Condition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaRulesTest {

    @Test
    fun `a playing player satisfies the condition and there is nothing to wait for`() {
        val sessions = MediaSessions(MediaPlayback.PLAYING)

        assertTrue(mediaPlayingWithGrace(sessions) == true)
        assertNull(mediaGraceWaitMillis(sessions))
    }

    @Test
    fun `silence fails the condition and it is not uncertainty`() {
        val sessions = MediaSessions(MediaPlayback.IDLE)

        assertFalse(mediaPlayingWithGrace(sessions)!!)
        assertNull(mediaGraceWaitMillis(sessions))
    }

    @Test
    fun `an unmeasured state is unknown, not silence`() {
        assertNull(mediaPlayingWithGrace(MediaSessions()))
        assertNull(mediaPlayingWithGrace(MediaSessions(MediaPlayback.UNKNOWN)))
        assertNull(mediaGraceWaitMillis(MediaSessions()))
    }

    @Test
    fun `a fresh pause carries over and ends after a minute`() {
        assertTrue(mediaPlayingWithGrace(paused(0)) == true)
        assertTrue(mediaPlayingWithGrace(paused(MEDIA_GRACE_MILLIS - 1)) == true)
        assertFalse(mediaPlayingWithGrace(paused(MEDIA_GRACE_MILLIS))!!)
        assertFalse(mediaPlayingWithGrace(paused(MEDIA_GRACE_MILLIS * 10))!!)
    }

    @Test
    fun `the wait matches the remainder of the grace period`() {
        assertEquals(MEDIA_GRACE_MILLIS, mediaGraceWaitMillis(paused(0)))
        assertEquals(1L, mediaGraceWaitMillis(paused(MEDIA_GRACE_MILLIS - 1)))
        assertNull(mediaGraceWaitMillis(paused(MEDIA_GRACE_MILLIS)))
    }

    @Test
    fun `an undated pause does not carry over`() {
        val sessions = MediaSessions(MediaPlayback.PAUSED, pausedForMillis = null)

        assertFalse(mediaPlayingWithGrace(sessions)!!)
        assertNull(mediaGraceWaitMillis(sessions))
    }

    @Test
    fun `waiting ages the pause and the grace period ends`() {
        val sessions = paused(1_000)
        val wait = mediaGraceWaitMillis(sessions)!!
        val later = sessions.agedBy(wait)

        assertEquals(MEDIA_GRACE_MILLIS - 1_000, wait)
        assertEquals(MEDIA_GRACE_MILLIS, later.pausedForMillis)
        assertFalse(mediaPlayingWithGrace(later)!!)
        assertNull(mediaGraceWaitMillis(later))
    }

    @Test
    fun `age is only counted for a pause`() {
        assertNull(MediaSessions(MediaPlayback.PLAYING).agedBy(1_000).pausedForMillis)
        assertNull(MediaSessions().agedBy(1_000).pausedForMillis)
    }

    @Test
    fun `the grace period is reflected in the condition evaluation`() {
        val during = SystemState(media = mediaActivityOf(mapOf(SPOTIFY to paused(1))))
        val after = SystemState(media = mediaActivityOf(mapOf(SPOTIFY to paused(MEDIA_GRACE_MILLIS))))
        val unmeasured = SystemState(media = null)

        assertEquals(ConditionOutcome.HOLDS, stateConditionOutcome(Condition.MediaPlaying(), during))
        assertEquals(ConditionOutcome.FAILS, stateConditionOutcome(Condition.MediaPlaying(), after))
        assertEquals(ConditionOutcome.UNKNOWN, stateConditionOutcome(Condition.MediaPlaying(), unmeasured))
    }

    @Test
    fun `a chosen app counts only while that app plays`() {
        val state = SystemState(
            media = mediaActivityOf(
                mapOf(SPOTIFY to MediaSessions(MediaPlayback.PLAYING), YT_MUSIC to MediaSessions(MediaPlayback.IDLE)),
            ),
        )

        assertEquals(ConditionOutcome.HOLDS, stateConditionOutcome(Condition.MediaPlaying(), state))
        assertEquals(
            ConditionOutcome.FAILS,
            stateConditionOutcome(Condition.MediaPlaying(packages = listOf(YT_MUSIC)), state),
        )
        assertEquals(
            ConditionOutcome.HOLDS,
            stateConditionOutcome(Condition.MediaPlaying(packages = listOf(YT_MUSIC, SPOTIFY)), state),
        )
        assertEquals(
            ConditionOutcome.HOLDS,
            stateConditionOutcome(Condition.MediaPlaying(negate = true, packages = listOf(YT_MUSIC)), state),
        )
    }

    @Test
    fun `each app keeps its own grace period`() {
        val sessions = mapOf(SPOTIFY to paused(MEDIA_GRACE_MILLIS - 10), YT_MUSIC to paused(MEDIA_GRACE_MILLIS - 1_000))

        assertEquals(setOf(SPOTIFY, YT_MUSIC), mediaActivityOf(sessions).playing)
        assertEquals(10L, mediaGraceWaitMillis(sessions))
        assertEquals(setOf(YT_MUSIC), mediaActivityOf(sessions.agedBy(10)).playing)
    }

    @Test
    fun `without picked apps the answer is the one the global reading gave`() {
        val cases = listOf(
            mapOf(SPOTIFY to MediaSessions(MediaPlayback.PLAYING), YT_MUSIC to paused(MEDIA_GRACE_MILLIS * 2)),
            mapOf(SPOTIFY to paused(MEDIA_GRACE_MILLIS * 2), YT_MUSIC to paused(1_000)),
            mapOf(SPOTIFY to MediaSessions(MediaPlayback.PAUSED, pausedForMillis = null), YT_MUSIC to paused(1_000)),
            mapOf(SPOTIFY to MediaSessions(MediaPlayback.PAUSED, pausedForMillis = null)),
            mapOf(SPOTIFY to MediaSessions(MediaPlayback.IDLE), YT_MUSIC to paused(MEDIA_GRACE_MILLIS)),
        )
        val expected = listOf(true, true, true, false, false)

        cases.zip(expected).forEach { (sessions, playing) ->
            assertEquals(playing, mediaActivityOf(sessions).isPlaying(emptyList())) { sessions.toString() }
        }
    }

    @Test
    fun `no session means nothing plays, not an unknown`() {
        val state = SystemState(media = mediaActivityOf(emptyMap()))

        assertEquals(ConditionOutcome.FAILS, stateConditionOutcome(Condition.MediaPlaying(), state))
        assertNull(mediaGraceWaitMillis(emptyMap<String, MediaSessions>()))
    }

    private fun paused(forMillis: Long): MediaSessions =
        MediaSessions(MediaPlayback.PAUSED, pausedForMillis = forMillis)

    private companion object {
        const val SPOTIFY = "com.spotify.music"
        const val YT_MUSIC = "com.google.android.apps.youtube.music"
    }
}
