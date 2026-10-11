package app.cue.launcher.data.ha

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HaConfigTest {

    @Test
    fun `a home address may be http`() {
        assertNull(haUrlError("http://homeassistant.local:8123", external = false))
        assertNull(haUrlError("https://homeassistant.local:8123", external = false))
        assertNull(haUrlError("http://192.168.1.10:8123", external = false))
        assertNull(haUrlError("http://homeassistant:8123", external = false))
        assertNull(haUrlError("http://10.0.0.5", external = false))
        assertNull(haUrlError("http://100.64.0.3:8123", external = false))
    }

    // Being on the right Wi-Fi is the whole protection of a cleartext address, and it protects nothing
    // once the address leaves the network — including when it arrives from the record of a discovered
    // instance rather than from the user's own typing.
    @Test
    fun `a cleartext home address has to be a local one`() {
        assertEquals(HaUrlError.INSECURE_HOME, haUrlError("http://ha.example.com", external = false))
        assertEquals(HaUrlError.INSECURE_HOME, haUrlError("http://8.8.8.8:8123", external = false))
        assertEquals(HaUrlError.INSECURE_HOME, haUrlError("http://example.ui.nabu.casa", external = false))
        assertNull(haUrlError("https://ha.example.com", external = false))
    }

    @Test
    fun `an address outside home must be https`() {
        assertEquals(
            HaUrlError.INSECURE_EXTERNAL,
            haUrlError("http://ha.example.com", external = true),
        )
        assertNull(haUrlError("https://ucet.ui.nabu.casa", external = true))
    }

    @Test
    fun `a blank field is not an error`() {
        assertNull(haUrlError("", external = true))
        assertNull(haUrlError("   ", external = false))
    }

    @Test
    fun `an address without a scheme and without a host is an error`() {
        assertEquals(HaUrlError.SCHEME, haUrlError("homeassistant.local:8123", external = false))
        assertEquals(HaUrlError.SCHEME, haUrlError("ftp://ha.local", external = false))
        assertEquals(HaUrlError.HOST, haUrlError("http://", external = false))
        assertEquals(HaUrlError.HOST, haUrlError("https://ha local", external = true))
    }

    @Test
    fun `the address is saved without a trailing slash and spaces`() {
        assertEquals("http://ha.local:8123", normalizeHaUrl("  http://ha.local:8123/  "))
        assertEquals("http://ha.local:8123", normalizeHaUrl("http://ha.local:8123"))
    }

    @Test
    fun `the connection is configured only with both an address and a token`() {
        assertFalse(HaConfig(urlInternal = "http://ha.local:8123").isConfigured)
        assertFalse(HaConfig(token = "abc").isConfigured)
        assertTrue(HaConfig(urlExternal = "https://ha.example", token = "abc").isConfigured)
    }

    @Test
    fun `the home wifi field splits on comma and newline`() {
        assertEquals(
            listOf("Doma-5G", "Doma-2G", "BSSID:a4:2b:b0:11:22:33"),
            haHomeNetworksOf(" Doma-5G , Doma-2G\nBSSID:a4:2b:b0:11:22:33 "),
        )
        assertEquals(emptyList<String>(), haHomeNetworksOf("  ,  "))
    }

    @Test
    fun `the same network does not get into the list twice`() {
        assertEquals(listOf("Doma-5G"), haHomeNetworksOf("Doma-5G, doma-5g"))
    }

    @Test
    fun `a home network is recognized by name or MAC address`() {
        val entries = listOf("Doma-5G", "BSSID:A4:2B:B0:11:22:33")

        assertTrue(haHomeNetworkMatches(entries, ssid = "doma-5g", bssid = null))
        assertTrue(haHomeNetworkMatches(entries, ssid = "Hotel", bssid = "a4:2b:b0:11:22:33"))
        assertFalse(haHomeNetworkMatches(entries, ssid = "Hotel", bssid = "ff:ff:ff:ff:ff:ff"))
    }

    // The prefix is offered in upper case, but a hand-typed entry has no reason to fail silently.
    @Test
    fun `the BSSID prefix is recognized however it was typed`() {
        assertTrue(haHomeNetworkMatches(listOf("bssid:a4:2b:b0:11:22:33"), ssid = null, bssid = "A4:2B:B0:11:22:33"))
        assertTrue(haHomeNetworkMatches(listOf("Bssid: A4:2B:B0:11:22:33"), ssid = null, bssid = "a4:2b:b0:11:22:33"))
    }

    @Test
    fun `an unknown network is not home`() {
        assertFalse(haHomeNetworkMatches(listOf("Doma-5G"), ssid = null, bssid = null))
        assertFalse(haHomeNetworkMatches(emptyList(), ssid = "Doma-5G", bssid = null))
    }

    @Test
    fun `only an http address counts as cleartext`() {
        assertTrue(haIsCleartext(" http://ha.local:8123 "))
        assertTrue(haIsCleartext("HTTP://ha.local"))
        assertFalse(haIsCleartext("https://ucet.ui.nabu.casa"))
        assertFalse(haIsCleartext(""))
    }

    @Test
    fun `a cleartext home address asks for at least one home network`() {
        assertTrue(haHomeNetworkMissing("http://ha.local:8123", emptyList()))
        assertFalse(haHomeNetworkMissing("http://ha.local:8123", listOf("Doma-5G")))
        assertFalse(haHomeNetworkMissing("https://ha.example.com", emptyList()))
        assertFalse(haHomeNetworkMissing("", emptyList()))
    }

    // A paired instance answers "am I home" on mDNS, which is what a phone without the location
    // permission has instead of a network name.
    @Test
    fun `a paired instance replaces the network name`() {
        assertFalse(haHomeNetworkMissing("http://ha.local:8123", emptyList(), paired = true))
        assertTrue(haHomeNetworkMissing("http://ha.local:8123", emptyList(), paired = false))
    }

    @Test
    fun `an address is local when its host says so`() {
        assertTrue(haUrlIsLocal("http://homeassistant.local:8123"))
        assertTrue(haUrlIsLocal("http://homeassistant:8123"))
        assertTrue(haUrlIsLocal("http://192.168.1.10:8123"))
        assertTrue(haUrlIsLocal("http://10.0.0.5"))
        assertTrue(haUrlIsLocal("https://172.16.4.4:8123"))
        assertTrue(haUrlIsLocal("https://100.64.0.3:8123"))
    }

    @Test
    fun `an address on the internet is not local`() {
        assertFalse(haUrlIsLocal("https://example.ui.nabu.casa"))
        assertFalse(haUrlIsLocal("https://ha.example.com"))
        assertFalse(haUrlIsLocal("https://8.8.8.8"))
        assertFalse(haUrlIsLocal(""))
    }

    @Test
    fun `the live channel keeps the scheme of the address it belongs to`() {
        assertEquals("ws://ha.local:8123/api/websocket", haWebsocketUrl("http://ha.local:8123"))
        assertEquals("wss://example.ui.nabu.casa/api/websocket", haWebsocketUrl("https://example.ui.nabu.casa"))
    }

    // A host the validator reads differently from the HTTP client is a way to get a public cleartext
    // address accepted as a local one: the client would connect to evil.example in each of these.
    @Test
    fun `an address that hides its real host is refused`() {
        listOf(
            "http://evil.example?.local",
            "http://evil.example#.local",
            "http://evil.example/?x=.local",
            "http://homeassistant.local@evil.example",
            "http://user:pass@homeassistant.local:8123",
            "http://evil.example\\.local",
            "http://homeassistant.local:8123/#",
        ).forEach { url ->
            assertEquals(HaUrlError.HOST, haUrlError(url, external = false), url)
            assertFalse(haUrlIsLocal(url), url)
        }
    }

    @Test
    fun `the scheme is read regardless of case and a path is fine`() {
        assertNull(haUrlError("HTTP://HomeAssistant.local:8123", external = false))
        assertNull(haUrlError("http://homeassistant.local:8123/", external = false))
        assertNull(haUrlError("https://ha.example.com/sub/path/", external = true))
        assertEquals("homeassistant.local", haUrlHost("HTTP://HomeAssistant.LOCAL:8123/"))
    }

    @Test
    fun `a port out of range is not an address`() {
        assertEquals(HaUrlError.HOST, haUrlError("http://ha.local:0", external = false))
        assertEquals(HaUrlError.HOST, haUrlError("http://ha.local:65536", external = false))
        assertNull(haUrlError("http://ha.local:65535", external = false))
    }

    // The resolver reads 134744072, 0x8080808 and 010.010.010.010 as 8.8.8.8, so none of them may pass
    // as a local single-label name or a private range.
    @Test
    fun `a numeric host counts as local only in its canonical form`() {
        assertFalse(haUrlIsLocal("http://134744072"))
        assertFalse(haUrlIsLocal("http://0x8080808"))
        assertFalse(haUrlIsLocal("http://010.010.010.010"))
        assertFalse(haUrlIsLocal("http://192.168.1"))
        assertEquals(HaUrlError.INSECURE_HOME, haUrlError("http://134744072:8123", external = false))
        assertTrue(haUrlIsLocal("http://10.10.10.10"))
    }

    @Test
    fun `an IPv6 literal is local only in a private range`() {
        assertTrue(haUrlIsLocal("http://[fd00::1]:8123"))
        assertTrue(haUrlIsLocal("http://[fe80::1]:8123"))
        assertTrue(haUrlIsLocal("http://[::1]:8123"))
        assertFalse(haUrlIsLocal("http://[2001:4860:4860::8888]:8123"))
        assertFalse(haUrlIsLocal("http://[::ffff:8.8.8.8]:8123"))
        assertEquals("[fd00::1]", haUrlHost("http://[FD00::1]:8123"))
        assertEquals(HaUrlError.INSECURE_HOME, haUrlError("http://[2001:4860:4860::8888]", external = false))
    }

    // java.net.URI has no host for either of these, but the HTTP client connects to both, so both
    // are addresses — read with the same rules for what counts as local.
    @Test
    fun `an underscore or a non-ASCII host is an address`() {
        assertNull(haUrlError("http://my_ha:8123", external = false))
        assertEquals("my_ha", haUrlHost("http://My_HA:8123"))
        assertTrue(haUrlIsLocal("http://my_ha:8123"))
        assertTrue(haUrlIsLocal("http://my_ha.local:8123"))
        assertFalse(haUrlIsLocal("http://my_ha.example.com"))
        assertEquals(HaUrlError.INSECURE_HOME, haUrlError("http://my_ha.example.com", external = false))

        assertNull(haUrlError("https://dům.example", external = true))
        assertNull(haUrlError("https://dům.example:8443/", external = true))
        assertEquals("xn--dm-sua.example", haUrlHost("https://Dům.example"))
        assertFalse(haUrlIsLocal("https://dům.example"))
    }

    @Test
    fun `a host read by hand still hides nothing`() {
        listOf(
            "http://user@my_ha:8123",
            "http://my_ha:8123?.local",
            "http://my_ha:abc",
            "http://my_ha:",
            "http://my_ha:70000",
            "http://my%5Fha",
        ).forEach { url ->
            assertEquals(HaUrlError.HOST, haUrlError(url, external = false), url)
            assertFalse(haUrlIsLocal(url), url)
        }
    }

    // java.net.URI takes an empty port for no port at all, OkHttp refuses the address outright.
    @Test
    fun `an empty port is not an address`() {
        assertEquals(HaUrlError.HOST, haUrlError("http://ha.local:/", external = false))
        assertEquals(HaUrlError.HOST, haUrlError("https://ha.example.com:", external = true))
    }
}
