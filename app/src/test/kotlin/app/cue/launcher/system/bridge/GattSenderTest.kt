package app.cue.launcher.system.bridge

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GattSenderTest {

    // Odd seqs take longer to reserve than even ones, so without one lock from the reservation to the
    // write a later seq would overtake an earlier one on the air.
    @Test
    fun `concurrent sends reach the air in seq order`() = runTest {
        var next = 0L
        val written = mutableListOf<Long>()
        val sender = GattSender(
            fits = { true },
            reserve = {
                val seq = ++next
                delay(if (seq % 2 == 1L) SLOW_MS else FAST_MS)
                seq
            },
            seal = { _, seq, _ -> longToBytes(seq) },
            write = { frame ->
                delay(FAST_MS)
                written += bytesToLong(frame)
                GattWrite.SENT
            },
        )
        val signals = List(SENDS) { index -> if (index % 2 == 0) BleSignal.Ping else BleSignal.SyncRequest }
        val jobs = signals.map { signal -> launch { sender.send(signal) } }
        jobs.forEach { it.join() }

        assertEquals((1L..SENDS).toList(), written, "frames must be written in the order of their seqs")
    }

    @Test
    fun `a frame that does not fit spends no seq`() = runTest {
        var reserved = 0
        val sender = GattSender(
            fits = { false },
            reserve = {
                reserved++
                1L
            },
            seal = { _, _, _ -> ByteArray(0) },
            write = { GattWrite.SENT },
        )

        assertNull(sender.send(BleSignal.Ping))
        assertEquals(0, reserved, "no seq may be reserved for a frame the link cannot carry")
    }

    private fun longToBytes(value: Long): ByteArray = ByteArray(Long.SIZE_BYTES) { i ->
        (value ushr (Byte.SIZE_BITS * i)).toByte()
    }

    private fun bytesToLong(bytes: ByteArray): Long = bytes.foldIndexed(0L) { i, acc, byte ->
        acc or ((byte.toLong() and BYTE_MASK) shl (Byte.SIZE_BITS * i))
    }

    private companion object {
        const val SENDS = 12
        const val SLOW_MS = 3L
        const val FAST_MS = 1L
        const val BYTE_MASK = 0xFFL
    }
}
