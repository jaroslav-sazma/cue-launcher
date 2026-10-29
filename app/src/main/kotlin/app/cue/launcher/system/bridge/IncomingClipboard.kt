package app.cue.launcher.system.bridge

import android.app.Application
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import app.cue.launcher.data.bridge.BridgeConfigRepository
import app.cue.launcher.data.bridge.BridgeSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

// Mac → phone. A clip lands only while the keyguard is down. Behind it the companion window holds
// the newest one, and the unlock receiver is registered only while something is held; both go when
// the debounced presence says the Mac is absent, or with the window. Without a window, which a debug
// pairing never opens, a clip behind the keyguard is dropped.
@SingleIn(AppScope::class)
@Inject
class IncomingClipboard(
    private val application: Application,
    private val config: BridgeConfigRepository,
    private val writer: ClipboardWriter,
) {

    private val lock = Any()

    private val hold = ClipboardHold(SystemClock::elapsedRealtime)

    private var window: CoroutineScope? = null

    private var summon: (() -> Unit)? = null

    private var receiver: BroadcastReceiver? = null

    fun open(scope: CoroutineScope) = synchronized(lock) {
        hold.clear()
        window = scope
    }

    fun close() = synchronized(lock) {
        window = null
        absent()
    }

    fun absent() = synchronized(lock) {
        hold.clear()
        summon = null
        release()
    }

    // The caller's job is checked under the lock: a clip still on its way from an attendance that
    // was cancelled is not held after absent() cleared the hold.
    internal suspend fun clip(text: String, sensitive: Boolean, ageMs: Long?) {
        val settings = config.settings()
        if (!settings.clipboardFromMac) {
            BridgeLog.event("clipboard ignored: clipboard from the Mac is off")
            return
        }
        val caller = currentCoroutineContext()
        val arrival = synchronized(lock) {
            caller.ensureActive()
            val locked = keyguardLocked()
            if (locked && window == null) {
                hold.clear()
                null
            } else {
                hold.arrived(text, sensitive, ageMs, locked, limitMs(settings)).also { watchUnlock() }
            }
        }
        when (arrival) {
            null -> BridgeLog.event("clipboard dropped: locked, and no companion window to hold it")
            ClipArrival.APPLY -> writer.write(text, sensitive)
            ClipArrival.HOLD -> BridgeLog.event("clipboard held until unlock")
            ClipArrival.STALE -> BridgeLog.event("clipboard dropped: older than the limit")
        }
    }

    // The Mac holds a clip too large for a BLE frame; a visit fetches it.
    internal suspend fun pending(summonVisit: () -> Unit) {
        if (!config.settings().clipboardFromMac) {
            BridgeLog.event("clipboard-pending ignored: clipboard from the Mac is off")
            return
        }
        val caller = currentCoroutineContext()
        val now = synchronized(lock) {
            caller.ensureActive()
            hold.owed(keyguardLocked()).also { now ->
                summon = if (now) null else summonVisit
                watchUnlock()
            }
        }
        if (now) summonVisit() else BridgeLog.event("clipboard-pending held until unlock")
    }

    private suspend fun unlocked() {
        val settings = config.settings()
        val outcome = synchronized(lock) {
            if (!hold.pending || keyguardLocked()) return
            val step = hold.unlocked(limitMs(settings))
            val visit = summon
            summon = null
            release()
            step to visit
        }
        val (step, visit) = outcome
        if (!settings.clipboardFromMac) {
            BridgeLog.event("clipboard ignored at unlock: clipboard from the Mac is off")
            return
        }
        step.clip?.let { clip ->
            BridgeLog.event("clipboard applied at unlock")
            writer.write(clip.text, clip.sensitive)
        }
        if (step.staleClip) BridgeLog.event("clipboard dropped at unlock: older than the limit")
        when (step.owed) {
            OwedOutcome.SUMMON -> visit?.invoke()
            OwedOutcome.STALE -> BridgeLog.event("clipboard-pending dropped at unlock: older than the limit")
            OwedOutcome.NONE -> Unit
        }
    }

    // Called under the lock. The keyguard is read once more after registering, so an unlock that
    // came between the first read and the registration is not missed.
    private fun watchUnlock() {
        val scope = window
        when {
            !hold.pending -> release()
            scope != null && receiver == null -> register(scope)
        }
    }

    private fun register(scope: CoroutineScope) {
        val watching = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                scope.launch { unlocked() }
            }
        }
        // SystemUI sends the broadcast from its own uid, which a not-exported receiver does not hear
        // (the same trap as the Bluetooth module's ACL_DISCONNECTED, see docs/security-audit.md).
        // It is a protected broadcast that no app can send, and unlocked() reads the keyguard anyway.
        application.registerReceiver(watching, USER_PRESENT, Context.RECEIVER_EXPORTED)
        receiver = watching
        BridgeLog.event("unlock receiver registered")
        if (!keyguardLocked()) scope.launch { unlocked() }
    }

    private fun release() {
        val registered = receiver ?: return
        receiver = null
        application.unregisterReceiver(registered)
        BridgeLog.event("unlock receiver released")
    }

    private fun keyguardLocked(): Boolean =
        application.getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private companion object {
        val USER_PRESENT = IntentFilter(Intent.ACTION_USER_PRESENT)
    }
}

private const val MILLIS_PER_MINUTE = 60_000L

private fun limitMs(settings: BridgeSettings): Long = settings.clipboardMaxAgeMinutes * MILLIS_PER_MINUTE
