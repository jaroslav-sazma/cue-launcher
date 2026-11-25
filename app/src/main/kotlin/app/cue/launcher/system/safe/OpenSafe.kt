package app.cue.launcher.system.safe

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import app.cue.launcher.engine.safe.VaultReader
import app.cue.launcher.system.setGatedComponentEnabled
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest

interface SafeHost {

    val session: StateFlow<SafeSession?>

    fun open(session: SafeSession)

    fun close()

    fun update(transform: (SafeSession) -> SafeSession)

    // Only reads what is in memory, so with the vault locked or never set up it costs nothing.
    fun lockIfExpired(nowElapsed: Long): Boolean {
        val expired = session.value?.expired(nowElapsed) == true
        if (expired) close()
        return expired
    }

    // The vault's one timer, for ForegroundSession.run() alone: with the home screen started an
    // unlock locks right at its limit, re-armed whenever the session changes. Everywhere else the
    // expiry stays a lazy check. The elapsed clock counts deep sleep and delay() does not, so the
    // delay never ends before the limit.
    suspend fun lockWhenDue(nowElapsed: () -> Long) {
        session.collectLatest { open ->
            if (open != null) {
                delay(open.remainingMillis(nowElapsed()))
                lockIfExpired(nowElapsed())
            }
        }
    }
}

fun vaultUnlocked(session: SafeSession?, nowElapsed: Long): Boolean = session?.expired(nowElapsed) == false

@Inject
class ProcessSafeHost(private val application: Application) : SafeHost {

    override val session: StateFlow<SafeSession?> = OpenSafe.session

    override fun open(session: SafeSession) = OpenSafe.open(application, session)

    override fun close() = OpenSafe.close(application)

    override fun update(transform: (SafeSession) -> SafeSession) = OpenSafe.update(transform)
}

internal class LiveSafe(val session: SafeSession, val reads: SafeReads)

// The thread that serves one session's descriptors. A descriptor another app still holds after the
// lock keeps it alive, idle, so each of its reads gets EIO from the closed content rather than
// whatever the system answers for a dead thread; the last descriptor's release ends it.
internal class SafeReads(private val content: VaultReader) {

    private val thread = HandlerThread(READS_THREAD).apply { start() }

    val handler = Handler(thread.looper)

    private var descriptors = 0

    private var closed = false

    // False once the session is closed: no new descriptor may count on this thread.
    fun acquire(): Boolean = synchronized(this) {
        if (!closed) descriptors++
        !closed
    }

    fun release() {
        val last = synchronized(this) {
            descriptors--
            closed && descriptors == 0
        }
        if (last) thread.quitSafely()
    }

    // The content is closed on this thread, after every read queued before it; that wipes its cached
    // segments and closes the private copy, which frees it.
    fun close() {
        handler.post { content.close() }
        val idle = synchronized(this) {
            closed = true
            descriptors == 0
        }
        if (idle) thread.quitSafely()
    }

    private companion object {
        const val READS_THREAD = "CueSafeReads"
    }
}

fun safeAuthority(context: Context): String = "${context.packageName}.vault"

// ContentProvider.onCreate runs before Application.onCreate, and the provider's calls can arrive
// before the graph exists, so the open session lives here, outside the graph. A process death takes
// it along, which is a lock as well.
internal object OpenSafe {

    private class Open(val session: SafeSession, val reads: SafeReads)

    private var opened: Open? = null

    private val sessions = MutableStateFlow<SafeSession?>(null)

    val session: StateFlow<SafeSession?> = sessions.asStateFlow()

    private val main by lazy { Handler(Looper.getMainLooper()) }

    fun open(context: Context, session: SafeSession) {
        val reads = SafeReads(session.content)
        val previous = synchronized(this) {
            opened.also {
                opened = Open(session, reads)
                sessions.value = session
            }
        }
        previous?.reads?.close()
        setProviderEnabled(context, true)
    }

    // With reads, only the session they belong to: a read that failed late must not lock a newer one.
    fun close(context: Context, reads: SafeReads? = null) {
        val closing = synchronized(this) {
            opened?.takeIf { reads == null || it.reads === reads }?.also {
                opened = null
                sessions.value = null
            }
        } ?: return
        setProviderEnabled(context, false)
        closing.reads.close()
    }

    fun update(transform: (SafeSession) -> SafeSession) = synchronized(this) {
        opened = opened?.let { Open(transform(it.session), it.reads) }
        sessions.value = opened?.session
    }

    // What the provider may serve: an expired session is locked on the spot and serves nothing.
    fun live(context: Context, nowElapsed: Long): LiveSafe? {
        val current = synchronized(this) { opened }
        val expired = current?.session?.expired(nowElapsed) == true
        if (expired) close(context)
        return current?.takeUnless { expired }?.let { LiveSafe(it.session, it.reads) }
    }

    // Both for the reading thread, which must not wait for the package manager the lock talks to: the
    // lock runs on the main thread, after the read that asked for it has already failed.
    fun lockIfExpiredLater(context: Context) {
        main.post { live(context, SystemClock.elapsedRealtime()) }
    }

    fun closeLater(context: Context, reads: SafeReads) {
        main.post { close(context, reads) }
    }

    // For a provider the system created in a process without a session: one that died unlocked left
    // the component enabled.
    fun disableIfLocked(context: Context) {
        if (synchronized(this) { opened } == null) setProviderEnabled(context, false)
    }

    // The roots for the document UI's list of places, the whole authority for a folder still on screen.
    private fun setProviderEnabled(context: Context, enabled: Boolean) {
        val authority = safeAuthority(context)
        val everything = Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority).build()
        if (!enabled) revokeGrants(context, everything)
        context.setGatedComponentEnabled(SafeDocumentsProvider::class.java, enabled)
        context.contentResolver.notifyChange(DocumentsContract.buildRootsUri(authority), null)
        context.contentResolver.notifyChange(everything, null)
    }

    // Every app's grant below the authority, persisted ones included: the provider's own app may revoke
    // grants it never issued, but the system finds the provider only while it is enabled, so this has
    // to come first. The ids those grants name die with the session anyway.
    private fun revokeGrants(context: Context, everything: Uri) {
        try {
            context.revokeUriPermission(
                everything,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (refused: SecurityException) {
            Log.w(TAG, "Could not revoke the vault's grants (${refused::class.java.simpleName})")
        }
    }

    private const val TAG = "CueSafe"
}
