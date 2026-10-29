package app.cue.launcher.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// Whether the paired Mac's screen is locked, as its last lock-state said; null while that is unknown,
// which includes no Mac paired and the Mac away.
fun interface MacLockSource {

    fun observeLocked(): Flow<Boolean?>

    companion object {
        val NONE: MacLockSource = MacLockSource { flowOf(null) }
    }
}
