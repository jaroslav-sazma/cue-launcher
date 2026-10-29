package app.cue.launcher.data.bridge

import androidx.datastore.core.DataStore
import app.cue.launcher.data.settings.LauncherSettings
import app.cue.launcher.data.settings.LauncherSettingsSerializer
import app.cue.launcher.data.settings.MemorySettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Base64

class BridgeConfigRepositoryTest {

    // Reversible but not the identity, so a test that compares the plain key with what was stored
    // would notice the key going to disk unsealed.
    private class FakeSealer : BridgeKeySealer {
        var failing = false
        var destroyed = 0

        override fun seal(plain: ByteArray): BridgeSealedKey? = if (failing) {
            null
        } else {
            BridgeSealedKey(iv = "iv", data = Base64.getEncoder().encodeToString(plain.reversedArray()))
        }

        override fun open(sealed: BridgeSealedKey): ByteArray? =
            if (failing) null else Base64.getDecoder().decode(sealed.data).reversedArray()

        override fun destroy() {
            destroyed++
        }
    }

    private val store = MemorySettingsStore()
    private val sealer = FakeSealer()
    private val disk = MemorySequenceStore()
    private val sequences = BridgeSequences(disk)
    private val repository = BridgeConfigRepository(store, sealer, sequences)
    private val key = ByteArray(32) { it.toByte() }
    private val keyTag = bridgeKeyTag(key)

    private fun commit(
        name: String = "Example-Mac",
        fingerprint: String = FINGERPRINT,
        dev: Boolean = false,
        enable: Boolean = true,
        association: BridgeAssociation? = null,
    ) = runBlocking {
        if (enable) repository.enable()
        repository.commitPairing(
            PairingCommit(name, "192.168.1.20", PORT, fingerprint, key, association),
            devEndpoint = dev,
        )
    }

    private fun stored(): BridgeSettings = runBlocking { repository.settings() }

    @Test
    fun `the bridge is off and stores nothing by default`() {
        assertEquals(BridgeSettings(), stored())
        assertFalse(stored().enabled)
        assertNull(stored().pairing)
        assertNull(stored().devEndpoint)
    }

    @Test
    fun `a committed pairing keeps K_ble only sealed and opens it again`() = runBlocking {
        assertEquals(CommitOutcome.SAVED, commit())

        val record = requireNotNull(stored().pairing)
        assertFalse(Base64.getDecoder().decode(record.bleKey.data).contentEquals(key))
        assertEquals(BridgeSequenceState(FINGERPRINT, keyTag), disk.stored)

        val opened = requireNotNull(repository.openPairing())
        assertArrayEquals(key, opened.bleKey)
        assertEquals(FINGERPRINT, opened.macFingerprint)
        assertEquals("Example-Mac", opened.macName)
        assertEquals(keyTag, opened.keyTag)
    }

    @Test
    fun `a moved Mac keeps its pairing and sequences at the new address`() = runBlocking {
        commit()
        val pairing = requireNotNull(repository.openPairing())
        assertEquals(0L, repository.reserveOutgoingSeq(pairing))
        assertTrue(repository.recordIncomingSeq(pairing, 7))

        assertTrue(repository.moveEndpoint(FINGERPRINT, "192.168.1.42", PORT + 1))

        val record = requireNotNull(stored().pairing)
        assertEquals("192.168.1.42", record.host)
        assertEquals(PORT + 1, record.port)
        val moved = requireNotNull(repository.openPairing())
        assertArrayEquals(key, moved.bleKey)
        assertEquals(1L, repository.reserveOutgoingSeq(moved))
        assertEquals(7L, repository.incomingWindow(moved)?.highest)
    }

    // No counter can be trusted for a key whose state is gone, so nothing may be sealed under it again.
    @Test
    fun `a pairing without the sequence state of its own key does not open`() = runBlocking {
        commit()

        sequences.start(FINGERPRINT, bridgeKeyTag(key.reversedArray()))
        assertNull(repository.openPairing())

        sequences.wipe()
        assertNull(repository.openPairing())
        assertEquals(FINGERPRINT, stored().pairing?.macFingerprint)
    }

