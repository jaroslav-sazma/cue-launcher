package app.cue.launcher.system.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.InetAddress

class BridgeBrowserTest {

    private val lan = InetAddress.getByAddress(byteArrayOf(192.toByte(), 168.toByte(), 1, 20))
    private val pairing = mapOf("v" to "1".encodeToByteArray(), "pair" to "1".encodeToByteArray())

    private fun target(name: String, host: String = "192.168.1.20") = PairingTarget(name, host, PORT)

    @Test
    fun `a Mac in pairing mode of this protocol version is a target`() {
        assertEquals(target("Example-Mac"), pairingTargetOf("Example-Mac", listOf(lan), PORT, pairing))
    }

    @Test
    fun `a Mac without the pairing flag, or with another value, is not one`() {
        assertNull(pairingTargetOf("Example-Mac", listOf(lan), PORT, mapOf("v" to "1".encodeToByteArray())))
        assertNull(pairingTargetOf("Example-Mac", listOf(lan), PORT, pairing + ("pair" to "0".encodeToByteArray())))
        assertNull(pairingTargetOf("Example-Mac", listOf(lan), PORT, pairing + ("pair" to null)))
    }

    @Test
    fun `a Mac in pairing mode of another protocol version, or without an address, is not one`() {
        assertNull(pairingTargetOf("Example-Mac", listOf(lan), PORT, pairing + ("v" to "2".encodeToByteArray())))
        assertNull(pairingTargetOf("Example-Mac", emptyList(), PORT, pairing))
        assertNull(pairingTargetOf("Example-Mac", listOf(lan), 0, pairing))
    }

    @Test
    fun `no Mac found asks for pairing mode, one is paired with, several are offered by name`() {
        assertEquals(MacBrowse.None, browseOutcome(emptyList()))
        assertEquals(MacBrowse.One(target("Example-Mac")), browseOutcome(listOf(target("Example-Mac"))))
        assertEquals(
            MacBrowse.Several(listOf(target("attic"), target("Basement", "192.168.1.21"))),
            browseOutcome(listOf(target("Basement", "192.168.1.21"), target("attic"))),
        )
    }

    @Test
    fun `the same Mac seen twice is one Mac`() {
        assertEquals(
            MacBrowse.One(target("Example-Mac")),
            browseOutcome(listOf(target("Example-Mac"), target("Example-Mac"))),
        )
    }

    private companion object {
        const val PORT = 52_817
    }
}
