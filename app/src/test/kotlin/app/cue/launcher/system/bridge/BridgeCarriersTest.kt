package app.cue.launcher.system.bridge

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

class BridgeCarriersTest {

    private var lanUp = true
    private val carriers = BridgeCarriers(lanUp = { lanUp })
    private val route = L2capRoute { error("the test dials through a fake") }

    private class Window(var route: L2capRoute?) : L2capRouteSource {
        override fun l2capRoute(): L2capRoute? = route
    }

    private class Dialer(var tcpAnswers: Boolean = true, var l2capAnswers: Boolean = true) {
        val tried = mutableListOf<Carrier>()
        var duringTcp: () -> Unit = {}
        var duringL2cap: () -> Unit = {}

        fun tcp(): String {
            tried += Carrier.TCP
            duringTcp()
            if (!tcpAnswers) throw IOException("no answer over tcp")
            return "tcp"
        }

        fun l2cap(): String {
            tried += Carrier.L2CAP
            duringL2cap()
            if (!l2capAnswers) throw IOException("no answer over l2cap")
            return "l2cap"
        }
    }

    private suspend fun BridgeCarriers.dial(dialer: Dialer, tcp: Boolean = true): Carried<String>? =
        dial(tcp = { if (tcp) dialer.tcp() else null }, l2cap = { dialer.l2cap() })

    @Test
    fun `TCP is tried first and Bluetooth is left alone when it answers`() = runBlocking {
        carriers.attach(Window(route))
        val dialer = Dialer()

        assertEquals(Carrier.TCP, carriers.dial(dialer)?.carrier)
        assertEquals(listOf(Carrier.TCP), dialer.tried)
    }

    @Test
    fun `Bluetooth carries the dial when TCP fails`() = runBlocking {
        carriers.attach(Window(route))
        val dialer = Dialer(tcpAnswers = false)

        val carried = carriers.dial(dialer)

        assertEquals(Carrier.L2CAP, carried?.carrier)
        assertEquals("l2cap", carried?.value)
        assertEquals(listOf(Carrier.TCP, Carrier.L2CAP), dialer.tried)
    }

    @Test
    fun `after a dial over Bluetooth the next one tries Bluetooth first`() = runBlocking {
        carriers.attach(Window(route))
        val dialer = Dialer(tcpAnswers = false)
        carriers.dial(dialer)
        dialer.tried.clear()

        assertEquals(Carrier.L2CAP, carriers.dial(dialer)?.carrier)
        assertEquals(listOf(Carrier.L2CAP), dialer.tried)
    }

    @Test
    fun `a dial that succeeds over TCP puts TCP first again`() = runBlocking {
        carriers.attach(Window(route))
        val dialer = Dialer(tcpAnswers = false)
        carriers.dial(dialer)
        dialer.tcpAnswers = true
        dialer.l2capAnswers = false
        dialer.tried.clear()

        assertEquals(Carrier.TCP, carriers.dial(dialer)?.carrier)
        assertEquals(listOf(Carrier.L2CAP, Carrier.TCP), dialer.tried)

        dialer.l2capAnswers = true
        dialer.tried.clear()
        carriers.dial(dialer)
        assertEquals(listOf(Carrier.TCP), dialer.tried)
    }

    @Test
    fun `the remembered carrier goes with the window`() = runBlocking {
        val first = Window(route)
        carriers.attach(first)
        val dialer = Dialer(tcpAnswers = false)
        carriers.dial(dialer)
        carriers.detach(first)
        carriers.attach(Window(route))
        dialer.tcpAnswers = true
        dialer.tried.clear()

        carriers.dial(dialer)

        assertEquals(listOf(Carrier.TCP), dialer.tried)
    }

    @Test
    fun `a dial that ends after the window closed leaves the next window's memory alone`() = runBlocking {
        val first = Window(route)
        carriers.attach(first)
        val dialer = Dialer(tcpAnswers = false)
        dialer.duringL2cap = {
            carriers.detach(first)
            carriers.attach(Window(route))
        }
        assertEquals(Carrier.L2CAP, carriers.dial(dialer)?.carrier)
        dialer.duringL2cap = {}
        dialer.tried.clear()

        carriers.dial(dialer)

        assertEquals(listOf(Carrier.TCP, Carrier.L2CAP), dialer.tried)
    }

