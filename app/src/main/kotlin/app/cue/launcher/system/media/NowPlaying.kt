package app.cue.launcher.system.media

import android.graphics.Bitmap
import android.media.session.PlaybackState
import androidx.compose.runtime.Immutable
import app.cue.launcher.designsystem.relativeLuminance
import app.cue.launcher.engine.MediaPlayback
import app.cue.launcher.system.mediaPlaybackOf

sealed interface MediaWidgetRead {

    data object NoAccess : MediaWidgetRead

    data object Idle : MediaWidgetRead

    data class Playing(val track: NowPlaying, val controls: MediaControls) : MediaWidgetRead
}

@Immutable
data class NowPlaying(
    val title: String,
    val artist: String?,
    val art: Bitmap?,
    val artLuminance: Double?,
    val buttons: MediaButtons,
)

data class MediaButtons(
    val playing: Boolean,
    val canPlayPause: Boolean,
    val canPrevious: Boolean,
    val canNext: Boolean,
)

interface MediaControls {
    fun play()
    fun pause()
    fun previous()
    fun next()
}

internal fun isPlaying(state: Int?): Boolean = mediaPlaybackOf(listOf(state)) == MediaPlayback.PLAYING

internal fun mediaButtonsOf(state: Int?, actions: Long): MediaButtons {
    val playing = isPlaying(state)
    val toggle = if (playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
    return MediaButtons(
        playing = playing,
        canPlayPause = state != null && actions and (toggle or PlaybackState.ACTION_PLAY_PAUSE) != 0L,
        canPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
        canNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
    )
}

// A player keeps one session per playback target (the phone, a cast device), so an app can hold
// several at once; the one actually playing wins, otherwise the most recently active one.
internal fun <T> pickedSession(sessions: List<T>, isPlaying: (T) -> Boolean): T? =
    sessions.firstOrNull(isPlaying) ?: sessions.firstOrNull()

internal fun firstText(keys: List<String>, text: (String) -> CharSequence?): String? =
    keys.firstNotNullOfOrNull { key -> text(key)?.toString()?.takeIf(String::isNotBlank) }

internal fun averageLuminance(pixels: IntArray): Double? {
    if (pixels.isEmpty()) return null
    return pixels.sumOf { relativeLuminance(it and RGB_MASK) } / pixels.size
}

private const val RGB_MASK = 0x00FFFFFF