    // The fresh state goes first: a commit that never reaches the settings leaves the old key with
    // state that is not its own, never with counters back at zero.
    @Test
    fun `a commit cut short leaves the previous pairing unusable`() = runBlocking {
        val settings = BreakableStore(store)
        val breakable = BridgeConfigRepository(settings, sealer, sequences)
        commit()
        val previous = requireNotNull(breakable.openPairing())
        repeat(3) { breakable.reserveOutgoingSeq(previous) }

        settings.broken = true
        assertThrows(IOException::class.java) {
            runBlocking {
                breakable.commitPairing(
                    PairingCommit("Example-Mac", "192.168.1.20", PORT, FINGERPRINT, key.reversedArray(), null),
                    devEndpoint = false,
                )
            }
        }

        assertArrayEquals(key, Base64.getDecoder().decode(requireNotNull(stored().pairing).bleKey.data).reversedArray())
        assertNull(breakable.openPairing())
        assertNull(breakable.reserveOutgoingSeq(previous))
        assertNull(breakable.incomingWindow(previous))
    }

    @Test
    fun `an address is moved only for the pinned Mac and only when it changed`() = runBlocking {
        commit()

        assertFalse(repository.moveEndpoint("00".repeat(32), "192.168.1.42", PORT))
        assertFalse(repository.moveEndpoint(FINGERPRINT, "192.168.1.20", PORT))
        assertEquals("192.168.1.20", requireNotNull(stored().pairing).host)
    }

    @Test
    fun `nothing is moved without a pairing`() = runBlocking {
        repository.enable()

        assertFalse(repository.moveEndpoint(FINGERPRINT, "192.168.1.42", PORT))
        assertNull(stored().pairing)
    }

    @Test
    fun `a pairing that cannot be sealed is not stored`() {
        sealer.failing = true

        assertEquals(CommitOutcome.NOT_SEALED, commit())
        assertNull(stored().pairing)
    }

    // A ceremony can outlive the switch: turning the bridge off mid-pairing must leave nothing behind.
    @Test
    fun `no pairing is stored while the bridge is off`() {
        assertEquals(CommitOutcome.BRIDGE_OFF, commit(enable = false))

        assertEquals(BridgeSettings(), stored())
        assertEquals(1, sealer.destroyed)
        assertNull(disk.stored)
    }

    @Test
    fun `debug builds keep the endpoint they paired over`() {
        commit(dev = true)
        assertEquals(BridgeDevEndpoint("192.168.1.20", PORT), stored().devEndpoint)

        runBlocking { repository.setDevEndpoint(" ", 4_000) }
        assertEquals(BridgeDevEndpoint(BRIDGE_DEV_HOST, 4_000), stored().devEndpoint)
    }

    @Test
    fun `the endpoint cannot be set without a pairing`() = runBlocking {
        repository.setDevEndpoint(BRIDGE_DEV_HOST, PORT)

        assertNull(stored().devEndpoint)
    }

    // Frames go to disk in their own file: the settings every screen collects stay untouched.
    @Test
    fun `sending and receiving frames never rewrite the settings`() = runBlocking {
        commit()
        val pairing = requireNotNull(repository.openPairing())
        val before = stored()

        repeat(3) { assertEquals(it.toLong(), repository.reserveOutgoingSeq(pairing)) }
        assertTrue(repository.recordIncomingSeq(pairing, 5))
        assertFalse(repository.recordIncomingSeq(pairing, 5))

        assertTrue(before === stored())
    }

    @Test
    fun `the association is stored with the pairing and goes with it`() = runBlocking {
        val association = BridgeAssociation(id = 4, address = "C0:C7:DB:A1:3B:CB", addressType = 1)
        commit(association = association)

        assertEquals(association, stored().pairing?.association)
        assertNull(stored().devEndpoint)

        repository.unpair()
        assertNull(stored().pairing)
    }

    @Test
    fun `re-pairing starts both sequences again`() = runBlocking {
        commit()
        val first = requireNotNull(repository.openPairing())
        repository.reserveOutgoingSeq(first)
        repository.recordIncomingSeq(first, 7)

        commit(name = "Other-Mac", fingerprint = "ab".repeat(32))

        val record = requireNotNull(stored().pairing)
        assertEquals("Other-Mac", record.macName)
        assertEquals(BridgeSequenceState("ab".repeat(32), keyTag), disk.stored)
        val second = requireNotNull(repository.openPairing())
        assertEquals(0L, repository.reserveOutgoingSeq(second))
        assertNull(repository.reserveOutgoingSeq(first))
    }

    @Test
    fun `turning the bridge off wipes the pairing and the key`() = runBlocking {
        repository.enable()
        commit(dev = true)

        repository.disable()

        assertEquals(BridgeSettings(), stored())
        assertEquals(1, sealer.destroyed)
        assertNull(repository.openPairing())
        assertNull(disk.stored)
    }

