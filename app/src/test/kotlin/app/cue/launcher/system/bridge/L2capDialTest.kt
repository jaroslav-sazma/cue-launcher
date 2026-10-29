package app.cue.launcher.system.bridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.nio.channels.Channels
import java.nio.channels.Pipe
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class L2capDialTest {

    private val bleKey = ByteArray(BLE_KEY_BYTES) { it.toByte() }

    @Test
    fun `a channel opened over a route carries the session`() = runBlocking {
        val toMac = Pipe.open()
        val toPhone = Pipe.open()
        val macSide = CompletableFuture.supplyAsync {
            val raw = L2capStreams(Channels.newInputStream(toMac.source()), Channels.newOutputStream(toPhone.sink()))
            val streams = L2capChannel.handshake(L2capRole.MAC, raw, bleKey, X25519.generate(), ReadDeadline {})
            streams.output.write(BridgeFraming.encode(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION)))
            streams.output.flush()
            BridgeFraming.read(streams.input)
        }
        val socket = FakeSocket(Channels.newInputStream(toPhone.source()), Channels.newOutputStream(toMac.sink()))

        val connection = connectL2cap({ socket }, bleKey)
        connection.send(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION))

        assertEquals(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION), (connection.read() as BridgeInbound.Message).message)
        val received = macSide.get(WAIT_SECONDS, TimeUnit.SECONDS) as BridgeInbound.Message
        assertEquals(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION), received.message)
        connection.close()
        assertTrue(socket.closed)
    }

    @Test
    fun `cancelling the dial in the middle of the handshake closes the socket`() = runBlocking {
        val input = BlockingInput()
        val socket = FakeSocket(input, ByteArrayOutputStream())

        val dial = launch(Dispatchers.Default) { connectL2cap({ socket }, bleKey) }
        assertTrue(input.reading.await(WAIT_SECONDS, TimeUnit.SECONDS), "the handshake should be waiting for the Mac")
        withTimeout(WAIT_SECONDS * MILLIS) { dial.cancelAndJoin() }

        assertTrue(socket.closed)
    }

    @Test
    fun `cancelling a connect that hangs closes the socket`() = runBlocking {
        val socket = FakeSocket(BlockingInput(), ByteArrayOutputStream(), connectHangs = true)

        val dial = launch(Dispatchers.Default) { connectL2cap({ socket }, bleKey) }
        assertTrue(socket.connecting.await(WAIT_SECONDS, TimeUnit.SECONDS))
        withTimeout(WAIT_SECONDS * MILLIS) { dial.cancelAndJoin() }

        assertTrue(socket.closed)
    }

    @Test
    fun `a connect that never completes is given up on and the socket closed`() {
        val socket = FakeSocket(BlockingInput(), ByteArrayOutputStream(), connectHangs = true)

        assertThrows<SocketTimeoutException> {
            runBlocking { withTimeout(WAIT_SECONDS * MILLIS) { connectL2cap({ socket }, bleKey, SHORT_TIMEOUT_MS) } }
        }
        assertTrue(socket.closed)
    }

    @Test
    fun `a route that cannot open a socket fails the dial like any carrier`() {
        val failure = assertThrows<IOException> {
            runBlocking { connectL2cap({ throw IOException("no channel") }, bleKey) }
        }
        assertEquals("no channel", failure.message)
    }

    @Test
    fun `a dial that fails is an IOException the carrier fallback takes`() = runBlocking {
        val socket = FakeSocket(BlockingInput(), ByteArrayOutputStream(), connectHangs = true)
        val carriers = BridgeCarriers(lanUp = { true })
        carriers.attach { L2capRoute { socket } }

        val outcome = runCatching {
            carriers.dial(tcp = { null }, l2cap = { route -> connectL2cap(route, bleKey, SHORT_TIMEOUT_MS) })
        }

        assertTrue(outcome.exceptionOrNull() is SocketTimeoutException)
        assertTrue(socket.closed)
    }

    private class FakeSocket(
        override val input: InputStream,
        override val output: OutputStream,
        private val connectHangs: Boolean = false,
    ) : L2capSocket {

        val connecting = CountDownLatch(1)
        private val released = CountDownLatch(1)

        @Volatile
        var closed = false

        override fun connect() {
            connecting.countDown()
            if (connectHangs) {
                released.await()
                throw IOException("the socket is closed")
            }
        }

        override fun close() {
            closed = true
            released.countDown()
            input.close()
        }
    }

    // Blocks like a Bluetooth socket's stream with nothing to read, until the socket is closed.
    private class BlockingInput : InputStream() {

        val reading = CountDownLatch(1)
        private val released = CountDownLatch(1)

        override fun read(): Int {
            reading.countDown()
            released.await()
            throw IOException("the socket is closed")
        }

        override fun close() {
            released.countDown()
        }
    }

    private companion object {
        const val SHORT_TIMEOUT_MS = 100
        const val WAIT_SECONDS = 5L
        const val MILLIS = 1_000L
    }
}
