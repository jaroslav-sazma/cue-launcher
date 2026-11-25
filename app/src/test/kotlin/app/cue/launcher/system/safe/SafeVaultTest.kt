package app.cue.launcher.system.safe

import app.cue.launcher.data.safe.SafeConfigRepository
import app.cue.launcher.data.safe.SafeSettings
import app.cue.launcher.data.safe.SafeStoredKey
import app.cue.launcher.engine.safe.Credential
import app.cue.launcher.engine.safe.VaultFailure
import app.cue.launcher.engine.safe.VaultFixtures
import app.cue.launcher.engine.safe.VaultHeader
import app.cue.launcher.engine.safe.hex
import app.cue.launcher.engine.safe.long
import app.cue.launcher.engine.safe.objects
import app.cue.launcher.engine.safe.openVault
import app.cue.launcher.engine.safe.str
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.util.Base64

class SafeVaultTest {

    private val inputs = VaultFixtures.positive("few-files").getValue("inputs").jsonObject
    private val password = inputs.str("password")
    private val vaultId = inputs.str("vault_id")
    private val keyId = inputs.str("key_id")
    private val cek = inputs.hex("cek")

    @TempDir
    lateinit var storage: File

    private val store = MemoryStore(SafeSettings())
    private val config = SafeConfigRepository(store)
    private val keys = FakeKeys()
    private val containers = FakeContainers(VaultFixtures.bytes("few-files.cuevault"))
    private val host = FakeHost()
    private val clock = FakeClock()

    // Every copy of a file of known length reserves its room first, so the descriptors handed over
    // tell whether each copy was closed again.
    private val copyDescriptors = mutableListOf<FileDescriptor>()
    private var reserve: (FileDescriptor, Long) -> Unit = { descriptor, _ -> copyDescriptors += descriptor }
    private val copies by lazy { SafeCopies(File(storage, "safe")) { descriptor, bytes -> reserve(descriptor, bytes) } }
    private val vault by lazy { SafeVault(config, keys, containers, copies, host, clock) }

    private fun noCopyLeft() {
        assertTrue(File(storage, "safe").listFiles().isNullOrEmpty()) { "a copy keeps its name" }
        assertTrue(copyDescriptors.none { it.valid() }) { "a copy is still open" }
    }

    private suspend fun configured() = vault.configure(URI, "vault.cuevault")

    // The fake key guard "seals" by copying, so the sealed CEK is the CEK itself.
    private fun storedKey(vaultId: String = this.vaultId, keyId: String = this.keyId) =
        SafeStoredKey(vaultId, keyId, iv = "AAAAAAAAAAAAAAAA", sealedCek = Base64.getEncoder().encodeToString(cek))

    @Test
    fun `nothing is set up until a container is chosen`() = runTest {
        assertEquals(SafeState.NotConfigured, vault.state.first())
        assertEquals(SafeState.NotConfigured, vault.unlock())
        assertEquals(0, containers.opens)
    }

    @Test
    fun `without a kept key the unlock asks for the password before reading anything`() = runTest {
        configured()

        assertEquals(SafeState.NeedsPassword(PasswordReason.SET_UP), vault.unlock())
        assertEquals(SafeState.NeedsPassword(PasswordReason.SET_UP), vault.state.first())
        assertEquals(0, containers.opens)
        assertEquals(0, keys.prompts)
    }

    @Test
    fun `without a screen lock the password is the only way in`() = runTest {
        keys.canKeep = false
        configured()

        assertEquals(SafeState.NeedsPassword(PasswordReason.NO_SCREEN_LOCK), vault.unlock())
    }

