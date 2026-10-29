package app.cue.launcher.system.bridge

import app.cue.launcher.data.bridge.BridgeConfigRepository
import app.cue.launcher.data.bridge.BridgeSettings
import app.cue.launcher.data.bridge.OpenedPairing
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// The replay window lets lock-states in out of order, so only one sealed after the last applied may
// change the state. Seqs count per K_ble, so a new key starts the order over; forgetting keeps it, as
// a frame still on its way from before is no newer for that.
internal class MacLockCache {

    var locked: Boolean? = null
        private set

    private var keyTag: String? = null

    private var lastSeq = -1L

    fun received(keyTag: String, seq: Long, locked: Boolean): Boolean {
        if (keyTag == this.keyTag && seq <= lastSeq) return false
        this.keyTag = keyTag
        lastSeq = seq
        this.locked = locked
        return true
    }

    fun forget() {
        locked = null
    }
}

// A debug pairing has no companion window, so there the Mac counts as present for as long as
// DevPresence has its endpoint.
internal fun macLockKnowable(settings: BridgeSettings, macFingerprint: String, presence: MacPresence): Boolean =
    settings.enabled &&
        settings.pairing?.macFingerprint == macFingerprint &&
        (usesDevPresence(settings) || presence != MacPresence.ABSENT)

// The lock-state as the Mac last reported it, whether or not the Mac is still around. What a tile
// or a condition reads is BridgeConditions' MacLockSource, which also asks the presence.
fun interface ReportedMacLock {

    fun reported(): Flow<Boolean?>
}

// Whether the paired Mac's screen is locked, as its last lock-state said; null while that is unknown.
// Only frames that already came in fill it, so it adds nothing that runs. It is forgotten when the
// window publishes ABSENT and when the pairing is wiped, and a frame still on its way from an
// attendance or a session that was cancelled is not applied after that.
@SingleIn(AppScope::class)
@Inject
class BridgeMacLock(
    private val config: BridgeConfigRepository,
    private val nearby: BridgeNearby,
) : ReportedMacLock {

    private val lock = Any()

    private val cache = MacLockCache()

    private val current = MutableStateFlow<Boolean?>(null)

    // The settings cannot be read under the lock, so a forget between that read and the lock, as an
    // unpair makes right after wiping them, voids what was read.
    private var forgotten = 0L

    val locked: StateFlow<Boolean?> = current.asStateFlow()

    override fun reported(): Flow<Boolean?> = locked

    internal suspend fun received(pairing: OpenedPairing, seq: Long, locked: Boolean) {
        val applied = record(pairing, seq, locked)
        BridgeLog.event(
            if (applied) "mac lock-state locked=$locked seq=$seq" else "mac lock-state seq=$seq not applied",
        )
    }

    internal suspend fun record(pairing: OpenedPairing, seq: Long, locked: Boolean): Boolean {
        val before = synchronized(lock) { forgotten }
        val settings = config.settings()
        val caller = currentCoroutineContext()
        return synchronized(lock) {
            caller.ensureActive()
            val applies = forgotten == before &&
                macLockKnowable(settings, pairing.macFingerprint, nearby.state.value) &&
                cache.received(pairing.keyTag, seq, locked)
            if (applies) current.value = cache.locked
            applies
        }
    }

    internal fun forget() = synchronized(lock) {
        forgotten++
        cache.forget()
        current.value = null
    }
}
