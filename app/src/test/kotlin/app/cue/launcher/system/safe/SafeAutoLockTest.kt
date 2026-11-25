package app.cue.launcher.system.safe

import app.cue.launcher.data.safe.SafeConfigRepository
import app.cue.launcher.data.safe.SafeSettings
import app.cue.launcher.data.safe.SafeStoredKey
import app.cue.launcher.engine.safe.VaultFixtures
import app.cue.launcher.engine.safe.hex
import app.cue.launcher.engine.safe.str
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileDescriptor
import java.util.Base64

class SafeAutoLockTest {

    private val inputs = VaultFixtures.positive("few-files").getValue("inputs").jsonObject

    @TempDir
    lateinit var storage: File

    private val config = SafeConfigRepository(MemoryStore(SafeSettings()))
    private val host = FakeHost()
    private val clock = FakeClock()
    private val copyDescriptors = mutableListOf<FileDescriptor>()
    private val vault by lazy {
        val copies = SafeCopies(File(storage, "safe")) { descriptor, _ -> copyDescriptors += descriptor }
        val containers = FakeContainers(VaultFixtures.bytes("few-files.cuevault"))
        SafeVault(config, FakeKeys(), containers, copies, host, clock)
    }

    private suspend fun unlocked() {
        vault.configure("content://drive/vault", "vault.cuevault")
        val cek = Base64.getEncoder().encodeToString(inputs.hex("cek"))
        val key = SafeStoredKey(inputs.str("vault_id"), inputs.str("key_id"), iv = "AAAAAAAAAAAAAAAA", sealedCek = cek)
        config.storeKey(key)
        vault.unlock()
    }

    private fun noCopyLeft() {
        assertTrue(File(storage, "safe").listFiles().isNullOrEmpty()) { "a copy keeps its name" }
        assertTrue(copyDescriptors.none { it.valid() }) { "a copy is still open" }
    }