    @Test
    fun `the password sets the vault up with one prompt and opens it with the same key`() = runTest {
        configured()
        vault.unlock()

        val state = vault.unlockWithPassword(password)

        assertTrue(state is SafeState.Unlocked) { "$state" }
        assertEquals(1, keys.prompts)
        val kept = config.current().key
        assertEquals(vaultId, kept?.vaultId)
        assertEquals(keyId, kept?.keyId)
        assertEquals(inputs.long("written_at"), config.current().container?.writtenAt)
        val session = host.session.value!!
        assertNotNull(session.archive["čtěte mě.txt"])
        assertEquals("vault.cuevault", session.displayName)
        assertTrue(keys.sealed.single().all { it == 0.toByte() }) { "the CEK the password unwrapped is wiped" }
        assertTrue(copyDescriptors.single().valid()) { "the session reads its copy" }
        assertEquals(state, vault.state.first())
    }

    @Test
    fun `a wrong password asks again and keeps nothing`() = runTest {
        configured()
        vault.unlock()

        assertEquals(
            SafeState.NeedsPassword(PasswordReason.SET_UP, rejected = true),
            vault.unlockWithPassword("wrong password"),
        )
        assertNull(config.current().key)
        assertNull(host.session.value)
        assertEquals(0, keys.prompts)
        assertEquals(0, copyDescriptors.size, "nothing is copied before the password is right")
    }

    @Test
    fun `dismissing the prompt that keeps the key cancels the unlock`() = runTest {
        keys.sealing = { KeySealing.Dismissed }
        configured()

        assertEquals(SafeState.Locked, vault.unlockWithPassword(password))
        assertNull(config.current().key)
        assertNull(host.session.value)
        assertEquals(0, copyDescriptors.size)
    }

    @Test
    fun `when no key can be kept the password still opens the vault this once`() = runTest {
        keys.sealing = { KeySealing.Impossible }
        configured()

        assertTrue(vault.unlockWithPassword(password) is SafeState.Unlocked)
        assertNull(config.current().key)
    }

    @Test
    fun `a kept key opens the vault with one prompt`() = runTest {
        configured()
        config.storeKey(storedKey())

        val state = vault.unlock()

        assertTrue(state is SafeState.Unlocked) { "$state" }
        assertEquals(1, keys.prompts)
        assertNotNull(host.session.value?.archive?.get("Smlouvy/nájemní smlouva.pdf"))
    }

    @Test
    fun `a key of another key_id asks for the new password without a prompt`() = runTest {
        configured()
        config.storeKey(storedKey(keyId = "00".repeat(16)))

        assertEquals(SafeState.NeedsPassword(PasswordReason.PASSWORD_CHANGED), vault.unlock())
        assertEquals(0, keys.prompts)
    }

    @Test
    fun `a key of another vault asks for the password of the chosen one`() = runTest {
        configured()
        config.storeKey(storedKey(vaultId = "00".repeat(16)))

        assertEquals(SafeState.NeedsPassword(PasswordReason.OTHER_VAULT), vault.unlock())
        assertEquals(0, keys.prompts)
    }

    @Test
    fun `a lost Keystore key drops the kept one and asks for the password`() = runTest {
        keys.opening = { KeyOpening.Lost }
        configured()
        config.storeKey(storedKey())

        assertEquals(SafeState.NeedsPassword(PasswordReason.KEY_LOST), vault.unlock())
        assertNull(config.current().key)

        assertTrue(vault.unlockWithPassword(password) is SafeState.Unlocked)
        assertNotNull(config.current().key)
    }

    @Test
    fun `a stored record that does not decode counts as a lost key`() = runTest {
        configured()
        config.storeKey(storedKey().copy(sealedCek = "not base64!"))

        assertEquals(SafeState.NeedsPassword(PasswordReason.KEY_LOST), vault.unlock())
    }

    @Test
    fun `a dismissed unlock prompt leaves the vault locked with its key`() = runTest {
        keys.opening = { KeyOpening.Dismissed }
        configured()
        config.storeKey(storedKey())

        assertEquals(SafeState.Locked, vault.unlock())
        assertNotNull(config.current().key)
        assertEquals(0, copyDescriptors.size, "nothing is copied before the prompt is passed")
    }

    @Test
    fun `a lost read grant asks for the file again`() = runTest {
        containers.failure = SecurityException("no grant")
        configured()
        config.storeKey(storedKey())

        assertEquals(SafeState.ChooseFileAgain, vault.unlock())
    }

