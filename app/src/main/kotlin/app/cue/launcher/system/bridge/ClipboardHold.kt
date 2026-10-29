package app.cue.launcher.system.bridge

internal class HeldClip(val text: String, val sensitive: Boolean, val observedAt: Long)

internal enum class ClipArrival { APPLY, HOLD, STALE }

internal enum class OwedOutcome { NONE, SUMMON, STALE }

internal class UnlockStep(val clip: HeldClip?, val staleClip: Boolean, val owed: OwedOutcome)

// The newest word from the Mac while the user cannot receive it: a clip, or the owed flag of one
// too large for a BLE frame. Each replaces the other, as the Mac's clipboard holds only the newest
// copy. A clip's age counts from when the Mac saw the copy (ageMs) plus the time it waited here.
// Nothing expires on a timer: the age is judged at unlock.
internal class ClipboardHold(private val clock: () -> Long) {

    private var clip: HeldClip? = null

    private var owedSince: Long? = null

    val pending: Boolean get() = clip != null || owedSince != null

    fun arrived(text: String, sensitive: Boolean, ageMs: Long?, locked: Boolean, limitMs: Long): ClipArrival {
        clear()
        val now = clock()
        val observedAt = now - (ageMs ?: 0L).coerceAtLeast(0L)
        return when {
            now - observedAt > limitMs -> ClipArrival.STALE
            locked -> {
                clip = HeldClip(text, sensitive, observedAt)
                ClipArrival.HOLD
            }
            else -> ClipArrival.APPLY
        }
    }

    // True when the visit should be summoned now.
    fun owed(locked: Boolean): Boolean {
        clear()
        if (locked) owedSince = clock()
        return !locked
    }

    fun unlocked(limitMs: Long): UnlockStep {
        val now = clock()
        val held = clip
        val fresh = held?.takeIf { now - it.observedAt <= limitMs }
        val owed = when (val since = owedSince) {
            null -> OwedOutcome.NONE
            else -> if (now - since <= limitMs) OwedOutcome.SUMMON else OwedOutcome.STALE
        }
        clear()
        return UnlockStep(fresh, staleClip = held != null && fresh == null, owed = owed)
    }

    fun clear() {
        clip = null
        owedSince = null
    }
}
