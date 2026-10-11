package app.cue.launcher.data.ha

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HaInstanceTest {

    private fun txt(vararg pairs: Pair<String, String>): Map<String, ByteArray?> =
        pairs.associate { (key, value) -> key to value.toByteArray() }

    @Test
    fun `the addresses come from what the instance publishes`() {
        val instance = haInstanceOf(
            serviceName = "Home",
            attributes = txt(
                HA_TXT_UUID to "abc123",
                HA_TXT_NAME to "Doma",
                HA_TXT_VERSION to "2026.9.1",
                HA_TXT_INTERNAL_URL to "http://homeassistant.local:8123/",
                HA_TXT_EXTERNAL_URL to "https://example.ui.nabu.casa",
            ),
            host = "homeassistant.local.",
            port = 8123,
        )

        assertEquals("abc123", instance.uuid)
        assertEquals("Doma", instance.label)
        assertEquals("http://homeassistant.local:8123", instance.homeUrl)
        assertEquals("https://example.ui.nabu.casa", instance.awayUrl)
    }

    @Test
    fun `without an internal url the mDNS host and port are used`() {
        val instance = haInstanceOf(
            serviceName = "Home",
            attributes = txt(HA_TXT_UUID to "abc", HA_TXT_INTERNAL_URL to ""),
            host = "homeassistant.local",
            port = 8123,
        )

        assertEquals("http://homeassistant.local:8123", instance.homeUrl)
    }

    // The away field has to hold an https address, exactly as the user's own typing must.
    @Test
    fun `a cleartext external url is not offered as the away address`() {
        val instance = haInstanceOf(
            serviceName = "Home",
            attributes = txt(HA_TXT_EXTERNAL_URL to "http://ha.example.com"),
            host = "ha.local",
            port = 8123,
        )

        assertTrue(instance.awayUrl.isEmpty())
    }

    // Anything on the network can publish an mDNS record, so what it offers has to pass the same
    // validation as an address the user types: a cleartext address to a host outside the network would
    // carry the credential over the internet in the clear, and no Save could make that safe.
    @Test
    fun `a published cleartext address outside the network is not offered`() {
        val instance = haInstanceOf(
            serviceName = "Home",
            attributes = txt(
                HA_TXT_UUID to "abc",
                HA_TXT_INTERNAL_URL to "http://evil.example.com",
                HA_TXT_EXTERNAL_URL to "http://evil.example.com",
            ),
            host = "homeassistant.local",
            port = 8123,
        )

        assertEquals("http://homeassistant.local:8123", instance.homeUrl)
        assertTrue(instance.awayUrl.isEmpty())
    }

    @Test
    fun `a published address that is not a url at all is not offered`() {
        val instance = haInstanceOf(
            serviceName = "Home",
            attributes = txt(HA_TXT_INTERNAL_URL to "javascript:alert(1)"),
            host = "",
            port = 0,
        )

        assertTrue(instance.homeUrl.isEmpty())
    }

    // An IPv6 literal is left alone rather than assembled into an address nothing else can parse.
    @Test
    fun `an IPv6 host is left for the user to type`() {
        val instance = haInstanceOf(
            serviceName = "Home",
            attributes = txt(HA_TXT_UUID to "abc"),
            host = "fd00::1",
            port = 8123,
        )

        assertTrue(instance.homeUrl.isEmpty())
    }

    @Test
    fun `an instance with no name falls back to the service name`() {
        val instance = haInstanceOf(serviceName = "Home Assistant", attributes = emptyMap(), host = "", port = 0)

        assertEquals("Home Assistant", instance.label)
        assertTrue(instance.homeUrl.isEmpty())
    }
}