    @Test
    fun `a file that cannot be read is an error to retry`() = runTest {
        containers.failure = IOException("offline")
        configured()
        config.storeKey(storedKey())

        assertEquals(SafeState.Failed(SafeError.UNREADABLE), vault.unlock())
        vault.dismiss()
        assertEquals(SafeState.Locked, vault.state.first())
    }

    @Test
    fun `a damaged file opens nothing and keeps no plaintext`() = runTest {
        containers.file = VaultFixtures.bytes("segment-byte-flipped.cuevault")
        configured()

        // The negative vectors are mutations of the few-files vault and keep its password.
        assertEquals(SafeState.Failed(SafeError.DAMAGED), vault.unlockWithPassword(password))
        assertNull(host.session.value)
        assertEquals(1, copyDescriptors.size)
        noCopyLeft()
    }

    @Test
    fun `an archive that breaks the profile is closed again before it becomes a session`() = runTest {
        containers.file = VaultFixtures.bytes("archive-symlink.cuevault")
        configured()

        assertEquals(SafeState.Failed(SafeError.DAMAGED), vault.unlockWithPassword(password))
        assertNull(host.session.value)
        assertEquals(1, copyDescriptors.size)
        noCopyLeft()
    }

    @Test
    fun `a file cut short after its header is damaged before anything is copied`() = runTest {
        containers.file = VaultFixtures.bytes("payload-cut-short.cuevault")
        configured()

        // A mutation of the empty vault, which has a password of its own.
        val empty = VaultFixtures.positive("empty").getValue("inputs").jsonObject.str("password")
        assertEquals(SafeState.Failed(SafeError.DAMAGED), vault.unlockWithPassword(empty))
        assertEquals(0, copyDescriptors.size)
        noCopyLeft()
    }

    @Test
    fun `a pipe cut short after its header is damaged, and its copy is dropped`() = runTest {
        containers.file = VaultFixtures.bytes("payload-cut-short.cuevault")
        containers.unknownLength = true
        containers.sizeHint = containers.file.size.toLong()
        configured()

        val empty = VaultFixtures.positive("empty").getValue("inputs").jsonObject.str("password")
        assertEquals(SafeState.Failed(SafeError.DAMAGED), vault.unlockWithPassword(empty))
        assertEquals(1, copyDescriptors.size, "the provider's SIZE column still reserves the room")
        noCopyLeft()
    }

    @Test
    fun `a file of unknown length is copied all the same`() = runTest {
        containers.unknownLength = true
        configured()
        config.storeKey(storedKey())

        assertTrue(vault.unlock() is SafeState.Unlocked)
        assertEquals(containers.file.size.toLong(), containers.consumed)
        assertEquals(0, copyDescriptors.size, "nothing to reserve without a length")
        assertTrue(File(storage, "safe").listFiles().isNullOrEmpty())
    }

    @Test
    fun `a vault the phone has no room for says so and leaves nothing behind`() = runTest {
        reserve = { descriptor, _ ->
            copyDescriptors += descriptor
            throw IOException("ENOSPC")
        }
        configured()
        config.storeKey(storedKey())

        assertEquals(SafeState.Failed(SafeError.NO_SPACE), vault.unlock())
        assertNull(host.session.value)
        noCopyLeft()
    }

    @Test
    fun `a provider that fails during the copy leaves nothing behind`() = runTest {
        containers.wrap = { ProviderInputStream(FailAfter(it, 200, IllegalStateException("provider bug"))) }
        configured()
        config.storeKey(storedKey())

        assertEquals(SafeState.Failed(SafeError.UNREADABLE), vault.unlock())
        assertEquals(1, copyDescriptors.size)
        noCopyLeft()
    }

