package app.cue.launcher.system.safe

import android.os.SystemClock
import app.cue.launcher.engine.safe.VaultArchive
import app.cue.launcher.engine.safe.VaultReader

// content reads the session's private copy with the CEK. Once the session is open it is used only on
// the provider's reading thread, and closed there as well, so no read meets a closed copy halfway.
data class SafeSession(
    val content: VaultReader,
    val archive: VaultArchive,
    val displayName: String,
    // The header's written_at, in Unix seconds.
    val writtenAt: Long,
    val unlockedAt: Long,
    val unlockedElapsed: Long,
    val lockAfterMinutes: Int,
    // Random and new at every unlock, never derived from the vault: see SafeDocumentIds.
    val documentToken: String,
) {
    val locksAt: Long get() = unlockedAt + lockAfterMinutes * MINUTE_MS

    fun expired(nowElapsed: Long): Boolean = remainingMillis(nowElapsed) == 0L

    fun remainingMillis(nowElapsed: Long): Long =
        (lockAfterMinutes * MINUTE_MS - (nowElapsed - unlockedElapsed)).coerceAtLeast(0L)
}

private const val MINUTE_MS = 60_000L

// Asked before every read of a descriptor, on the reading thread, so it only reads memory and the
// clock: a descriptor serves only while the session it was opened in is open and within its limit,
// whatever limit that session has by now. Past the limit the read fails and lockExpired, which must
// not block, is asked once to lock.
internal class SafeReadGate(
    private val documentToken: String,
    private val session: () -> SafeSession?,
    private val nowElapsed: () -> Long,
    private val lockExpired: () -> Unit,
) {
    private var lockAsked = false

    fun open(): Boolean {
        val current = session()?.takeIf { it.documentToken == documentToken }
        val expired = current?.expired(nowElapsed()) == true
        if (expired && !lockAsked) {
            lockAsked = true
            lockExpired()
        }
        return current != null && !expired
    }
}

interface SafeClock {

    fun wallMillis(): Long

    // Counts deep sleep too, and a change of the clock or the zone does not move it.
    fun elapsedMillis(): Long

    companion object {
        val SYSTEM: SafeClock = object : SafeClock {
            override fun wallMillis() = System.currentTimeMillis()
            override fun elapsedMillis() = SystemClock.elapsedRealtime()
        }
    }
}
