package app.cue.launcher.feature.home

import android.graphics.Rect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import app.cue.launcher.system.ActionOutcome
import app.cue.launcher.system.InstalledApp
import app.cue.launcher.system.media.MediaControls
import app.cue.launcher.system.media.MediaWidgetRead
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Immutable
data class MediaWidgetState(

    // null when the chosen player is not installed.
    val appLabel: String?,
    val read: MediaWidgetRead,
)

@Stable
interface MediaFeed {

    fun state(): Flow<MediaWidgetState>

    suspend fun open(sourceBounds: Rect?): ActionOutcome

    suspend fun players(include: Set<String> = emptySet()): List<InstalledApp>

    companion object {

        val NONE: MediaFeed = object : MediaFeed {
            override fun state(): Flow<MediaWidgetState> = flowOf()
            override suspend fun open(sourceBounds: Rect?): ActionOutcome = ActionOutcome.TARGET_MISSING
            override suspend fun players(include: Set<String>): List<InstalledApp> = emptyList()
        }
    }
}

val LocalMediaFeed: ProvidableCompositionLocal<MediaFeed> = compositionLocalOf { MediaFeed.NONE }

// The builder shows the real widget, and a tap on its play button must not start the real player.
fun MediaFeed.inert(): MediaFeed {
    val live = this
    return object : MediaFeed {
        override fun state(): Flow<MediaWidgetState> = live.state().map { state ->
            val read = state.read
            if (read is MediaWidgetRead.Playing) state.copy(read = read.copy(controls = NO_CONTROLS)) else state
        }

        override suspend fun open(sourceBounds: Rect?): ActionOutcome = ActionOutcome.DONE
        override suspend fun players(include: Set<String>): List<InstalledApp> = live.players(include)
    }
}

private val NO_CONTROLS = object : MediaControls {
    override fun play() = Unit
    override fun pause() = Unit
    override fun previous() = Unit
    override fun next() = Unit
}
