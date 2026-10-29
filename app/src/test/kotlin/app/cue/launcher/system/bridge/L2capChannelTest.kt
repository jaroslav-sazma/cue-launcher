package app.cue.launcher.system.bridge

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream
import java.net.SocketTimeoutException
import java.nio.channels.Channels
import java.nio.channels.Pipe
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class L2capChannelTest {

    private val bleKey = ByteArray(BLE_KEY_BYTES) { it.toByte() }
    private val phone = X25519.generate()
    private val mac = X25519.generate()

    @Test
    fun `a Mac that never sends its key is given up on and the channel closed`() {
        val silent = SilentInput()

        assertTimeoutPreemptively(Duration.ofSeconds(WAIT_SECONDS)) {
            assertThrows(SocketTimeoutException::class.java) {
                L2capChannel.connect(silent, raw(silent), bleKey, SHORT_TIMEOUT_MS, phone)
            }
        }
        assertTrue(silent.closed)
    }

    @Test
    fun `a Mac that sends its key but no record is given up on at the first read`() {
        val silent = SilentInput()
        val input = SequenceInputStream(ByteArrayInputStream(mac.publicKey), silent)
        val connection = L2capChannel.connect(silent, raw(input), bleKey, SHORT_TIMEOUT_MS, phone)

        assertTimeoutPreemptively(Duration.ofSeconds(WAIT_SECONDS)) {
            assertThrows(SocketTimeoutException::class.java) { connection.read() }
        }
        assertTrue(silent.closed)
    }

    @Test
    fun `a stream that ends inside the handshake fails and closes the channel`() {
        val socket = Socket()

        val truncated = ByteArrayInputStream(mac.publicKey.copyOf(X25519_KEY_BYTES - 1))

        assertThrows(BridgeProtocolException::class.java) {
            L2capChannel.connect(socket, raw(truncated), bleKey, ephemeral = phone)
        }
        assertTrue(socket.closed)
    }

    @Test
    fun `a record longer than the limit is fatal`() {
        val length = L2capRecordCodec.MAX_CIPHERTEXT_BYTES + 1
        val oversized = byteArrayOf((length ushr Byte.SIZE_BITS).toByte(), length.toByte()) + ByteArray(length)

        val failure = assertThrows(L2capRecordException::class.java) { connectTo(macRecords(), oversized).read() }

        assertEquals(L2capRejection.MALFORMED, failure.reason)
    }

    @Test
    fun `a stream that ends inside a record is fatal, one that ends between records is not`() {
        val hello = macRecords(BridgeFraming.encode(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION)))

        val clean = connectTo(hello)
        assertEquals(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION), (clean.read() as BridgeInbound.Message).message)
        assertNull(clean.read())

        val failure = assertThrows(L2capRecordException::class.java) { connectTo(hello.copyOf(hello.size - 1)).read() }
        assertEquals(L2capRejection.MALFORMED, failure.reason)
    }

    @Test
    fun `a Mac with another pairing's key cannot produce a record`() {
        val otherPairing = ByteArray(BLE_KEY_BYTES) { (it + 1).toByte() }
        val hello = BridgeFraming.encode(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION))
        val forged = macRecords(hello, bleKey = otherPairing)

        val failure = assertThrows(L2capRecordException::class.java) { connectTo(forged).read() }

        assertEquals(L2capRejection.AUTHENTICATION, failure.reason)
    }

    @Test
    fun `a record holds one to 16384 bytes of plaintext`() {
        val key = ByteArray(ChaCha20Poly1305.KEY_BYTES)

        assertThrows(IllegalArgumentException::class.java) { L2capRecordCodec.seal(key, 0, ByteArray(0)) }
        assertThrows(IllegalArgumentException::class.java) {
            L2capRecordCodec.seal(key, 0, ByteArray(L2capRecordCodec.MAX_PLAINTEXT_BYTES + 1))
        }
    }

    @Test
    fun `a write larger than a record goes out as full records and the rest`() {
        val out = ByteArrayOutputStream()
        val streams = L2capChannel.handshake(
            L2capRole.PHONE, L2capStreams(ByteArrayInputStream(mac.publicKey), out), bleKey, phone, ReadDeadline {},
        )
        val payload = ByteArray(2 * L2capRecordCodec.MAX_PLAINTEXT_BYTES + REST_BYTES) { (it * 7).toByte() }

        streams.output.write(payload)
        streams.output.flush()

        val written = out.toByteArray()
        val sent = ByteArrayInputStream(written.copyOfRange(X25519_KEY_BYTES, written.size))
        val key = keys().phoneToMac
        val records = generateSequence(0L) { it + 1 }
            .map { counter -> L2capRecordCodec.read(sent, key, counter) }
            .takeWhile { it != null }
            .filterNotNull()
            .toList()
        assertEquals(
            listOf(L2capRecordCodec.MAX_PLAINTEXT_BYTES, L2capRecordCodec.MAX_PLAINTEXT_BYTES, REST_BYTES),
            records.map { it.size },
        )
        assertArrayEquals(payload, records.reduce { all, next -> all + next })
    }

    @Test
    fun `both sides hold a session over the channel`() {
        val toMac = Pipe.open()
        val toPhone = Pipe.open()
        val icon = ByteArray(ICON_BYTES) { (it % 251).toByte() }
        val iconHash = sha256(icon).toHexString()

        val macSide = CompletableFuture.supplyAsync {
            val raw = L2capStreams(Channels.newInputStream(toMac.source()), Channels.newOutputStream(toPhone.sink()))
            val streams = L2capChannel.handshake(L2capRole.MAC, raw, bleKey, mac, ReadDeadline {})
            streams.output.write(BridgeFraming.encode(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION)))
            streams.output.flush()
            generateSequence { BridgeFraming.read(streams.input) }.toList()
        }
        val raw = L2capStreams(Channels.newInputStream(toPhone.source()), Channels.newOutputStream(toMac.sink()))
        val connection = L2capChannel.connect(Socket(), raw, bleKey, ephemeral = phone)
        connection.send(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION))
        assertEquals(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION), (connection.read() as BridgeInbound.Message).message)
        connection.send(BridgeMessage.IconData(iconHash, icon.size), icon)
        connection.send(BridgeMessage.Bye())
        toMac.sink().close()

        val received = macSide.get(WAIT_SECONDS, TimeUnit.SECONDS).map { it as BridgeInbound.Message }
        val expected = listOf(
            BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION),
            BridgeMessage.IconData(iconHash, icon.size),
            BridgeMessage.Bye(),
        )
        assertEquals(expected, received.map { it.message })
        assertArrayEquals(icon, received[1].binary)
    }

    private fun keys(otherBleKey: ByteArray = bleKey): L2capKeys =
        L2capDerivation.keys(X25519.agree(phone.privateKey, mac.publicKey), otherBleKey, phone.publicKey, mac.publicKey)

    private fun macRecords(vararg plaintexts: ByteArray, bleKey: ByteArray = this.bleKey): ByteArray {
        val key = keys(bleKey).macToPhone
        return plaintexts.foldIndexed(mac.publicKey) { counter, stream, plaintext ->
            stream + L2capRecordCodec.seal(key, counter.toLong(), plaintext)
        }
    }

    private fun connectTo(vararg parts: ByteArray): BridgeConnection {
        val input = ByteArrayInputStream(parts.reduce { all, next -> all + next })
        return L2capChannel.connect(Socket(), raw(input), bleKey, ephemeral = phone)
    }

    private fun raw(input: InputStream): L2capStreams = L2capStreams(input, ByteArrayOutputStream())

    private class Socket : Closeable {

        var closed = false

        override fun close() {
            closed = true
        }
    }

    // Blocks like a Bluetooth socket's stream with nothing to read, until the socket is closed.
    private class SilentInput : InputStream() {

        private val released = CountDownLatch(1)

        @Volatile
        var closed = false

        override fun read(): Int {
            released.await()
            throw IOException("the socket is closed")
        }

        override fun close() {
            closed = true
            released.countDown()
        }
    }

    private companion object {
        const val SHORT_TIMEOUT_MS = 100
        const val WAIT_SECONDS = 5L
        const val REST_BYTES = 7_232
        const val ICON_BYTES = 50_000
    }
}