    @Test
    fun `the vault locks once the set time since the unlock has passed, and only then`() = runTest {
        clock.elapsed = 1_000
        unlocked()

        clock.elapsed = 1_000 + 15 * 60_000L - 1
        assertFalse(vault.lockIfExpired())
        assertNotNull(host.session.value)

        clock.elapsed = 1_000 + 15 * 60_000L
        val content = host.session.value!!.content
        assertTrue(vault.lockIfExpired())
        assertNull(host.session.value)
        assertTrue(content.closed)
        noCopyLeft()
        assertEquals(SafeState.Locked, vault.state.first())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `with the home screen started the vault locks right at its limit, re-armed by a new one`() = runTest {
        clock.source = { testScheduler.currentTime }
        unlocked()
        val limit = 15 * 60_000L
        backgroundScope.launch { host.lockWhenDue(clock::elapsedMillis) }

        advanceTimeBy(limit - 1)
        runCurrent()
        assertNotNull(host.session.value)

        vault.setLockAfterMinutes(20)
        runCurrent()
        advanceTimeBy(1)
        runCurrent()
        assertNotNull(host.session.value) { "the old limit still counted" }

        advanceTimeBy(5 * 60_000L)
        runCurrent()
        assertNull(host.session.value)
        assertEquals(SafeState.Locked, vault.state.first())
        noCopyLeft()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a vault already past its limit locks as soon as the timer starts`() = runTest {
        unlocked()

        backgroundScope.launch { host.lockWhenDue { 15 * 60_000L } }
        runCurrent()

        assertNull(host.session.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a live vault condition turns false when the timer locks the vault`() = runTest {
        clock.source = { testScheduler.currentTime }
        unlocked()
        val seen = mutableListOf<Boolean?>()
        backgroundScope.launch { SafeConditions(host, clock).observe().collect { seen += it.vaultUnlocked } }
        backgroundScope.launch { host.lockWhenDue(clock::elapsedMillis) }
        runCurrent()
        assertEquals(listOf(true), seen)

        advanceTimeBy(15 * 60_000L)
        runCurrent()

        assertNull(host.session.value)
        assertEquals(listOf(true, false), seen)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a vault condition still collected turns false at the limit with no lock behind it`() = runTest {
        clock.source = { testScheduler.currentTime }
        unlocked()
        val seen = mutableListOf<Boolean?>()
        backgroundScope.launch { SafeConditions(host, clock).observe().collect { seen += it.vaultUnlocked } }
        runCurrent()

        advanceTimeBy(15 * 60_000L - 1)
        runCurrent()
        assertEquals(listOf(true), seen)

        advanceTimeBy(1)
        runCurrent()
        assertNotNull(host.session.value) { "the condition must not lock the vault" }
        assertEquals(listOf(true, false), seen)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a vault condition still collected follows a changed limit`() = runTest {
        clock.source = { testScheduler.currentTime }
        unlocked()
        val seen = mutableListOf<Boolean?>()
        backgroundScope.launch { SafeConditions(host, clock).observe().collect { seen += it.vaultUnlocked } }
        runCurrent()

        host.update { it.copy(lockAfterMinutes = 20) }
        advanceTimeBy(15 * 60_000L)
        runCurrent()
        assertEquals(listOf(true), seen) { "the old limit still counted" }

        host.update { it.copy(lockAfterMinutes = 16) }
        runCurrent()
        assertEquals(listOf(true), seen)
        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(listOf(true, false), seen)
    }

    @Test
    fun `the vault condition reads unlocked from the unlock to the limit, and locked past it`() = runTest {
        val conditions = SafeConditions(host, clock)
        assertEquals(false, conditions.observe().first().vaultUnlocked)

        clock.elapsed = 1_000
        unlocked()
        assertEquals(true, conditions.observe().first().vaultUnlocked)

        clock.elapsed = 1_000 + 15 * 60_000L
        assertNotNull(host.session.value)
        assertEquals(false, conditions.observe().first().vaultUnlocked) { "an expired session reads as locked" }

        vault.lock()
        assertEquals(false, conditions.observe().first().vaultUnlocked)
    }

    @Test
    fun `every unlock names its documents anew, and a changed limit keeps the names`() = runTest {
        unlocked()
        val first = host.session.value!!.documentToken
        vault.setLockAfterMinutes(30)
        assertEquals(first, host.session.value!!.documentToken)

        vault.lock()
        vault.unlock()
        val second = host.session.value!!.documentToken
        assertTrue(first != second) { "a document id outlived the lock" }
    }

    @Test
    fun `a descriptor reads only until its session's limit, then asks once for the lock`() = runTest {
        clock.elapsed = 1_000
        unlocked()
        var asked = 0
        val gate = SafeReadGate(host.session.value!!.documentToken, { host.session.value }, clock::elapsedMillis) {
            asked++
        }
        assertTrue(gate.open())

        clock.elapsed = 1_000 + 15 * 60_000L - 1
        assertTrue(gate.open())

        clock.elapsed = 1_000 + 15 * 60_000L
        assertFalse(gate.open())
        assertFalse(gate.open())
        assertEquals(1, asked)
        assertNotNull(host.session.value) { "the gate itself must not lock on the reading thread" }
    }

    @Test
    fun `a descriptor follows a changed limit of its session`() = runTest {
        clock.elapsed = 1_000
        unlocked()
        val gate = SafeReadGate(host.session.value!!.documentToken, { host.session.value }, clock::elapsedMillis) {}

        vault.setLockAfterMinutes(30)
        clock.elapsed = 1_000 + 20 * 60_000L
        assertTrue(gate.open())

        host.update { it.copy(lockAfterMinutes = 10) }
        assertFalse(gate.open())
    }

    @Test
    fun `a descriptor of a locked session reads nothing, not even in the next unlock`() = runTest {
        unlocked()
        var asked = 0
        val gate = SafeReadGate(host.session.value!!.documentToken, { host.session.value }, clock::elapsedMillis) {
            asked++
        }

        vault.lock()
        assertFalse(gate.open())

        vault.unlock()
        assertNotNull(host.session.value)
        assertFalse(gate.open())
        assertEquals(0, asked)
    }

    @Test
    fun `a shorter limit applies to the open session at once`() = runTest {
        unlocked()
        clock.elapsed += 5 * 60_000L

        vault.setLockAfterMinutes(10)
        assertNotNull(host.session.value)

        vault.setLockAfterMinutes(3)
        assertNull(host.session.value)
        assertEquals(3, config.current().lockAfterMinutes)
    }

    @Test
    fun `the limit stays between 1 and 60 minutes`() = runTest {
        vault.setLockAfterMinutes(0)
        assertEquals(1, config.current().lockAfterMinutes)
        vault.setLockAfterMinutes(600)
        assertEquals(60, config.current().lockAfterMinutes)
    }
}
