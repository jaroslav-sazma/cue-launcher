package app.cue.launcher.system.bridge

internal enum class PresenceInput {
    CDM_APPEARED,
    CDM_DISAPPEARED,
    GATT_CONNECTED,
    GATT_AUTHENTICATED,
    GATT_DISCONNECTED,
    BLUETOOTH_OFF,
    BLUETOOTH_ON,
    ATTENDANCE_FAILED,
}

internal sealed interface DebounceOrder {

    data object Keep : DebounceOrder

    data class Arm(val token: Long) : DebounceOrder

    data object Cancel : DebounceOrder
}

internal data class PresenceStep(val present: Boolean, val debounce: DebounceOrder = DebounceOrder.Keep)

// The system's appeared/disappeared pair is a hint, not a state: next to the Mac it reports a false
// absence of about a second every eleven or so. A disappearance therefore only arms one debounce,
// and only its expiry makes the Mac absent; any sign of the Mac cancels it. A GATT connection is such
// a sign and no more, as anyone can answer under the Mac's address; once a frame from the Mac has
// opened over it, the link is the truth until it drops, and the system's disappearance is ignored. A
// frame counts only over a connection that is up: one queued from before the drop proves nothing.
// The phone's radio turning off ends the link with no word from the stack and arms the debounce, as
// nothing can confirm the Mac then. Turning it on makes the Mac present on trial, for a fresh
// debounce that only a sign of the Mac cancels — not what is left of the off's, which may run out
// before a connection could — unless the system's last word was a disappearance: the system need
// not report again a Mac it never lost across the restart. An attendance that failed makes the Mac
// absent at once, and only a new appearance or the radio coming on brings it back. Expiries carry
// the token of the debounce they belong to, so one that was cancelled after it fired changes nothing.
internal class PresenceMachine {

    var present = false
        private set

    var linked = false
        private set

    private var connected = false

    private var cdmNearby = false

    private var armed: Long? = null

    private var issued = 0L

    fun on(input: PresenceInput): PresenceStep = when (input) {
        PresenceInput.CDM_APPEARED -> {
            cdmNearby = true
            seen()
        }
        PresenceInput.GATT_CONNECTED -> {
            connected = true
            seen()
        }
        PresenceInput.GATT_AUTHENTICATED -> if (connected) {
            linked = true
            seen()
        } else {
            PresenceStep(present)
        }
        PresenceInput.CDM_DISAPPEARED -> {
            cdmNearby = false
            if (linked) PresenceStep(present) else lost()
        }
        PresenceInput.GATT_DISCONNECTED, PresenceInput.BLUETOOTH_OFF -> {
            connected = false
            linked = false
            lost()
        }
        PresenceInput.BLUETOOTH_ON -> {
            connected = false
            linked = false
            if (present || cdmNearby) {
                present = true
                armed = null
                lost()
            } else {
                PresenceStep(present)
            }
        }
        PresenceInput.ATTENDANCE_FAILED -> {
            present = false
            linked = false
            connected = false
            armed = null
            PresenceStep(present = false, DebounceOrder.Cancel)
        }
    }

    // The window closes the link with the absence, without a disconnect of its own.
    fun expired(token: Long): PresenceStep {
        if (armed != token) return PresenceStep(present)
        armed = null
        if (!linked) {
            present = false
            connected = false
        }
        return PresenceStep(present)
    }

    private fun seen(): PresenceStep {
        present = true
        val order = if (armed != null) DebounceOrder.Cancel else DebounceOrder.Keep
        armed = null
        return PresenceStep(present, order)
    }

    private fun lost(): PresenceStep {
        if (!present || armed != null) return PresenceStep(present)
        val token = ++issued
        armed = token
        return PresenceStep(present, DebounceOrder.Arm(token))
    }
}

internal const val PRESENCE_DEBOUNCE_MS = 60_000L