    @Test
    fun `detaching a window that is no longer attached leaves the current one`() {
        val stale = Window(route)
        val current = Window(route)
        carriers.attach(stale)
        carriers.attach(current)

        carriers.detach(stale)

        assertSame(route, carriers.l2capRoute())
    }

    @Test
    fun `without a window only TCP is tried`() {
        val dialer = Dialer(tcpAnswers = false)

        val failure = assertThrows<IOException> { runBlocking { carriers.dial(dialer) } }

        assertEquals("no answer over tcp", failure.message)
        assertEquals(listOf(Carrier.TCP), dialer.tried)
    }

    @Test
    fun `a GATT link without a PSM gives no route`() {
        carriers.attach(Window(route = null))
        val dialer = Dialer(tcpAnswers = false)

        val failure = assertThrows<IOException> { runBlocking { carriers.dial(dialer) } }

        assertEquals("no answer over tcp", failure.message)
        assertEquals(listOf(Carrier.TCP), dialer.tried)
        assertNull(carriers.l2capRoute())
    }

    @Test
    fun `with neither TCP allowed nor a route there is nothing to dial`() = runBlocking {
        carriers.attach(Window(route = null))
        val dialer = Dialer()

        assertNull(carriers.dial(dialer, tcp = false))
        assertEquals(emptyList<Carrier>(), dialer.tried)
    }

    @Test
    fun `without TCP the dial goes straight over Bluetooth`() = runBlocking {
        carriers.attach(Window(route))
        val dialer = Dialer()

        assertEquals(Carrier.L2CAP, carriers.dial(dialer, tcp = false)?.carrier)
        assertEquals(listOf(Carrier.L2CAP), dialer.tried)
    }

    @Test
    fun `without Wi-Fi or Ethernet TCP is not dialled and Bluetooth carries the dial`() = runBlocking {
        carriers.attach(Window(route))
        lanUp = false
        val dialer = Dialer()

        assertEquals(Carrier.L2CAP, carriers.dial(dialer)?.carrier)
        assertEquals(listOf(Carrier.L2CAP), dialer.tried)
    }

    @Test
    fun `without Wi-Fi, Ethernet or a route nothing is dialled at all`() = runBlocking {
        lanUp = false
        val dialer = Dialer()

        assertNull(carriers.dial(dialer))
        assertEquals(emptyList<Carrier>(), dialer.tried)
    }

    @Test
    fun `a route that came up during the TCP attempt is used`() = runBlocking {
        val window = Window(route = null)
        carriers.attach(window)
        val dialer = Dialer(tcpAnswers = false)
        dialer.duringTcp = { window.route = route }

        assertEquals(Carrier.L2CAP, carriers.dial(dialer)?.carrier)
    }

    @Test
    fun `when both fail the TCP failure stands unless it was a pin mismatch elsewhere`() {
        carriers.attach(Window(route))
        val both = Dialer(tcpAnswers = false, l2capAnswers = false)
        val first = assertThrows<IOException> { runBlocking { carriers.dial(both) } }
        assertEquals("no answer over tcp", first.message)

        val mismatch = assertThrows<BridgePinMismatchException> {
            runBlocking {
                dialCarriers<String>(listOf(Carrier.L2CAP, Carrier.TCP)) { carrier ->
                    when (carrier) {
                        Carrier.L2CAP -> throw IOException("no answer over l2cap")
                        Carrier.TCP -> throw BridgePinMismatchException(IllegalStateException("another device"))
                    }
                }
            }
        }
        assertEquals("the peer certificate is not the expected one", mismatch.message)
    }

    @Test
    fun `a PSM is exactly two bytes, big-endian and not zero`() {
        assertEquals(0x0081, bridgePsmOf(byteArrayOf(0x00, 0x81.toByte())))
        assertEquals(0xF00D, bridgePsmOf(byteArrayOf(0xF0.toByte(), 0x0D)))
        assertNull(bridgePsmOf(byteArrayOf(0x00, 0x00)))
        assertNull(bridgePsmOf(byteArrayOf(0x81.toByte())))
        assertNull(bridgePsmOf(byteArrayOf(0x00, 0x81.toByte(), 0x00)))
        assertNull(bridgePsmOf(ByteArray(0)))
    }
}