    // The provider's stream reads straight from the array, so a reader that kept reading from it
    // instead of its copy would meet the zeros.
    @Test
    fun `the session reads its copy, not the file it came from, which may change meanwhile`() = runTest {
        configured()
        config.storeKey(storedKey())
        vault.unlock()
        containers.file.fill(0)
        val entry = host.session.value!!.archive["čtěte mě.txt"]!!
        val read = ByteArray(entry.size.toInt())
        host.session.value!!.content.read(entry.offset, read, 0, read.size)

        val expected = openVault(VaultFixtures.bytes("few-files.cuevault"), Credential.Password(password))
        assertEquals(expected.content(entry).decodeToString(), read.decodeToString())
        assertEquals(1, containers.opens)
    }

    @Test
    fun `forgetting during a long copy stops it at once and frees what was copied`() = runTest {
        val hold = Hold(at = 1024)
        containers.wrap = hold::stream
        configured()
        config.storeKey(storedKey())

        val unlocking = async { vault.unlock() }
        hold.reached.await()
        val forgetting = launch(start = CoroutineStart.UNDISPATCHED) { vault.forget() }
        hold.release()

        assertEquals(SafeState.Locked, unlocking.await())
        forgetting.join()
        assertTrue(containers.consumed < containers.file.size) { "read ${containers.consumed} bytes" }
        assertEquals(0, host.opens)
        assertNull(config.current().container)
        noCopyLeft()
    }

    // The archive breaks the profile, which the index pass reports only after its last segment, so an
    // unlock that ran the pass to its end would say the file is damaged.
    @Test
    fun `a lock that comes as the copy completes stops the index pass before its first segment`() = runTest {
        containers.file = VaultFixtures.bytes("archive-symlink.cuevault")
        val hold = Hold(at = containers.file.size.toLong())
        containers.wrap = hold::stream
        configured()

        val unlocking = async { vault.unlockWithPassword(password) }
        hold.reached.await()
        vault.lock()
        hold.release()

        assertEquals(SafeState.Locked, unlocking.await())
        assertEquals(0, host.opens)
        assertEquals(1, copyDescriptors.size)
        noCopyLeft()
    }

    // A file cut short and opened with a wrong credential as well: with the descriptor's length the
    // layout is checked before the credential, while a pipe's length is known only from the copy.
    @TestFactory
    fun `every negative vector ends in its state, exactly so with a known length`(): List<DynamicTest> =
        VaultFixtures.vectors.objects("negative").flatMap { case ->
            val expected = case.getValue("expected").jsonObject
            val codes = listOfNotNull(expected.str("error"), expected["error_streaming"]?.jsonPrimitive?.content)
            listOf(true, false).map { known ->
                DynamicTest.dynamicTest("${case.str("name")}, length known: $known") {
                    val allowed = (if (known) codes.take(1) else codes).map(::stateOf)
                    val state = unlockVector(case, known)
                    assertTrue(state in allowed) { "$state; want $allowed" }
                }
            }
        }

    @Test
    fun `a pipe's length the provider's SIZE column claims is only a hint for the reservation`() = runTest {
        containers.file = VaultFixtures.bytes("cut-short-wrong-password.cuevault")
        containers.unknownLength = true
        containers.sizeHint = containers.file.size.toLong()
        configured()

        assertEquals(
            SafeState.NeedsPassword(PasswordReason.SET_UP, rejected = true),
            vault.unlockWithPassword("wrong password"),
        )
    }

    @Test
    fun `a vault with more entries than the phone lists has a message of its own`() {
        assertEquals(
            app.cue.launcher.R.string.safe_error_too_many_entries,
            SafeState.Failed(SafeError.TOO_MANY_ENTRIES).message,
        )
    }

    // A fresh vault for each case, as the dynamic tests share this instance.
    private fun unlockVector(case: JsonObject, known: Boolean): SafeState {
        val file = VaultFixtures.bytes(case.getValue("expected").jsonObject.str("file"))
        val containers = FakeContainers(file).apply { unknownLength = !known }
        val config = SafeConfigRepository(MemoryStore(SafeSettings()))
        val copies = SafeCopies(File(storage, "${case.str("name")}-$known")) { _, _ -> }
        val vault = SafeVault(config, FakeKeys(), containers, copies, FakeHost(), FakeClock())
        val credential = case.getValue("credential").jsonObject
        return runBlocking {
            vault.configure(URI, "vault.cuevault")
            if ("password" in credential) {
                vault.unlockWithPassword(credential.str("password"))
            } else {
                val key = credential.getValue("stored_key").jsonObject
                val header = VaultHeader.read(ByteArrayInputStream(file))
                config.storeKey(
                    SafeStoredKey(
                        vaultId = header.vaultId.toHexString(),
                        keyId = key.str("key_id"),
                        iv = "AAAAAAAAAAAAAAAA",
                        sealedCek = Base64.getEncoder().encodeToString(key.hex("cek")),
                    ),
                )
                vault.unlock()
            }
        }
    }

