package app.cue.launcher.system.bridge

import java.io.Closeable
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.util.Objects
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal enum class L2capRole { PHONE, MAC }

internal class L2capKeys(val phoneToMac: ByteArray, val macToPhone: ByteArray)

internal enum class L2capRejection { MALFORMED, AUTHENTICATION }

internal class L2capRecordException(val reason: L2capRejection, message: String, cause: Throwable? = null) :
    BridgeProtocolException(message, cause)

internal class L2capStreams(val input: InputStream, val output: OutputStream)

// K_ble in the salt is the authentication: whoever swaps the ephemeral keys in flight still cannot
// seal or open a record without the pairing.
internal object L2capDerivation {

    private const val KEY_BYTES = ChaCha20Poly1305.KEY_BYTES
    private const val OKM_BYTES = 2 * KEY_BYTES

    fun salt(bleKey: ByteArray, phonePublicKey: ByteArray, macPublicKey: ByteArray): ByteArray =
        sha256(lengthPrefixed(bleKey, phonePublicKey, macPublicKey))

    fun okm(sharedSecret: ByteArray, salt: ByteArray): ByteArray =
        Hkdf.sha256(sharedSecret, salt, L2CAP_KEYS_INFO.encodeToByteArray(), OKM_BYTES)

    fun keys(
        sharedSecret: ByteArray,
        bleKey: ByteArray,
        phonePublicKey: ByteArray,
        macPublicKey: ByteArray,
    ): L2capKeys {
        val okm = okm(sharedSecret, salt(bleKey, phonePublicKey, macPublicKey))
        return L2capKeys(okm.copyOf(KEY_BYTES), okm.copyOfRange(KEY_BYTES, OKM_BYTES)).also { okm.fill(0) }
    }
}

// The keys are fresh per connection, so a counter that starts at 0 never repeats under a key and,
// unlike a BLE seq, is never persisted.
internal object L2capRecordCodec {

    const val LENGTH_BYTES = 2
    const val MAX_PLAINTEXT_BYTES = 16_384
    const val MIN_CIPHERTEXT_BYTES = 1 + ChaCha20Poly1305.TAG_BYTES
    const val MAX_CIPHERTEXT_BYTES = MAX_PLAINTEXT_BYTES + ChaCha20Poly1305.TAG_BYTES

    private val NO_AAD = ByteArray(0)

    fun nonce(counter: Long): ByteArray {
        require(counter >= 0) { "counter $counter is out of range" }
        return ByteBuffer.allocate(ChaCha20Poly1305.NONCE_BYTES).putInt(0).putLong(counter).array()
    }

    fun seal(key: ByteArray, counter: Long, plaintext: ByteArray): ByteArray {
        require(plaintext.size in 1..MAX_PLAINTEXT_BYTES) { "a ${plaintext.size}-byte plaintext does not fit a record" }
        val ciphertext = ChaCha20Poly1305.seal(key, nonce(counter), NO_AAD, plaintext)
        return ByteBuffer.allocate(LENGTH_BYTES + ciphertext.size)
            .putShort(ciphertext.size.toShort()).put(ciphertext).array()
    }

    // Returns null only at a clean end of stream between records; everything else that is not a whole
    // record that opens throws, and the caller closes the channel.
    fun read(input: InputStream, key: ByteArray, counter: Long): ByteArray? {
        val ciphertext = ByteArray(readLength(input) ?: return null)
        try {
            DataInputStream(input).readFully(ciphertext)
        } catch (e: EOFException) {
            throw L2capRecordException(L2capRejection.MALFORMED, "the stream ended inside a record", e)
        }
        return try {
            ChaCha20Poly1305.open(key, nonce(counter), NO_AAD, ciphertext)
        } catch (e: GeneralSecurityException) {
            throw L2capRecordException(L2capRejection.AUTHENTICATION, "the record does not open", e)
        }
    }

    private fun readLength(input: InputStream): Int? {
        val first = input.read()
        if (first < 0) return null
        val second = input.read()
        val length = (first shl Byte.SIZE_BITS) or second
        if (second < 0 || length !in MIN_CIPHERTEXT_BYTES..MAX_CIPHERTEXT_BYTES) {
            val problem = if (second < 0) "the stream ended inside a record" else "length $length is out of range"
            throw L2capRecordException(L2capRejection.MALFORMED, problem)
        }
        return length
    }
}

// A Bluetooth socket has no read timeout, so an expired wait closes the channel to end the blocked
// read. Unlike a timed-out TCP socket, the connection is gone afterwards.
internal class ReadDeadline(private val onExpiry: () -> Unit) {

    @Volatile
    var millis: Int = 0

    fun <T> guard(block: () -> T): T {
        val timeout = millis
        if (timeout <= 0) return block()
        val expired = AtomicBoolean(false)
        val expiry = scheduler.schedule(
            {
                expired.set(true)
                onExpiry()
            },
            timeout.toLong(),
            TimeUnit.MILLISECONDS,
        )
        try {
            return block()
        } catch (failure: IOException) {
            if (!expired.get()) throw failure
            throw SocketTimeoutException("nothing arrived within $timeout ms").apply { initCause(failure) }
        } finally {
            expiry.cancel(false)
        }
    }

