package app.cue.launcher.system.bridge

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class GattSent(val seq: Long, val written: GattWrite)

// The Mac drops a seq more than 64 below the highest it has accepted, so frames reach the air in the
// order their seqs were reserved: one lock holds from the reservation to the write's response. The
// seq is spent only for a frame that fits the link as it is now; null means nothing was reserved.
internal class GattSender(
    private val fits: (Int) -> Boolean,
    private val reserve: suspend () -> Long?,
    private val seal: (BleSignal, Long, ByteArray) -> ByteArray,
    private val write: suspend (ByteArray) -> GattWrite,
) {

    private val order = Mutex()

    suspend fun send(signal: BleSignal): GattSent? = order.withLock {
        val plaintext = encodeBleSignal(signal)
        val size = BleFrameCodec.HEADER_BYTES + plaintext.size + ChaCha20Poly1305.TAG_BYTES
        val seq = if (fits(size)) reserve() else null
        seq?.let { GattSent(it, write(seal(signal, it, plaintext))) }
    }
}
