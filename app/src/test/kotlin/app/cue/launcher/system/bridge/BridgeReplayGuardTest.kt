package app.cue.launcher.system.bridge

import androidx.datastore.core.DataStore
import app.cue.launcher.data.bridge.BridgeConfigRepository
import app.cue.launcher.data.bridge.BridgeKeySealer
import app.cue.launcher.data.bridge.BridgeReplayWindow
import app.cue.launcher.data.bridge.BridgeSealedKey
import app.cue.launcher.data.bridge.BridgeSequenceFile
import app.cue.launcher.data.bridge.BridgeSequenceState
import app.cue.launcher.data.bridge.BridgeSequences
import app.cue.launcher.data.bridge.OpenedPairing
import app.cue.launcher.data.bridge.bridgeKeyTag
import app.cue.launcher.data.settings.MemorySettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BridgeReplayGuardTest {

    // Reads and writes yield in between and nothing serializes them, so every interleaving a race
    // could take is open to the two paths; only the guard's lock keeps the check and record whole.
    private class RacyStore(initial: BridgeSequenceFile) : DataStore<BridgeSequenceFile> {

        private val state = MutableStateFlow(initial)

        override val data: Flow<BridgeSequenceFile> = state.onEach { yield() }

        override suspend fun updateData(
            transform: suspend (t: BridgeSequenceFile) -> BridgeSequenceFile,
        ): BridgeSequenceFile {
            val before = state.value
            yield()
            val after = transform(before)
            yield()
            state.value = after
            return after
        }

        val window: BridgeReplayWindow? get() = state.value.state?.incoming
    }

    private object PlainSealer : BridgeKeySealer {
        override fun seal(plain: ByteArray) = BridgeSealedKey("", "")
        override fun open(sealed: BridgeSealedKey): ByteArray? = null
        override fun destroy() = Unit
    }

    private val key = BridgeFixtures.load("frames.json").hex("kBle")

    private val store = RacyStore(BridgeSequenceFile(BridgeSequenceState(FINGERPRINT, bridgeKeyTag(key))))

    private val guard = BridgeReplayGuard(
        BridgeConfigRepository(MemorySettingsStore(), PlainSealer, BridgeSequences(store)),
    )

    private val pairing = OpenedPairing("Example-Mac", "192.168.1.20", PORT, FINGERPRINT, key.copyOf())

    private fun frame(seq: Long, signal: BleSignal = BleSignal.SyncRequest): ByteArray =
        BleFrameCodec.seal(key, BleDirection.MAC_TO_PHONE, signal.type.code, seq, encodeBleSignal(signal))

    private suspend fun accepted(seq: Long) = guard.open(pairing, frame(seq)) is BleOpened.Accepted

    @Test
    fun `the same frame arriving over GATT and over the session at once is accepted exactly once`() = runTest {
        val frame = frame(0)

        val results = listOf(async { guard.open(pairing, frame) }, async { guard.open(pairing, frame) }).awaitAll()

        assertEquals(1, results.count { it is BleOpened.Accepted })
        assertEquals(1, results.count { it == BleOpened.Rejected(BleRejection.REPLAY) })
        assertEquals(BridgeReplayWindow(0, 1), store.window)
    }

    @Test
    fun `fifty copies racing on real threads are accepted exactly once`() = runTest {
        val frame = frame(SEQ)

        val results = withContext(Dispatchers.Default) {
            List(COPIES) { async { guard.open(pairing, frame) } }.awaitAll()
        }

        assertEquals(1, results.count { it is BleOpened.Accepted })
        assertEquals(SEQ, store.window?.highest)
    }

    // Two carriers can deliver out of order; the window takes the late one and still each seq once.
    @Test
    fun `a frame overtaken on the other path is still accepted, once`() = runTest {
        assertTrue(accepted(SEQ))

        assertTrue(accepted(SEQ - 1))
        assertEquals(BleOpened.Rejected(BleRejection.REPLAY), guard.open(pairing, frame(SEQ - 1)))
        assertEquals(BleOpened.Rejected(BleRejection.REPLAY), guard.open(pairing, frame(SEQ)))
        assertEquals(BleOpened.Rejected(BleRejection.REPLAY), guard.open(pairing, frame(SEQ - 64)))
        assertTrue(accepted(SEQ - 63))
        assertTrue(accepted(SEQ + 1))
        assertEquals(SEQ + 1, store.window?.highest)
    }

    @Test
    fun `a frame that fails the AEAD records nothing`() = runTest {
        val tampered = frame(SEQ).also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }

        assertEquals(BleOpened.Rejected(BleRejection.AUTHENTICATION), guard.open(pairing, tampered))
        assertEquals(BridgeReplayWindow(), store.window)
        assertTrue(accepted(SEQ))
    }

    @Test
    fun `a frame for a pairing that is gone is dropped`() = runTest {
        val other = OpenedPairing("Other", "192.168.1.21", PORT, "00".repeat(32), key.copyOf())

        assertNull(guard.open(other, frame(0)))
        assertEquals(BridgeReplayWindow(), store.window)
    }

    // Lost sequence state: the window of the kept key is unknown, so nothing is taken from the Mac.
    @Test
    fun `a frame for a key without its own sequence state is dropped`() = runTest {
        val sameMacOtherKey = OpenedPairing("Example-Mac", "192.168.1.20", PORT, FINGERPRINT, key.reversedArray())

        assertNull(guard.open(sameMacOtherKey, frame(0)))
        assertEquals(BridgeReplayWindow(), store.window)
    }

    private companion object {
        const val PORT = 52817
        const val SEQ = 141L
        const val COPIES = 50
        const val FINGERPRINT = "f3949472bc16f7c5cb51fbb0e1702e5ed5e40ecf065e6e6ec89d4bfcc82243cb"
    }
}