    // What the phone shows for a category: a wrong credential asks for the password, never an error.
    private fun stateOf(code: String): SafeState = when (code) {
        "wrong_password" -> SafeState.NeedsPassword(PasswordReason.SET_UP, rejected = true)
        "stale_key" -> SafeState.NeedsPassword(PasswordReason.PASSWORD_CHANGED)
        else -> SafeState.Failed(safeErrorOf(VaultFailure.entries.single { it.code == code }))
    }

    @Test
    fun `a file that is no vault says so`() = runTest {
        containers.file = VaultFixtures.bytes("magic-wrong.cuevault")
        configured()

        assertEquals(SafeState.Failed(SafeError.NOT_A_VAULT), vault.unlockWithPassword(password))
    }

    @Test
    fun `locking drops the session`() = runTest {
        configured()
        config.storeKey(storedKey())
        vault.unlock()

        vault.lock()

        assertNull(host.session.value)
        assertEquals(SafeState.Locked, vault.state.first())
        assertEquals(1, copyDescriptors.size)
        noCopyLeft()
    }

    @Test
    fun `forgetting locks, releases the file and deletes the key`() = runTest {
        configured()
        config.storeKey(storedKey())
        vault.unlock()

        vault.forget()

        assertNull(host.session.value)
        noCopyLeft()
        assertEquals(listOf(URI), containers.released)
        assertTrue(keys.forgotten)
        assertEquals(SafeState.NotConfigured, vault.state.first())
        assertNull(config.current().key)
    }

    @Test
    fun `choosing another file releases the old one and keeps the key for the vault_id check`() = runTest {
        configured()
        config.storeKey(storedKey())

        vault.configure("content://other", "other.cuevault")

        assertEquals(listOf(URI), containers.released)
        assertNotNull(config.current().key)
        assertEquals("other.cuevault", config.current().container?.displayName)
    }

    @Test
    fun `the derived state follows an open session first, then the settings`() {
        val session = sessionStub()
        val unlocked = SafeState.Unlocked(100, 100 + 15 * 60_000L, 7)

        assertEquals(unlocked, safeStateOf(false, SafeState.Locked, session))
        assertEquals(unlocked, safeStateOf(true, SafeState.Locked, session))
        assertEquals(SafeState.NotConfigured, safeStateOf(false, SafeState.Locked, null))
        assertEquals(SafeState.ChooseFileAgain, safeStateOf(true, SafeState.ChooseFileAgain, null))
    }

    @Test
    fun `a lock closes an open session even when the settings say there is no vault`() = runTest {
        host.open(sessionStub())
        assertTrue(vault.state.first() is SafeState.Unlocked)

        vault.lock()

        assertNull(host.session.value)
        assertEquals(SafeState.NotConfigured, vault.state.first())
    }

    @Test
    fun `forgetting during the unlock prompt leaves no session and no key`() = runTest {
        configured()
        config.storeKey(storedKey())
        val gate = keys.hold()

        val unlocking = async { vault.unlock() }
        keys.prompted.await()
        val forgetting = launch(start = CoroutineStart.UNDISPATCHED) { vault.forget() }
        gate.complete(Unit)

        assertEquals(SafeState.Locked, unlocking.await())
        forgetting.join()
        assertEquals(0, host.opens)
        assertNull(config.current().key)
        assertNull(config.current().container)
        assertEquals(SafeState.NotConfigured, vault.state.first())
        assertTrue(keys.forgotten)
    }

