package app.cue.launcher.data.bridge

import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BridgeSequencesTest {

    private val disk = MemorySequenceStore()
    private val sequences = BridgeSequences(disk)

    private fun reserve(on: BridgeSequences = sequences, tag: String = TAG) = runBlocking {
        on.reserveOutgoing(FINGERPRINT, tag)
    }

    @Test
    fun `a block is on disk before its first seq is handed out and costs one write`() = runBlocking {
        sequences.start(FINGERPRINT, TAG)
        val before = disk.writes

        val handedOut = List(BRIDGE_SEQ_BLOCK.toInt()) {
            requireNotNull(reserve()).also { seq -> assertTrue(seq < requireNotNull(disk.stored).outgoingBound) }
        }

        assertEquals((0L until BRIDGE_SEQ_BLOCK).toList(), handedOut)
        assertEquals(1, disk.writes - before)
        assertEquals(BRIDGE_SEQ_BLOCK, disk.stored?.outgoingBound)
        assertEquals(BRIDGE_SEQ_BLOCK, reserve())
        assertEquals(2 * BRIDGE_SEQ_BLOCK, disk.stored?.outgoingBound)
        assertEquals(2, disk.writes - before)
    }

    // The rest of the block is lost with the process; the next one starts above it, never inside it.
    @Test
    fun `a restart continues above the persisted bound`() = runBlocking {
        sequences.start(FINGERPRINT, TAG)
        repeat(3) { reserve() }

        val restarted = BridgeSequences(disk)

        assertEquals(BRIDGE_SEQ_BLOCK, reserve(on = restarted))
        assertEquals(BRIDGE_SEQ_BLOCK + 1, reserve(on = restarted))
        assertEquals(2 * BRIDGE_SEQ_BLOCK, disk.stored?.outgoingBound)
    }

    @Test
    fun `no seq is handed out past the last usable one`() = runBlocking {
        val nearTheEnd = BridgeSequenceState(FINGERPRINT, TAG, outgoingBound = BRIDGE_MAX_SEQ - 1)
        disk.updateData { BridgeSequenceFile(nearTheEnd) }

        assertEquals(BRIDGE_MAX_SEQ - 1, reserve())
        assertEquals(BRIDGE_MAX_SEQ, reserve())
        assertNull(reserve())
        assertEquals(BRIDGE_MAX_SEQ + 1, disk.stored?.outgoingBound)
        assertNull(reserve(on = BridgeSequences(disk)))
    }

    @Test
    fun `nothing is handed out without state of this very key`() = runBlocking {
        assertNull(reserve())

        sequences.start(FINGERPRINT, TAG)

        assertNull(reserve(tag = OTHER_TAG))
        assertNull(sequences.reserveOutgoing("00".repeat(32), TAG))
        assertFalse(sequences.matches(FINGERPRINT, OTHER_TAG))
        assertTrue(sequences.matches(FINGERPRINT, TAG))
    }

    // A block held in memory belongs to state that a new pairing or a wipe has replaced.
    @Test
    fun `a new pairing or a wipe ends the block in memory`() = runBlocking {
        sequences.start(FINGERPRINT, TAG)
        assertEquals(0L, reserve())

        sequences.start(FINGERPRINT, OTHER_TAG)
        assertNull(reserve())
        assertEquals(0L, reserve(tag = OTHER_TAG))

        sequences.wipe()
        assertNull(reserve(tag = OTHER_TAG))
        assertNull(disk.stored)
    }

    @Test
    fun `the incoming window is persisted and lets each seq in once`() = runBlocking {
        sequences.start(FINGERPRINT, TAG)

        assertTrue(sequences.recordIncoming(FINGERPRINT, TAG, 5))
        assertTrue(sequences.recordIncoming(FINGERPRINT, TAG, 3))
        assertFalse(sequences.recordIncoming(FINGERPRINT, TAG, 3))
        assertFalse(sequences.recordIncoming(FINGERPRINT, OTHER_TAG, 9))

        val window = requireNotNull(BridgeSequences(disk).incomingWindow(FINGERPRINT, TAG))
        assertEquals(5L, window.highest)
        assertFalse(window.permits(5))
        assertFalse(window.permits(3))
        assertTrue(window.permits(4))
        assertNull(sequences.incomingWindow(FINGERPRINT, OTHER_TAG))
    }

    @Test
    fun `the window refuses what fell below it and forgets what a jump left behind`() {
        val window = BridgeReplayWindow().record(0).record(1).record(100)

        assertFalse(window.permits(36))
        assertTrue(window.permits(37))
        assertFalse(window.permits(100))
        assertTrue(window.permits(1_000))
        assertEquals(BridgeReplayWindow(1_000, 1), window.record(1_000))
        assertFalse(window.record(1_000).permits(100))
    }

    @Test
    fun `the key tag tells keys apart and never contains the key`() {
        val key = ByteArray(32) { it.toByte() }

        assertEquals(bridgeKeyTag(key), bridgeKeyTag(key.copyOf()))
        assertNotEquals(bridgeKeyTag(key), bridgeKeyTag(key.reversedArray()))
        assertEquals(32, bridgeKeyTag(key).length)
        assertFalse(key.toHexString().contains(bridgeKeyTag(key)))
    }

    @Test
    fun `the file survives a roundtrip and an unreadable one is corruption`() = runBlocking {
        val state = BridgeSequenceFile(BridgeSequenceState(FINGERPRINT, TAG, 128, BridgeReplayWindow(70, 5)))
        val bytes = ByteArrayOutputStream().also { BridgeSequenceSerializer.writeTo(state, it) }.toByteArray()

        assertEquals(state, BridgeSequenceSerializer.readFrom(ByteArrayInputStream(bytes)))
        assertThrows(CorruptionException::class.java) {
            runBlocking { BridgeSequenceSerializer.readFrom(ByteArrayInputStream(bytes.copyOf(bytes.size / 2))) }
        }
        val incomplete = """{"state":{"keyTag":"x"}}""".toByteArray()
        assertThrows(CorruptionException::class.java) {
            runBlocking { BridgeSequenceSerializer.readFrom(ByteArrayInputStream(incomplete)) }
        }
    }

    private companion object {
        const val FINGERPRINT = "f3949472bc16f7c5cb51fbb0e1702e5ed5e40ecf065e6e6ec89d4bfcc82243cb"
        const val TAG = "00112233445566778899aabbccddeeff"
        const val OTHER_TAG = "ffeeddccbbaa99887766554433221100"
    }
}
