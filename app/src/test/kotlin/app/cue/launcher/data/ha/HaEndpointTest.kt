package app.cue.launcher.data.ha

import io.ktor.client.network.sockets.ConnectTimeoutException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class HaEndpointTest {

    private val both = HaConfig(
        urlInternal = "http://ha.local:8123",
        urlExternal = "https://ucet.ui.nabu.casa",
        token = "token",
        homeNetworks = listOf("Doma-5G"),
    )

    private val home = HaEndpoint(HaEndpointKind.HOME, "http://ha.local:8123")

    private val away = HaEndpoint(HaEndpointKind.AWAY, "https://ucet.ui.nabu.casa")

    private fun onWifi(ssid: String?, bssid: String? = null) =
        HaNetwork(connected = true, ssid = ssid, bssid = bssid)

    @Test
    fun `without addresses there is nowhere to connect`() {
        assertEquals(emptyList<HaEndpoint>(), haEndpointOrder(HaConfig(), onWifi("Doma-5G"), null))
    }

    @Test
    fun `a single address is tried regardless of the network`() {
        val onlyAway = both.copy(urlInternal = "")

        assertEquals(listOf(away), haEndpointOrder(onlyAway, onWifi("Doma-5G"), null))
        assertEquals(listOf(away), haEndpointOrder(onlyAway, HaNetwork(), null))
        assertEquals(listOf(home), haEndpointOrder(both.copy(urlExternal = ""), onWifi("Doma-5G"), null))
    }

    @Test
    fun `a cleartext home address is not used off the home network`() {
        val onlyHome = both.copy(urlExternal = "")

        assertEquals(emptyList<HaEndpoint>(), haEndpointOrder(onlyHome, onWifi("Hotel-Guest"), null))
        assertEquals(emptyList<HaEndpoint>(), haEndpointOrder(onlyHome, HaNetwork(), null))
        assertEquals(listOf(away), haEndpointOrder(both, onWifi("Hotel-Guest"), null))
    }

    @Test
    fun `a cleartext home address without a named network is never used`() {
        val unnamed = both.copy(homeNetworks = emptyList())

        assertEquals(listOf(away), haEndpointOrder(unnamed, onWifi("Doma-5G"), null))
        assertEquals(listOf(away), haEndpointOrder(unnamed, onWifi("Doma-5G"), HaEndpointKind.HOME))
    }

    @Test
    fun `an https home address needs no network name`() {
        val secure = both.copy(urlInternal = "https://ha.local:8123", homeNetworks = emptyList())
        val secureHome = HaEndpoint(HaEndpointKind.HOME, "https://ha.local:8123")

        assertEquals(listOf(away, secureHome), haEndpointOrder(secure, onWifi("Hotel-Guest"), null))
        assertEquals(listOf(secureHome, away), haEndpointOrder(secure, HaNetwork(), HaEndpointKind.HOME))
    }

    @Test
    fun `on the home network the home address goes first`() {
        assertEquals(listOf(home, away), haEndpointOrder(both, onWifi("Doma-5G"), null))
        assertEquals(listOf(home, away), haEndpointOrder(both, onWifi("doma-5g"), null))
    }

    @Test
    fun `without a network name or on a foreign network only the away address is left`() {
        assertEquals(listOf(away), haEndpointOrder(both, onWifi(null), null))
        assertEquals(listOf(away), haEndpointOrder(both, onWifi("Hotel-Guest"), null))
        assertEquals(listOf(away), haEndpointOrder(both, HaNetwork(), null))
    }

    @Test
    fun `the home AP mac address is enough even without a network name`() {
        val byBssid = both.copy(homeNetworks = listOf("BSSID:a4:2b:b0:11:22:33"))

        assertEquals(
            listOf(home, away),
            haEndpointOrder(byBssid, onWifi(ssid = null, bssid = "A4:2B:B0:11:22:33"), null),
        )
    }

    @Test
    fun `the cache decides the order but cannot bring back a dropped cleartext address`() {
        assertEquals(listOf(away, home), haEndpointOrder(both, onWifi("Doma-5G"), HaEndpointKind.AWAY))
        assertEquals(listOf(away), haEndpointOrder(both, onWifi("Hotel-Guest"), HaEndpointKind.HOME))
    }

    @Test
    fun `the cache key is the network name`() {
        assertEquals("doma-5g", haNetworkKey(onWifi("Doma-5G")))
        assertNull(haNetworkKey(onWifi(null)))
        assertNull(haNetworkKey(HaNetwork()))
        assertNull(haNetworkKey(null))
    }

    @Test
    fun `a second address is tried only when no response arrived`() {
        assertTrue(haFallbackAllowed(HaProbe.Network("timeout")))
        assertTrue(haFallbackAllowed(HaProbe.LocalNetworkDenied))
        assertFalse(haFallbackAllowed(HaProbe.Http(401)))
        assertFalse(haFallbackAllowed(HaProbe.Http(500)))
        assertFalse(haFallbackAllowed(HaProbe.Ok(entities = 32, millis = 120)))
        assertFalse(haFallbackAllowed(HaProbe.NotConfigured))
    }

    // A service call runs to completion before Home Assistant answers, so a timeout may mean the gate
    // is already open. Sending it again to the other address would open it twice.
    @Test
    fun `a call that timed out is never repeated on the other address`() {
        assertFalse(haFallbackAllowed(HaProbe.Timeout))
    }

    // The other half of the same promise: a call is only repeated on the second address when it
    // provably never left the phone. A connection that broke later may have opened the gate already.
    @Test
    fun `only a call that never reached the instance may be tried again`() {
        assertTrue(haNeverSent(ConnectException("Connection refused")))
        assertTrue(haNeverSent(ConnectTimeoutException("Connect timeout has expired", null)))
        assertTrue(haNeverSent(UnknownHostException("ha.local")))
        assertTrue(haNeverSent(SSLHandshakeException("no trust")))

        assertFalse(haNeverSent(SocketException("Connection reset")))
        assertFalse(haNeverSent(IOException("unexpected end of stream")))
        assertFalse(haNeverSent(SocketTimeoutException("Read timed out")))
    }

    @Test
    fun `the presence of the paired instance can decide the order instead of the network name`() {
        val unnamed = both.copy(homeNetworks = emptyList(), instanceUuid = "abc")

        assertEquals(listOf(home, away), haEndpointOrder(unnamed, atHome = true, lastWorking = null))
        assertEquals(listOf(away), haEndpointOrder(unnamed, atHome = false, lastWorking = null))
    }

    // Android 17 answers a blocked local connection with a connect timeout, which on its own reads like
    // an instance that is switched off. The user has to be told which of the two it is.
    @Test
    fun `a local address that cannot be reached without the permission says so`() {
        assertEquals(
            HaProbe.LocalNetworkDenied,
            haDiagnose(HaProbe.Network("timeout"), local = true, paired = false) { false },
        )
    }

    @Test
    fun `the permission is only the answer for a local address that was not approved in the picker`() {
        val timeout = HaProbe.Network("timeout")

        assertEquals(timeout, haDiagnose(timeout, local = false, paired = false) { false })
        assertEquals(timeout, haDiagnose(timeout, local = true, paired = true) { false })
        assertEquals(timeout, haDiagnose(timeout, local = true, paired = false) { true })
    }

    // Asking the system whether a permission is held costs a call of its own, and an answer that has
    // nothing to do with the local network cannot be improved by it.
    @Test
    fun `an answer that is not a network failure is left alone without asking about the permission`() {
        val ok = HaProbe.Ok(entities = 4, millis = 12)

        assertEquals(ok, haDiagnose(ok, local = true, paired = false) { error("must not be asked") })
        assertEquals(
            HaProbe.Http(500),
            haDiagnose(HaProbe.Http(500), local = true, paired = false) { error("must not be asked") },
        )
    }

    @Test
    fun `a header with a token is bearer without extra spaces`() {
        assertEquals("Bearer abc123", haAuthorizationHeader("  abc123 "))
    }

    @Test
    fun `a network error sends the call to the second address`() = runTest {
        val tried = mutableListOf<HaEndpoint>()

        val attempt = haAttempt(listOf(home, away), fallback = HaProbe.NotConfigured, probeOf = { it }) { endpoint ->
            tried += endpoint
            if (endpoint == home) HaProbe.Network("timeout") else HaProbe.Ok(entities = 1, millis = 80)
        }

        assertEquals(listOf(home, away), tried)
        assertEquals(away, attempt.endpoint)
        assertTrue(attempt.succeeded)
    }

    @Test
    fun `an HA response does not try the second address`() = runTest {
        val tried = mutableListOf<HaEndpoint>()

        val attempt = haAttempt(listOf(home, away), fallback = HaProbe.NotConfigured, probeOf = { it }) { endpoint ->
            tried += endpoint
            HaProbe.Http(401)
        }

        assertEquals(listOf(home), tried)
        assertFalse(attempt.succeeded)
        assertEquals(HaProbe.Http(401), attempt.result)
    }

    @Test
    fun `after two failures the last result remains`() = runTest {
        val attempt = haAttempt(listOf(home, away), fallback = HaProbe.NotConfigured, probeOf = { it }) { endpoint ->
            HaProbe.Network(if (endpoint == home) "dns" else "handshake")
        }

        assertEquals(away, attempt.endpoint)
        assertEquals(HaProbe.Network("handshake"), attempt.result)
    }

    @Test
    fun `without an address nothing is called`() = runTest {
        val attempt = haAttempt(emptyList(), fallback = HaProbe.NotConfigured, probeOf = { it }) {
            error("nothing should be called here")
        }

        assertNull(attempt.endpoint)
        assertEquals(HaProbe.NotConfigured, attempt.result)
    }
}