    @Test
    fun `a key kept by an unlock that forgetting overtook does not outlive the forgetting`() = runTest {
        configured()
        val gate = keys.hold()

        val unlocking = async { vault.unlockWithPassword(password) }
        keys.prompted.await()
        val forgetting = launch(start = CoroutineStart.UNDISPATCHED) { vault.forget() }
        gate.complete(Unit)

        assertEquals(SafeState.Locked, unlocking.await())
        forgetting.join()
        assertEquals(0, host.opens)
        assertNull(config.current().key)
        assertTrue(keys.sealed.all { cek -> cek.all { it == 0.toByte() } })
        noCopyLeft()
    }

    @Test
    fun `choosing another file during an unlock opens nothing`() = runTest {
        configured()
        config.storeKey(storedKey())
        val gate = keys.hold()

        val unlocking = async { vault.unlock() }
        keys.prompted.await()
        val choosing = launch(start = CoroutineStart.UNDISPATCHED) {
            vault.configure("content://other", "other.cuevault")
        }
        gate.complete(Unit)

        assertEquals(SafeState.Locked, unlocking.await())
        choosing.join()
        assertEquals(0, host.opens)
        assertEquals("content://other", config.current().container?.uri)
        assertNull(config.current().container?.writtenAt)
        assertEquals(SafeState.Locked, vault.state.first())
    }

    @Test
    fun `a lock during the unlock prompt wins`() = runTest {
        configured()
        config.storeKey(storedKey())
        val gate = keys.hold()

        val unlocking = async { vault.unlock() }
        keys.prompted.await()
        vault.lock()
        gate.complete(Unit)

        assertEquals(SafeState.Locked, unlocking.await())
        assertEquals(0, host.opens)
        assertEquals(SafeState.Locked, vault.state.first())
        assertEquals(0, copyDescriptors.size, "a lock before the copy keeps it from starting")
    }

    @Test
    fun `whatever else a provider throws while the file is read is a file to retry`() = runTest {
        containers.wrap = { ProviderInputStream(FailingStream(IllegalStateException("provider bug"))) }
        configured()
        config.storeKey(storedKey())

        assertEquals(SafeState.Failed(SafeError.UNREADABLE), vault.unlock())
        assertNull(host.session.value)
    }

    @Test
    fun `choosing the same file again keeps what is known about it`() = runTest {
        configured()
        config.storeKey(storedKey())
        vault.unlock()
        val writtenAt = config.current().container?.writtenAt
        assertNotNull(writtenAt)

        vault.configure(URI, "renamed.cuevault")
        assertEquals(writtenAt, config.current().container?.writtenAt)
        assertEquals("renamed.cuevault", config.current().container?.displayName)
        assertEquals(emptyList<String>(), containers.released)

        vault.configure("content://other", "other.cuevault")
        assertNull(config.current().container?.writtenAt)
    }

    @Test
    fun `forgetting resets the vault's settings too`() = runTest {
        configured()
        vault.setLockAfterMinutes(31)
        vault.setOpenInFiles(false)

        vault.forget()

        assertEquals(SafeSettings(), config.current())
    }

    @Test
    fun `a rejected password says so, and a first request says why the password is needed`() {
        assertEquals(
            PasswordReason.KEY_LOST.message,
            SafeState.NeedsPassword(PasswordReason.KEY_LOST).message,
        )
        assertEquals(
            app.cue.launcher.R.string.safe_password_rejected,
            SafeState.NeedsPassword(PasswordReason.KEY_LOST, rejected = true).message,
        )
        assertNull(SafeState.Locked.message)
    }

    private fun sessionStub(): SafeSession {
        val empty = VaultFixtures.positive("empty").getValue("inputs").jsonObject.str("password")
        val opened = openVault(VaultFixtures.bytes("empty.cuevault"), Credential.Password(empty))
        return SafeSession(opened.reader, opened.index, "v", 7, 100, 0, 15, SafeDocumentIds.newToken())
    }

    private companion object {
        const val URI = "content://drive/vault"
    }
}
