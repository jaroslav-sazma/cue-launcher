package app.cue.launcher.system.notifications

// Whether the notification listener should be bound. As with the launcher Android ships, it stays
// bound — in the background too — for as long as something needs the posts: a scenario with a
// notification condition, or the badges. With nothing needing them it is released, so no post of
// any app wakes the process. The need is kept on disk: when the system restarts the process to bind
// the listener again, it has to decide before any screen has loaded a scenario. A sample picked in
// the builder binds it only for the rest of the session; the Mac bridge binds it while a paired Mac
// is in reach, for each holder on its own: the session link while the home screen is started, and
// the companion window while the system keeps it bound.
internal class ListenerHold(private val store: NeedStore) {

    private var sessions = 0

    // Whether the process before this one needed the posts: only then did it hear them, and only
    // then does this one have a gap to replay from its predecessor's death.
    val neededAtStart: Boolean = store.read()

    private var needed = neededAtStart

    private var sampling = false

    private var bridges = 0

    private var connectedBefore = false

    private var unheardSince: Long? = null

    val wanted: Boolean
        @Synchronized get() = needed || sampling || bridges > 0

    @Synchronized
    fun enter() {
        sessions++
    }

    // True when the last session leaves a listener bound only for a sample, which is released; a
    // picker still open in an activity recreated meanwhile binds it again on its next read.
    @Synchronized
    fun leave(): Boolean {
        sessions = (sessions - 1).coerceAtLeast(0)
        if (sessions > 0 || !sampling) return false
        sampling = false
        val release = !needed && bridges == 0
        if (release) released()
        return release
    }

    @Synchronized
    fun need() {
        if (needed) return
        needed = true
        store.write(true)
    }

    // True when the listener is to be released because nothing wants it any more.
    @Synchronized
    fun unneed(): Boolean {
        if (!needed) return false
        needed = false
        store.write(false)
        val release = !sampling && bridges == 0
        if (release) released()
        return release
    }

    // False outside a session: a sample picker still retrying after the launcher left the screen
    // must not bind the listener behind it.
    @Synchronized
    fun sample(): Boolean {
        if (sessions == 0) return false
        sampling = true
        return true
    }

    @Synchronized
    fun bridge() {
        bridges++
    }

    // True when the bridge's last holder was the last to want the listener, which is then released.
    @Synchronized
    fun unbridge(): Boolean {
        if (bridges == 0 || --bridges > 0) return false
        val release = !needed && !sampling
        if (release) released()
        return release
    }

    // The moment to replay from, as what is still posted is checked against it. The first connect of
    // a process that follows one which needed the posts covers what came while neither ran, from the
    // predecessor's death; otherwise nothing was being heard and nothing is replayed. Later, only a
    // binding the system dropped opens a window, at that moment.
    @Synchronized
    fun connected(now: Long, previousDeath: () -> Long): Long {
        val opened = when {
            connectedBefore -> unheardSince
            neededAtStart -> previousDeath()
            else -> null
        }
        connectedBefore = true
        unheardSince = null
        return opened ?: now
    }

    @Synchronized
    fun lost(now: Long) {
        if (wanted && unheardSince == null) unheardSince = now
    }

    // Released on purpose, nothing was meant to be heard, so the next connect replays nothing.
    private fun released() {
        connectedBefore = true
        unheardSince = null
    }
}

interface NeedStore {
    fun read(): Boolean

    fun write(needed: Boolean)
}
