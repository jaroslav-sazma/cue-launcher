package app.cue.launcher.system.media

import android.media.session.PlaybackState
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.system.InstalledApp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NowPlayingTest {

    private val everything = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
        PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SKIP_TO_NEXT

    @Test
    fun `a playing session offers pause and both skips`() {
        val buttons = mediaButtonsOf(PlaybackState.STATE_PLAYING, everything)

        assertEquals(MediaButtons(playing = true, canPlayPause = true, canPrevious = true, canNext = true), buttons)
    }

    @Test
    fun `buffering counts as playing`() {
        assertTrue(mediaButtonsOf(PlaybackState.STATE_BUFFERING, everything).playing)
    }

    @Test
    fun `the last track in the queue has no next`() {
        val actions = everything and PlaybackState.ACTION_SKIP_TO_NEXT.inv()

        val buttons = mediaButtonsOf(PlaybackState.STATE_PAUSED, actions)

        assertFalse(buttons.canNext)
        assertTrue(buttons.canPrevious)
        assertTrue(buttons.canPlayPause)
    }

    @Test
    fun `play is checked against the action the button would send`() {
        assertFalse(mediaButtonsOf(PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PAUSE).canPlayPause)
        assertFalse(mediaButtonsOf(PlaybackState.STATE_PLAYING, PlaybackState.ACTION_PLAY).canPlayPause)
        assertTrue(mediaButtonsOf(PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PLAY_PAUSE).canPlayPause)
    }

    @Test
    fun `a session without a playback state has nothing to press`() {
        val buttons = mediaButtonsOf(state = null, actions = 0L)

        assertEquals(MediaButtons(playing = false, canPlayPause = false, canPrevious = false, canNext = false), buttons)
    }

    @Test
    fun `the playing session wins over a more recent paused one`() {
        val sessions = listOf("phone" to false, "cast" to true)

        assertEquals("cast" to true, pickedSession(sessions) { it.second })
    }

    @Test
    fun `without anything playing the most recent session is picked`() {
        val sessions = listOf("recent" to false, "older" to false)

        assertEquals("recent" to false, pickedSession(sessions) { it.second })
        assertNull(pickedSession(emptyList<String>()) { false })
    }

    @Test
    fun `the first non-blank text wins`() {
        val texts = mapOf("title" to " ", "display_title" to "Teardrops", "artist" to "Bring Me The Horizon")

        assertEquals("Teardrops", firstText(listOf("title", "display_title"), texts::get))
        assertEquals("Bring Me The Horizon", firstText(listOf("artist", "album_artist"), texts::get))
        assertNull(firstText(listOf("subtitle"), texts::get))
    }

    @Test
    fun `the player list offers the wanted apps that are installed, each once`() {
        fun app(packageName: String, className: String = "Main") =
            InstalledApp(ComponentRef(packageName, className, userSerial = 0), packageName)
        val installed = listOf(
            app("com.spotify.music"),
            app("com.spotify.music", "Widgets"),
            app("com.android.chrome"),
            app("org.example.radio"),
        )

        val offered = playerChoices(installed, wanted = setOf("com.spotify.music", "org.example.radio"))

        assertEquals(listOf("com.spotify.music", "org.example.radio"), offered.map { it.component.packageName })
    }

    @Test
    fun `a wanted app that is not installed is not offered`() {
        assertTrue(playerChoices(emptyList(), wanted = setOf("com.spotify.music")).isEmpty())
    }

    @Test
    fun `cover luminance is the average of its pixels`() {
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()

        assertEquals(0.5, averageLuminance(intArrayOf(black, white))!!, 1e-9)
        assertEquals(0.0, averageLuminance(intArrayOf(black))!!, 1e-9)
        assertNull(averageLuminance(IntArray(0)))
    }
}
