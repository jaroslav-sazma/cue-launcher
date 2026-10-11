package app.cue.launcher.system

import android.media.session.PlaybackState
import android.os.SystemClock
import app.cue.launcher.engine.MediaPlayback
import app.cue.launcher.engine.MediaSessions
import app.cue.launcher.engine.StateAspect
import app.cue.launcher.engine.StateProvider
import app.cue.launcher.engine.SystemState
import app.cue.launcher.engine.agedBy
import app.cue.launcher.engine.mediaGraceWaitMillis
import app.cue.launcher.engine.mediaActivityOf
import app.cue.launcher.system.media.MediaSessionWatch
import app.cue.launcher.system.media.PlayerSession
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@SingleIn(AppScope::class)
@Inject
class MediaConditions(private val watch: MediaSessionWatch) : StateProvider {

    override val aspect: StateAspect = StateAspect.MEDIA

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<SystemState> = watch.sessions
        .map { sessions -> sessions?.let { perApp(it, SystemClock.elapsedRealtime()) } }
        .distinctUntilChanged()
        .flatMapLatest { measured -> if (measured == null) flowOf(UNMEASURED) else readings(measured) }

    private fun perApp(sessions: List<PlayerSession>, realtimeMillis: Long): Map<String, MediaSessions> =
        sessions.groupBy(PlayerSession::packageName).mapValues { (_, app) ->
            mediaSessionsOf(app.map(PlayerSession::state), realtimeMillis)
        }

    private fun readings(measured: Map<String, MediaSessions>): Flow<SystemState> = flow {
        var sessions = measured
        while (true) {
            emit(SystemState(media = mediaActivityOf(sessions)))
            val wait = mediaGraceWaitMillis(sessions) ?: break
            delay(wait)
            sessions = sessions.agedBy(wait)
        }
    }

    private companion object {

        val UNMEASURED = SystemState(media = null)
    }
}

private fun mediaSessionsOf(states: List<PlaybackState?>, realtimeMillis: Long): MediaSessions {
    val playback = mediaPlaybackOf(states.map { it?.state })
    if (playback != MediaPlayback.PAUSED) return MediaSessions(playback)
    val pausedUpdates = states
        .filter { it?.state == PlaybackState.STATE_PAUSED }
        .map { it?.lastPositionUpdateTime ?: 0L }
    return MediaSessions(playback, pausedForMillis(pausedUpdates, realtimeMillis))
}

internal fun mediaPlaybackOf(states: List<Int?>): MediaPlayback = when {
    states.any { it != null && it in ACTIVE_PLAYBACK_STATES } -> MediaPlayback.PLAYING
    states.any { it == PlaybackState.STATE_PAUSED } -> MediaPlayback.PAUSED
    else -> MediaPlayback.IDLE
}

internal fun pausedForMillis(updateTimes: List<Long>, realtimeMillis: Long): Long? =
    updateTimes.filter { it > 0L }.maxOrNull()?.let { (realtimeMillis - it).coerceAtLeast(0L) }

private val ACTIVE_PLAYBACK_STATES = setOf(
    PlaybackState.STATE_PLAYING,
    PlaybackState.STATE_BUFFERING,
    PlaybackState.STATE_FAST_FORWARDING,
    PlaybackState.STATE_REWINDING,
)
