package app.cue.launcher.system.notifications

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import app.cue.launcher.data.notifications.BadgeKey
import app.cue.launcher.data.notifications.NotificationListenerState
import app.cue.launcher.data.notifications.shouldRequestRebind
import app.cue.launcher.engine.PostedNotification
import androidx.core.content.edit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

@Suppress("TooManyFunctions") // every way into and out of the one binding, under one lock
@SingleIn(AppScope::class)
@Inject
class NotificationListenerLink(private val application: Application) {

    // Measured at once rather than left at false: a process woken by a receiver evaluates media
    // conditions before any screen has asked, and the media sessions need only the granted access.
    private val current = MutableStateFlow(
        NotificationListenerState(enabled = notificationListenerEnabled(application)),
    )

    @Volatile
    private var listener: WeakReference<NotificationListenerService>? = null

    val state: StateFlow<NotificationListenerState> = current.asStateFlow()

    private val counts = MutableStateFlow<Map<BadgeKey, Int>>(emptyMap())

    val badges: StateFlow<Map<BadgeKey, Int>> = counts.asStateFlow()

    private val hold by lazy { ListenerHold(SharedNeed(application)) }

    private val mirrorSinks = CopyOnWriteArrayList<NotificationMirrorSink>()

    // Every change of the hold is made together with the binder call it calls for, under one lock,
    // so they reach the system in the order they happened. requestRebind on a listener that is
    // already bound does nothing at all — onListenerConnected does not come again — and
    // requestUnbind on one that is not bound is just as harmless, so both are asked for freely and
    // the hold alone decides which one is wanted.
    private val lock = Any()

    // Followed for the whole process from the moment the home screen is first created, not only
    // while it is started: a scenario saved just before pressing Home must still be heard. Besides
    // the launcher's own screens, only a background event that starts or ends a tile waiting for a
    // notification changes the need, so the collector waits idle otherwise.
    suspend fun followNeed(needed: Flow<Boolean>) {
        needed.distinctUntilChanged().collect { wanted ->
            synchronized(lock) {
                if (wanted) {
                    hold.need()
                    refresh()
                } else if (hold.unneed()) {
                    counts.value = emptyMap()
                    requestNotificationListenerUnbind(application)
                }
            }
        }
    }

    // A sample picked in the builder binds the listener only for as long as the home screen is
    // started; the access state is measured again on every return, as it may have been granted.
    suspend fun holdSession() {
        synchronized(lock) { hold.enter() }
        try {
            refresh()
            awaitCancellation()
        } finally {
            synchronized(lock) {
                if (hold.leave()) {
                    counts.value = emptyMap()
                    requestNotificationListenerUnbind(application)
                }
            }
        }
    }

    fun bindForSample() = synchronized(lock) {
        if (hold.sample()) refresh()
    }

    // The one place the Mac bridge needs the listener: for as long as block runs, which is while a
    // paired Mac is in reach — inside the foreground session, or inside the companion window the
    // system binds while the Mac is nearby.
    suspend fun holdForBridge(block: suspend () -> Unit) {
        synchronized(lock) {
            hold.bridge()
            refresh()
        }
        try {
            block()
        } finally {
            synchronized(lock) {
                if (hold.unbridge()) {
                    counts.value = emptyMap()
                    requestNotificationListenerUnbind(application)
                }
            }
        }
    }

    // With the bridge off this stays empty, and checking it is all the listener's callbacks do for it.
    val mirrors: List<NotificationMirrorSink> get() = mirrorSinks

    suspend fun mirrorInto(sink: NotificationMirrorSink, block: suspend () -> Unit) {
        mirrorSinks.add(sink)
        try {
            block()
        } finally {
            mirrorSinks.remove(sink)
        }
    }

    // null while no listener is bound or the shade cannot be read, which is not an empty shade.
    fun <T> readShade(read: (List<StatusBarNotification>, NotificationListenerService.RankingMap?) -> T): T? {
        val service = listener?.get()
        val active = service?.activeOrNull()
        return if (service == null || active == null) null else read(active, service.currentRanking)
    }