    private companion object {
        // The thread lives only while a wait is armed, so an idle channel keeps nothing running.
        val scheduler = ScheduledThreadPoolExecutor(1) { task ->
            Thread(task, "cue-l2cap-deadline").apply { isDaemon = true }
        }.apply {
            setKeepAliveTime(1, TimeUnit.SECONDS)
            allowCoreThreadTimeOut(true)
            removeOnCancelPolicy = true
        }
    }
}

internal object L2capChannel {

    const val TIMEOUT_MS = 15_000

    // Blocking, like the TLS connect: a caller that gives up closes the socket, which ends the wait. The
    // connection comes back with the read timeout still armed for the peer's hello, as a TLS one does.
    fun connect(
        socket: Closeable,
        raw: L2capStreams,
        bleKey: ByteArray,
        timeoutMillis: Int = TIMEOUT_MS,
        ephemeral: X25519KeyPair = X25519.generate(),
    ): BridgeConnection {
        val carrier = L2capCarrier(socket)
        carrier.setReadTimeout(timeoutMillis)
        val activity = ReadActivity()
        val watched = L2capStreams(ActivityInputStream(raw.input, activity), raw.output)
        var connection: BridgeConnection? = null
        try {
            val streams = handshake(L2capRole.PHONE, watched, bleKey, ephemeral, carrier.deadline)
            connection = BridgeConnection(streams.input, streams.output, carrier, activity)
            return connection
        } finally {
            if (connection == null) runCatching { carrier.close() }
        }
    }

    fun handshake(
        role: L2capRole,
        raw: L2capStreams,
        bleKey: ByteArray,
        ephemeral: X25519KeyPair,
        deadline: ReadDeadline,
    ): L2capStreams {
        if (role == L2capRole.PHONE) writeKey(raw.output, ephemeral.publicKey)
        val peerKey = deadline.guard { readKey(raw.input) }
        if (role == L2capRole.MAC) writeKey(raw.output, ephemeral.publicKey)
        val shared = try {
            X25519.agree(ephemeral.privateKey, peerKey)
        } catch (e: GeneralSecurityException) {
            throw BridgeProtocolException("the peer's ephemeral key yields no usable secret", e)
        }
        val keys = try {
            when (role) {
                L2capRole.PHONE -> L2capDerivation.keys(shared, bleKey, ephemeral.publicKey, peerKey)
                L2capRole.MAC -> L2capDerivation.keys(shared, bleKey, peerKey, ephemeral.publicKey)
            }
        } finally {
            shared.fill(0)
        }
        val (receiveKey, sendKey) = when (role) {
            L2capRole.PHONE -> keys.macToPhone to keys.phoneToMac
            L2capRole.MAC -> keys.phoneToMac to keys.macToPhone
        }
        return L2capStreams(RecordInput(raw.input, receiveKey, deadline), RecordOutput(raw.output, sendKey))
    }

    private fun writeKey(output: OutputStream, key: ByteArray) {
        output.write(key)
        output.flush()
    }

    private fun readKey(input: InputStream): ByteArray {
        val key = ByteArray(X25519_KEY_BYTES)
        try {
            DataInputStream(input).readFully(key)
        } catch (e: EOFException) {
            throw BridgeProtocolException("the stream ended inside the handshake", e)
        }
        return key
    }
}

private class L2capCarrier(private val socket: Closeable) : BridgeCarrier {

    val deadline = ReadDeadline { runCatching { socket.close() } }

    override fun setReadTimeout(millis: Int) {
        deadline.millis = millis
    }

    override fun close() {
        socket.close()
    }
}

// Record boundaries carry no meaning: a read may return part of a record's plaintext or the end of one
// record's and nothing more, and the framing above reads fully across them.
private class RecordInput(
    private val source: InputStream,
    private val key: ByteArray,
    private val deadline: ReadDeadline,
) : InputStream() {

    private var counter = 0L
    private var plaintext = ByteArray(0)
    private var position = 0
    private var ended = false

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toUByte().toInt()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        Objects.checkFromIndexSize(off, len, b.size)
        if (len > 0 && position == plaintext.size && !next()) return -1
        val count = minOf(len, plaintext.size - position)
        plaintext.copyInto(b, off, position, position + count)
        position += count
        return count
    }

    override fun available(): Int = plaintext.size - position

    override fun close() {
        source.close()
    }

    private fun next(): Boolean {
        val opened = if (ended) null else deadline.guard { L2capRecordCodec.read(source, key, counter) }
        if (opened == null) {
            ended = true
            return false
        }
        counter++
        plaintext = opened
        position = 0
        return true
    }
}

// Bytes wait here until a record is full or the writer flushes, so one flushed frame of up to 16 KiB
// travels as one record.
private class RecordOutput(private val sink: OutputStream, private val key: ByteArray) : OutputStream() {

    private val buffer = ByteArray(L2capRecordCodec.MAX_PLAINTEXT_BYTES)
    private var size = 0
    private var counter = 0L

    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        Objects.checkFromIndexSize(off, len, b.size)
        var from = off
        val end = off + len
        while (from < end) {
            val take = minOf(end - from, buffer.size - size)
            b.copyInto(buffer, size, from, from + take)
            size += take
            from += take
            if (size == buffer.size) emit()
        }
    }

    override fun flush() {
        if (size > 0) emit()
        sink.flush()
    }

    override fun close() {
        try {
            flush()
        } finally {
            sink.close()
        }
    }

    private fun emit() {
        sink.write(L2capRecordCodec.seal(key, counter, buffer.copyOf(size)))
        counter++
        size = 0
    }
}
