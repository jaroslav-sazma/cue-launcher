package app.cue.launcher.system.bridge

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.net.InetAddress

class BridgeLocatorTest {

    private val saved = BridgeEndpoint("192.168.1.20", 52817)
    private val moved = BridgeEndpoint("192.168.1.42", 52817)

    private class Dialer(private val answering: Set<BridgeEndpoint>) {
        val tried = mutableListOf<BridgeEndpoint>()

        suspend fun connect(endpoint: BridgeEndpoint): String {
            tried += endpoint
            if (endpoint !in answering) throw IOException("no answer at $endpoint")
            return "connected to ${endpoint.host}"
        }
    }

    @Test
    fun `the saved address that answers is used without asking the network`() = runBlocking {
        val dialer = Dialer(setOf(saved))
        var located = false
        val saves = mutableListOf<BridgeEndpoint>()
        val result = dialRelocating(saved, dialer::connect, { located = true; moved }, { saves += it })
        assertEquals("connected to 192.168.1.20", result)
        assertEquals(false, located, "nothing should be resolved while the saved address works")
        assertTrue(saves.isEmpty())
    }

    @Test
    fun `a Mac found at a new address is connected to and saved`() = runBlocking {
        val dialer = Dialer(setOf(moved))
        val saves = mutableListOf<BridgeEndpoint>()
        val result = dialRelocating(saved, dialer::connect, { moved }, { saves += it })
        assertEquals("connected to 192.168.1.42", result)
        assertEquals(listOf(saved, moved), dialer.tried)
        assertEquals(listOf(moved), saves)
    }

    @Test
    fun `a new address that fails the connection is not saved`() {
        val dialer = Dialer(emptySet())
        val saves = mutableListOf<BridgeEndpoint>()
        val failure = assertThrows<IOException> {
            runBlocking { dialRelocating(saved, dialer::connect, { moved }, { saves += it }) }
        }
        assertEquals("no answer at $moved", failure.message)
        assertTrue(saves.isEmpty())
    }

    @Test
    fun `without an answer by name the original failure stands`() {
        val dialer = Dialer(emptySet())
        val failure = assertThrows<IOException> {
            runBlocking { dialRelocating(saved, dialer::connect, { null }, { error("nothing to save") }) }
        }
        assertEquals("no answer at $saved", failure.message)
        assertEquals(listOf(saved), dialer.tried)
    }

    @Test
    fun `the same address resolved again is not tried twice`() {
        val dialer = Dialer(emptySet())
        val original = assertThrows<IOException> {
            runBlocking { dialRelocating(saved, dialer::connect, { saved }, { error("nothing to save") }) }
        }
        assertEquals(listOf(saved), dialer.tried)
        assertEquals("no answer at $saved", original.message)
    }

    @Test
    fun `a pin mismatch at the old address also looks for the Mac`() = runBlocking {
        val saves = mutableListOf<BridgeEndpoint>()
        val result = dialRelocating(
            saved,
            { endpoint ->
                if (endpoint == saved) throw BridgePinMismatchException(IllegalStateException("another device"))
                "connected"
            },
            { moved },
            { saves += it },
        )
        assertSame("connected", result)
        assertEquals(listOf(moved), saves)
    }

    @Test
    fun `IPv4 is preferred over IPv6`() {
        val addresses = listOf(address("fd00::5"), address("192.168.1.42"))
        assertEquals(BridgeEndpoint("192.168.1.42", 52817), bonjourEndpointOf(addresses, 52817, VERSION))
    }

    @Test
    fun `a routable IPv6 address is used when there is no IPv4`() {
        val endpoint = bonjourEndpointOf(listOf(address("fe80::1"), address("fd00::5")), 52817, VERSION)
        assertEquals("fd00:0:0:0:0:0:0:5", endpoint?.host)
    }

    @Test
    fun `a link-local IPv6 address alone is not usable`() {
        assertNull(bonjourEndpointOf(listOf(address("fe80::1")), 52817, VERSION))
    }

    @Test
    fun `an instance of another protocol version or without one is skipped`() {
        val addresses = listOf(address("192.168.1.42"))
        assertNull(bonjourEndpointOf(addresses, 52817, "2".encodeToByteArray()))
        assertNull(bonjourEndpointOf(addresses, 52817, null))
    }

    @Test
    fun `a port out of range is skipped`() {
        val addresses = listOf(address("192.168.1.42"))
        assertNull(bonjourEndpointOf(addresses, 0, VERSION))
        assertNull(bonjourEndpointOf(addresses, 65_536, VERSION))
    }

    private fun address(literal: String): InetAddress = InetAddress.getByName(literal)

    private companion object {
        val VERSION = BRIDGE_PROTOCOL_VERSION.toString().encodeToByteArray()
    }
}
