package app.cue.launcher.system.safe

import android.content.Context
import app.cue.launcher.data.safe.SAFE_LOCK_AFTER_MINUTES
import app.cue.launcher.data.safe.SafeConfigRepository
import app.cue.launcher.data.safe.SafeContainer
import app.cue.launcher.data.safe.SafeSettings
import app.cue.launcher.data.safe.SafeStoredKey
import app.cue.launcher.engine.safe.VaultException
import app.cue.launcher.engine.safe.VaultFailure
import app.cue.launcher.engine.safe.VaultHeader
import app.cue.launcher.engine.safe.VaultKeys
import app.cue.launcher.engine.safe.VaultLayout
import app.cue.launcher.engine.safe.VaultLimitReached
import app.cue.launcher.engine.safe.VaultReader
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Base64

// The phone's own limit on the files and directories of a vault, implied ones included; the format sets
// none. The index keeps each one on the heap — the entry, its path and the map nodes that find it —
// at about 250 bytes for a typical path and up to about 700 for the longest the profile allows, so
// this many take 25 to 70 MB of a heap the system caps at a few hundred.
internal const val SAFE_MAX_ENTRIES = 100_000

enum class SafeFilesOpened { OPENED, LOCKED, NO_FILE_MANAGER }

// The vault on the phone. Until a container is chosen nothing of it runs: the state flow reads the
// vault's own settings file only once something collects it, and the expiry check only looks at the
// session in memory. The password is asked for whenever no usable key is kept — never a dead end.
@Suppress("TooManyFunctions") // one entry point per thing the settings screen and the actions can do
@SingleIn(AppScope::class)
@Inject
class SafeVault(
    private val config: SafeConfigRepository,
    private val keys: SafeKeyGuard,
    private val containers: SafeContainers,
    private val copies: SafeCopies,
    private val host: SafeHost,
    private val clock: SafeClock,
) {

    // Everything but NotConfigured and Unlocked, which follow the settings and the session.
    private val phase = MutableStateFlow<SafeState>(SafeState.Locked)

    // Serializes the unlocks with the changes of the file, so neither a key nor a written_at is stored
    // after the vault was forgotten or another file chosen.
    private val busy = Mutex()

    // Bumped by every lock: an unlock it overtook stops at its next chunk of the copy or its next
    // segment, and never opens a session.
    private val gate = Any()
    private var generation = 0L

    val settings: Flow<SafeSettings> get() = config.settings

    val state: Flow<SafeState> = combine(
        config.settings.map { it.container != null }.distinctUntilChanged(),
        phase,
        host.session,
        ::safeStateOf,
    )

    // The picker has already taken the persistable read grant on the URI.
    suspend fun configure(uri: String, displayName: String) {
        lock()
        busy.withLock {
            config.current().container?.uri?.takeIf { it != uri }?.let(containers::release)
            config.choose(uri, displayName)
            phase.value = SafeState.Locked
        }
    }

    // With a kept key: the file's header first, then one prompt, then the decryption. Without one, or
    // with one that no longer fits the file, it ends in NeedsPassword without a prompt.
    suspend fun unlock(): SafeState = busy.withLock {
        val settings = config.current()
        val container = settings.container
        val stored = settings.key
        when {
            host.session.value != null -> current()
            container == null -> SafeState.NotConfigured
            stored == null -> settle(SafeState.NeedsPassword(reasonWithoutKey()))
            else -> attempt(PasswordReason.KEY_LOST) { started -> openWithKey(container, stored, started) }
        }
    }

    // Sets the vault up on this phone, or recovers it: the key derived from the password is kept under
    // the Keystore key, which takes one prompt, and the same key opens the vault right away.
    suspend fun unlockWithPassword(password: CharSequence): SafeState = busy.withLock {
        val container = config.current().container
        val reason = (phase.value as? SafeState.NeedsPassword)?.reason ?: reasonWithoutKey()
        when {
            host.session.value != null -> current()
            container == null -> SafeState.NotConfigured
            else -> attempt(reason) { started -> openWithPassword(container, password, started) }
        }
    }

    // Back to Locked from a password request or an error the user has seen.
    fun dismiss() {
        phase.update { if (it is SafeState.Unlocking) it else SafeState.Locked }
    }

    fun lock() {
        synchronized(gate) {
            generation++
            host.close()
        }
        dismiss()
    }

    fun lockIfExpired(): Boolean = host.lockIfExpired(clock.elapsedMillis())

    suspend fun forget() {
        lock()
        busy.withLock {
            config.current().container?.uri?.let(containers::release)
            keys.forget()
            config.forget()
            phase.value = SafeState.Locked
        }
    }

    suspend fun setLockAfterMinutes(minutes: Int) {
        config.setLockAfterMinutes(minutes)
        val kept = minutes.coerceIn(SAFE_LOCK_AFTER_MINUTES)
        host.update { it.copy(lockAfterMinutes = kept) }
        lockIfExpired()
    }

    suspend fun setOpenInFiles(enabled: Boolean) = config.setOpenInFiles(enabled)

    // A vault locked in the meantime has nothing to show, which is no missing file manager.
    fun openInFiles(context: Context): SafeFilesOpened = when {
        host.session.value == null -> SafeFilesOpened.LOCKED
        context.openSafeRoot() -> SafeFilesOpened.OPENED
        else -> SafeFilesOpened.NO_FILE_MANAGER
    }

    // For the caller of a successful unlock, from an activity. With the switch off there is nothing to
    // open, which counts as opened.
    suspend fun openInFilesIfEnabled(context: Context): SafeFilesOpened =
        if (config.current().openInFiles) openInFiles(context) else SafeFilesOpened.OPENED

    private fun current(): SafeState = safeStateOf(configured = true, phase.value, host.session.value)

    private fun settle(state: SafeState): SafeState = state.also { phase.value = it }

    private fun reasonWithoutKey() = if (keys.canKeep) PasswordReason.SET_UP else PasswordReason.NO_SCREEN_LOCK

    private suspend fun attempt(reason: PasswordReason, block: suspend (started: Long) -> SafeState): SafeState {
        val started = synchronized(gate) { generation }
        phase.value = SafeState.Unlocking(UnlockStep.READING)
        var outcome: SafeState = SafeState.Locked
        try {
            outcome = withContext(Dispatchers.IO) { block(started) }
        } catch (failure: VaultException) {
            outcome = when (failure.failure) {
                VaultFailure.WRONG_PASSWORD -> SafeState.NeedsPassword(reason, rejected = true)
                VaultFailure.STALE_KEY -> SafeState.NeedsPassword(PasswordReason.PASSWORD_CHANGED)
                else -> SafeState.Failed(safeErrorOf(failure.failure))
            }
        } catch (_: SecurityException) {
            outcome = SafeState.ChooseFileAgain
        } catch (_: IOException) {
            outcome = SafeState.Failed(SafeError.UNREADABLE)
        } catch (_: SafeNoSpace) {
            outcome = SafeState.Failed(SafeError.NO_SPACE)
        } catch (_: VaultLimitReached) {
            outcome = SafeState.Failed(SafeError.TOO_MANY_ENTRIES)
        } catch (_: Overtaken) {
            outcome = SafeState.Locked
        } finally {
            phase.value = if (outcome is SafeState.Unlocked) SafeState.Locked else outcome
        }
        return outcome
    }

    // Only the header is read before the prompt; the rest of the same stream is copied after it. A key
    // of a changed password fails as stale_key before the prompt, which attempt() turns into a request
    // for the password.
    private suspend fun openWithKey(container: SafeContainer, stored: SafeStoredKey, started: Long): SafeState {
        val key = KeptKey.of(stored) ?: return forgetKey(PasswordReason.KEY_LOST)
        return containers.open(container.uri).use { stream ->
            val header = headerOf(stream)
            if (!header.vaultId.contentEquals(key.vaultId)) {
                SafeState.NeedsPassword(PasswordReason.OTHER_VAULT)
            } else {
                VaultKeys.checkStoredKey(header, key.keyId)
                phase.value = SafeState.Unlocking(UnlockStep.AUTHENTICATING)
                when (val opening = keys.open(key.iv, key.sealedCek)) {
                    is KeyOpening.Opened -> open(stream, header, container, opening.cek, started)
                    KeyOpening.Dismissed -> SafeState.Locked
                    KeyOpening.Lost -> forgetKey(PasswordReason.KEY_LOST)
                    KeyOpening.NoScreenLock -> forgetKey(PasswordReason.NO_SCREEN_LOCK)
                }
            }
        }
    }

    // The prompt that keeps the key comes after the password is known to be right, and dismissing it
    // cancels the unlock: without a kept key the next unlock would ask for the password again anyway.
    private suspend fun openWithPassword(container: SafeContainer, password: CharSequence, started: Long): SafeState =
        containers.open(container.uri).use { stream ->
            val header = headerOf(stream)
            phase.value = SafeState.Unlocking(UnlockStep.CHECKING_PASSWORD)
            val cek = withContext(Dispatchers.Default) { VaultKeys.cek(header, password) }
            try {
                if (keep(header, cek)) open(stream, header, container, cek, started) else SafeState.Locked
            } finally {
                cek.fill(0)
            }
        }

    // The spec's order wants the layout checked before the credential whenever the length is known. A
    // pipe's length is known only from the copy, after the key; open() checks it against the copy anyway.
    private fun headerOf(stream: SafeContainerStream): VaultHeader =
        VaultHeader.read(stream.input).also { if (stream.length >= 0) VaultLayout.of(it, stream.length) }

    private suspend fun keep(header: VaultHeader, cek: ByteArray): Boolean {
        phase.value = SafeState.Unlocking(UnlockStep.AUTHENTICATING)
        return when (val sealing = keys.seal(cek)) {
            is KeySealing.Sealed -> {
                config.storeKey(
                    SafeStoredKey(
                        vaultId = header.vaultId.toHexString(),
                        keyId = header.keyId.toHexString(),
                        iv = BASE64.encodeToString(sealing.iv),
                        sealedCek = BASE64.encodeToString(sealing.sealedCek),
                    ),
                )
                true
            }
            KeySealing.Dismissed -> false
            KeySealing.Impossible -> {
                config.clearKey()
                true
            }
        }
    }

    // Copies the rest of the stream into private storage and wipes the CEK once the reader holds its
    // key, whatever happens. The copy becomes a session only once every segment verified and the
    // archive passed, and only if no lock came in the meantime; otherwise closing it frees it.
    private suspend fun open(
        stream: SafeContainerStream,
        header: VaultHeader,
        container: SafeContainer,
        cek: ByteArray,
        started: Long,
    ): SafeState {
        val job = currentCoroutineContext().job
        val content = try {
            if (synchronized(gate) { generation } != started) return SafeState.Locked
            phase.value = SafeState.Unlocking(UnlockStep.READING)
            val copy = copies.copy(header.bytes, stream.input, stream.sizeHint) { ensureCurrent(job, started) }
            var handed = false
            try {
                VaultReader(VaultLayout.of(header, copy.length), copy, cek).also { handed = true }
            } finally {
                if (!handed) copy.close()
            }
        } finally {
            cek.fill(0)
        }
        var kept = false
        try {
            phase.value = SafeState.Unlocking(UnlockStep.DECRYPTING)
            val archive = content.index(SAFE_MAX_ENTRIES) { ensureCurrent(job, started) }
            val session = SafeSession(
                content = content,
                archive = archive,
                displayName = container.displayName,
                writtenAt = header.writtenAt,
                unlockedAt = clock.wallMillis(),
                unlockedElapsed = clock.elapsedMillis(),
                lockAfterMinutes = config.current().lockAfterMinutes,
                documentToken = SafeDocumentIds.newToken(),
            )
            kept = synchronized(gate) {
                (generation == started).also { if (it) host.open(session) }
            }
        } finally {
            if (!kept) content.close()
        }
        if (kept) config.setWrittenAt(header.writtenAt)
        return if (kept) current() else SafeState.Locked
    }

    private fun ensureCurrent(job: Job, started: Long) {
        job.ensureActive()
        if (synchronized(gate) { generation } != started) throw Overtaken()
    }

    private suspend fun forgetKey(reason: PasswordReason): SafeState {
        config.clearKey()
        return SafeState.NeedsPassword(reason)
    }

    private class KeptKey(val vaultId: ByteArray, val keyId: ByteArray, val iv: ByteArray, val sealedCek: ByteArray) {
        companion object {
            // A record that does not decode is as good as no key.
            fun of(stored: SafeStoredKey): KeptKey? = try {
                KeptKey(
                    stored.vaultId.hexToByteArray(),
                    stored.keyId.hexToByteArray(),
                    BASE64_DECODER.decode(stored.iv),
                    BASE64_DECODER.decode(stored.sealedCek),
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    private class Overtaken : Exception("a lock overtook the unlock")

    private companion object {
        val BASE64: Base64.Encoder = Base64.getEncoder()
        val BASE64_DECODER: Base64.Decoder = Base64.getDecoder()
    }
}
