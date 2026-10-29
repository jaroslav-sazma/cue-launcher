package app.cue.launcher.system.bridge

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.Closeable
import java.io.IOException

class SendToMacCarriersTest {

    private val carriers = BridgeCarriers(lanUp = { true })
    private val route = L2capRoute { error("the test pushes through a fake") }
    private val tried = mutableListOf<Carrier>()

    private suspend fun send(
        tcpAllowed: Boolean = true,
        endpoint: Boolean = true,
        tcpCarries: Boolean = true,
        l2capCarries: Boolean = true,
    ): SendToMacOutcome = sendOverCarriers(
        carriers = carriers,
        tcpAllowed = tcpAllowed,
        tcp = {
            if (endpoint) {
                tried += Carrier.TCP
                if (!tcpCarries) throw IOException("no answer over tcp")
            } else {
                null
            }
        },
        l2cap = {
            tried += Carrier.L2CAP
            if (!l2capCarries) throw IOException("no answer over l2cap")
        },
    )

    @Test
    fun `the clip goes over TCP when it answers`() = runBlocking {
        carriers.attach { route }

        assertEquals(SendToMacOutcome.SENT, send())
        assertEquals(listOf(Carrier.TCP), tried)
    }

    @Test
    fun `Bluetooth carries the clip when TCP fails`() = runBlocking {
        carriers.attach { route }

        assertEquals(SendToMacOutcome.SENT, send(tcpCarries = false))
        assertEquals(listOf(Carrier.TCP, Carrier.L2CAP), tried)
    }

    @Test
    fun `Bluetooth carries the clip when no endpoint is known`() = runBlocking {
        carriers.attach { route }

        assertEquals(SendToMacOutcome.SENT, send(endpoint = false))
        assertEquals(listOf(Carrier.L2CAP), tried)
    }

    @Test
    fun `the Mac is unreachable only when neither carrier takes the clip`() = runBlocking {
        carriers.attach { route }

        assertEquals(SendToMacOutcome.UNREACHABLE, send(tcpCarries = false, l2capCarries = false))
        assertEquals(listOf(Carrier.TCP, Carrier.L2CAP), tried)
    }

    @Test
    fun `without a window a failed TCP push is unreachable`() = runBlocking {
        assertEquals(SendToMacOutcome.UNREACHABLE, send(tcpCarries = false))
        assertEquals(SendToMacOutcome.UNREACHABLE, send(endpoint = false))
        assertEquals(listOf(Carrier.TCP), tried)
    }

    @Test
    fun `without the local network permission Bluetooth still carries the clip`() = runBlocking {
        carriers.attach { route }

        assertEquals(SendToMacOutcome.SENT, send(tcpAllowed = false))
        assertEquals(listOf(Carrier.L2CAP), tried)
    }

    @Test
    fun `the missing permission is named when Bluetooth cannot carry the clip either`() = runBlocking {
        assertEquals(SendToMacOutcome.NO_LOCAL_NETWORK, send(tcpAllowed = false))
        carriers.attach { route }
        assertEquals(SendToMacOutcome.NO_LOCAL_NETWORK, send(tcpAllowed = false, l2capCarries = false))
        assertEquals(listOf(Carrier.L2CAP), tried)
    }

    @Test
    fun `the Bluetooth transfer budget grows with the text and stops at its cap`() {
        assertEquals(5_000L, l2capTransferMillis(0))
        assertEquals(6_000L, l2capTransferMillis(1))
        assertEquals(6_000L, l2capTransferMillis(4_096))
        assertEquals(7_000L, l2capTransferMillis(4_097))
        assertEquals(30_000L, l2capTransferMillis(CLIPBOARD_MAX_BYTES))
        assertEquals(35_000L, l2capTransferMillis(10 * CLIPBOARD_MAX_BYTES))
    }

    private class FakeConnection : Closeable {
        var closed = false

        override fun close() {
            closed = true
        }
    }

    @Test
    fun `a long transfer outlasts the connect cap within its own budget`() = runTest {
        val connection = FakeConnection()
        val budget = l2capTransferMillis(CLIPBOARD_MAX_BYTES)

        val sent = connectThenTransfer(budget, { connection }) {
            delay(budget - 1)
            SendToMacOutcome.SENT
        }

        assertEquals(SendToMacOutcome.SENT, sent)
        assertTrue(connection.closed)
    }

    @Test
    fun `the connect and handshake get the one-shot cap only`() = runTest {
        val connection = FakeConnection()
        var transferred = false

        val sent = connectThenTransfer(
            transferMillis = 30_000,
            connect = {
                delay(ONE_SHOT_TIMEOUT_MS + 1)
                connection
            },
        ) { transferred = true }

        assertNull(sent)
        assertFalse(transferred)
    }

    @Test
    fun `a transfer past its budget is cut and the connection closed`() = runTest {
        val connection = FakeConnection()

        val sent = connectThenTransfer(l2capTransferMillis(1), { connection }) {
            delay(l2capTransferMillis(1) + 1)
            SendToMacOutcome.SENT
        }

        assertNull(sent)
        assertTrue(connection.closed)
    }
}