    @Test
    fun `unpairing keeps the switch on and wipes the rest`() = runBlocking {
        repository.enable()
        commit(dev = true)

        repository.unpair()

        assertEquals(BridgeSettings(enabled = true), stored())
        assertEquals(1, sealer.destroyed)
        assertNull(disk.stored)
    }

    @Test
    fun `the clipboard choices start at their defaults and outlive an unpair, but not turning off`() = runBlocking {
        assertTrue(stored().clipboardFromMac)
        assertEquals(CLIPBOARD_MAX_AGE_DEFAULT_MINUTES, stored().clipboardMaxAgeMinutes)
        commit()

        repository.setClipboardFromMac(false)
        repository.setClipboardMaxAge(12)
        repository.unpair()
        assertFalse(stored().clipboardFromMac)
        assertEquals(12, stored().clipboardMaxAgeMinutes)
        assertTrue(stored().enabled)

        repository.disable()
        assertEquals(BridgeSettings(), stored())
        assertTrue(stored().clipboardFromMac)
        assertEquals(CLIPBOARD_MAX_AGE_DEFAULT_MINUTES, stored().clipboardMaxAgeMinutes)
    }

    @Test
    fun `the apps kept off the Mac outlive an unpair, but not turning off`() = runBlocking {
        assertTrue(stored().mirrorExcluded.isEmpty())
        commit()

        repository.setMirrorExcluded("com.example.bank", excluded = true)
        repository.setMirrorExcluded("com.example.chat", excluded = true)
        repository.setMirrorExcluded("com.example.chat", excluded = false)
        assertEquals(setOf("com.example.bank"), stored().mirrorExcluded)
        repository.unpair()
        assertEquals(setOf("com.example.bank"), stored().mirrorExcluded)

        repository.disable()
        assertTrue(stored().mirrorExcluded.isEmpty())
    }

    @Test
    fun `a stored pairing that does not open is reported unusable`() = runBlocking {
        assertFalse(repository.pairingUnusable(), "no pairing is not an unusable one")
        commit()
        assertFalse(repository.pairingUnusable())

        sequences.wipe()
        assertTrue(repository.pairingUnusable(), "the sequence state is gone")

        commit()
        sealer.failing = true
        assertTrue(repository.pairingUnusable(), "the key cannot be opened")

        sealer.failing = false
        repository.unpair()
        assertFalse(repository.pairingUnusable())
    }

    @Test
    fun `the clipboard age limit is clamped to its range`() = runBlocking {
        repository.setClipboardMaxAge(0)
        assertEquals(CLIPBOARD_MAX_AGE_MINUTES.first, stored().clipboardMaxAgeMinutes)

        repository.setClipboardMaxAge(61)
        assertEquals(CLIPBOARD_MAX_AGE_MINUTES.last, stored().clipboardMaxAgeMinutes)

        repository.setClipboardMaxAge(60)
        assertEquals(60, stored().clipboardMaxAgeMinutes)
    }

    @Test
    fun `the settings file survives a roundtrip with a pairing in it`() = runBlocking {
        repository.enable()
        commit(dev = true)
        val whole = LauncherSettings(bridge = repository.settings())

        val bytes = ByteArrayOutputStream().also { LauncherSettingsSerializer.writeTo(whole, it) }.toByteArray()
        val read = LauncherSettingsSerializer.readFrom(ByteArrayInputStream(bytes))

        assertEquals(whole.bridge, read.bridge)
    }

    @Test
    fun `settings written before the bridge existed read as off`() = runBlocking {
        val read = LauncherSettingsSerializer.readFrom(ByteArrayInputStream("""{"dockSeeded":true}""".toByteArray()))

        assertEquals(BridgeSettings(), read.bridge)
    }

    private class BreakableStore(private val inner: DataStore<LauncherSettings>) : DataStore<LauncherSettings> {
        var broken = false

        override val data: Flow<LauncherSettings> = inner.data

        override suspend fun updateData(
            transform: suspend (t: LauncherSettings) -> LauncherSettings,
        ): LauncherSettings = if (broken) throw IOException("disk full") else inner.updateData(transform)
    }

    private companion object {
        const val FINGERPRINT = "f3949472bc16f7c5cb51fbb0e1702e5ed5e40ecf065e6e6ec89d4bfcc82243cb"
        const val PORT = 52_817
    }
}
