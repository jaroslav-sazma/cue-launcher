package app.cue.launcher.system.media

import android.app.Application
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.cue.launcher.system.notifications.NotificationListenerLink
import app.cue.launcher.system.notifications.notificationListenerComponent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

data class PlayerSession(
    val controller: MediaController,
    val state: PlaybackState?,
    val metadata: MediaMetadata?,
) {
    val packageName: String get() = controller.packageName
}

// One subscription to the media sessions for the whole app: the "something is playing" condition
// and every media widget read the same callbacks instead of each registering its own. null while
// the sessions cannot be read, i.e. without notification access.
@SingleIn(AppScope::class)
@Inject
class MediaSessionWatch(
    private val application: Application,
    private val listener: NotificationListenerLink,
    scope: CoroutineScope,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    val sessions: Flow<List<PlayerSession>?> = listener.state
        .map { it.enabled }
        .distinctUntilChanged()
        .flatMapLatest { enabled -> if (enabled) watch() else flowOf(null) }
        // Stops the moment the last reader goes, and forgets what it saw: a reader arriving later
        // must not start from sessions that may have ended in the meantime.
        .shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)

    fun current(packageName: String): List<MediaController>? {
        val manager = application.getSystemService(MediaSessionManager::class.java) ?: return null
        return try {
            manager.getActiveSessions(notificationListenerComponent(application))
                .filter { it.packageName == packageName }
        } catch (denied: SecurityException) {
            Log.w(TAG, "Media sessions cannot be read", denied)
            null
        }
    }

    // Registration, callbacks and release all run on the main thread, so a callback already queued
    // when the watch stops can never register anything again.
    private fun watch(): Flow<List<PlayerSession>?> = callbackFlow {
        val manager = application.getSystemService(MediaSessionManager::class.java)
        if (manager == null) {
            send(null)
            awaitClose { }
            return@callbackFlow
        }
        val watch = SessionWatch(manager, notificationListenerComponent(application)) { trySend(it) }
        watch.start()
        awaitClose { watch.stop() }
    }.flowOn(Dispatchers.Main.immediate)
}

private class SessionWatch(
    private val manager: MediaSessionManager,
    private val component: ComponentName,
    private val onSessions: (List<PlayerSession>?) -> Unit,
) {

    private val handler = Handler(Looper.getMainLooper())
    private var watched: List<Watched> = emptyList()
    private var stopped = false

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        rebind(controllers.orEmpty())
    }

    fun start() {
        val active = try {
            manager.addOnActiveSessionsChangedListener(sessionsChanged, component, handler)
            manager.getActiveSessions(component)
        } catch (denied: SecurityException) {
            Log.w(TAG, "Media sessions cannot be subscribed to", denied)
            onSessions(null)
            return
        }
        rebind(active)
    }

    fun stop() {
        stopped = true
        manager.removeOnActiveSessionsChangedListener(sessionsChanged)
        release()
    }

    private fun rebind(controllers: List<MediaController>) {
        if (stopped) return
        release()
        watched = controllers.map(::Watched)
        report()
    }

    private fun report() {
        if (!stopped) onSessions(watched.map(Watched::session))
    }

    private fun release() {
        watched.forEach(Watched::release)
        watched = emptyList()
    }

    // The callbacks hand over the new state and metadata themselves; reading them back from the
    // controller would be another binder call, and for the metadata another copy of the cover.
    private inner class Watched(private val controller: MediaController) : MediaController.Callback() {

        private var state: PlaybackState? = controller.playbackState
        private var metadata: MediaMetadata? = controller.metadata

        init {
            controller.registerCallback(this, handler)
        }

        val session: PlayerSession get() = PlayerSession(controller, state, metadata)

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            this.state = state
            report()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            this.metadata = metadata
            report()
        }

        override fun onSessionDestroyed() {
            if (stopped) return
            release()
            watched = watched - this
            report()
        }

        fun release() = controller.unregisterCallback(this)
    }
}

private const val TAG = "MediaSessionWatch"