    // null when nobody wants the binding: the listener is released at once and replays nothing.
    fun onConnected(service: NotificationListenerService): Long? = synchronized(lock) {
        if (!hold.wanted) {
            Log.i(NOTIFICATION_LISTENER_TAG, "Listener bound while not wanted, releasing it")
            // The system can bind a listener that is released — after its process died, or when a
            // bind was still on its way as it was released. requestUnbind alone is then ignored, as
            // the component counts as released already; the rebind first marks it bound again (a
            // no-op for the connection itself), so the unbind really takes it down. Only safe with
            // the connection known to exist: on an unbound listener the rebind would start a new one.
            requestNotificationListenerRebind(application)
            requestNotificationListenerUnbind(application)
            return@synchronized null
        }
        Log.i(NOTIFICATION_LISTENER_TAG, "Listener bound")
        listener = WeakReference(service)
        current.update { NotificationListenerState(enabled = true, connected = true) }
        hold.connected(System.currentTimeMillis()) { application.previousDeathMillis() ?: processStartMillis() }
    }

    // A disconnect nobody asked for — access revoked, the system dropping the binding — clears the
    // badges and binds again while the listener is still wanted. A binding that was never taken up
    // can still be the one the hold is waiting for.
    fun onDisconnected(service: NotificationListenerService) = synchronized(lock) {
        if (listener?.get() !== service) {
            if (hold.wanted && listener == null) refresh()
            return@synchronized
        }
        Log.i(NOTIFICATION_LISTENER_TAG, "Listener unbound")
        listener = null
        current.update { it.copy(connected = false) }
        counts.value = emptyMap()
        if (!hold.wanted) return@synchronized
        hold.lost(System.currentTimeMillis())
        refresh()
    }

    fun isCurrent(service: NotificationListenerService): Boolean = listener?.get() === service

    // A count worked out just before a release must not land after the release cleared them.
    fun publishBadges(service: NotificationListenerService, badges: Map<BadgeKey, Int>) = synchronized(lock) {
        if (isCurrent(service)) counts.value = badges
    }

    suspend fun activeNotification(key: String): StatusBarNotification? {
        val service = withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) {
            current.first { it.connected }
            listener?.get()
        } ?: return null
        return try {
            service.getActiveNotifications(arrayOf(key))?.firstOrNull()
        } catch (denied: SecurityException) {
            Log.w(NOTIFICATION_LISTENER_TAG, "The posted notification cannot be read", denied)
            null
        }
    }

    // false while no listener is bound. The system ignores a key that is no longer posted and one
    // the user may not clear; a cancel that lands comes back through onNotificationRemoved.
    fun cancel(key: String): Boolean {
        val service = listener?.get() ?: return false
        return try {
            service.cancelNotification(key)
            true
        } catch (denied: SecurityException) {
            Log.w(NOTIFICATION_LISTENER_TAG, "The posted notification cannot be cancelled", denied)
            false
        }
    }

    // null while no listener is bound, which is not the same as an app with nothing posted.
    fun activeNotifications(packageName: String): List<PostedNotification>? {
        val service = listener?.get() ?: return null
        return service.activeOrEmpty()
            .filter { it.packageName.equals(packageName.trim(), ignoreCase = true) }
            .map(::postedNotificationOf)
            .filter { !it.isGroupSummary && (it.title != null || it.text != null) }
    }

    // Access revoked while the listener was released leaves no disconnect to clear the badges by.
    fun refresh() = synchronized(lock) {
        val enabled = notificationListenerEnabled(application)
        val measured = current.updateAndGet { it.copy(enabled = enabled, connected = enabled && it.connected) }
        if (!enabled) counts.value = emptyMap()
        if (shouldRequestRebind(measured) && hold.wanted) requestNotificationListenerRebind(application)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 2_000L
    }
}

// SharedPreferences rather than DataStore: the hold needs the value synchronously, on the main
// thread of a process the system has just started to bind the listener, before anything is loaded.
private class SharedNeed(application: Application) : NeedStore {

    private val preferences = application.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun read(): Boolean = preferences.getBoolean(KEY, false)

    override fun write(needed: Boolean) {
        // Committed at once: a process killed right after would otherwise refuse its own listener.
        preferences.edit(commit = true) { putBoolean(KEY, needed) }
    }

    private companion object {
        const val FILE = "notification_listener"
        const val KEY = "needed"
    }
}

// The exit record is stamped when the system notices the death, after posts the dying process no
// longer handled; the margin takes those in. A post it did handle comes out as a running activation
// that already lasts as long, which the replay leaves alone.
private fun Application.previousDeathMillis(): Long? = runCatching {
    getSystemService(ActivityManager::class.java)
        ?.getHistoricalProcessExitReasons(null, 0, 1)
        ?.firstOrNull()
        ?.timestamp
        ?.minus(DEATH_MARGIN_MILLIS)
}.getOrNull()

private const val DEATH_MARGIN_MILLIS = 5_000L

private fun processStartMillis(): Long =
    System.currentTimeMillis() - (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime())
