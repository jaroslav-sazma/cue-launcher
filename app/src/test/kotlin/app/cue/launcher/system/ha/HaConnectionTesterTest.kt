package app.cue.launcher.system.ha

import app.cue.launcher.data.ha.HaConfig
import app.cue.launcher.data.ha.HaEndpoint
import app.cue.launcher.data.ha.HaEndpointKind
import app.cue.launcher.data.ha.HaProbe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

// The test runs on the draft before Save. A stored credential sent to an address the picker put in a
// field would hand it to whoever published that mDNS record.
class HaConnectionTesterTest {

    private val sent = mutableListOf<String>()

    private val ok = HaProbe.Ok(entities = 1, millis = 0)

    private suspend fun test(draft: HaConfig, saved: HaConfig, atHome: Boolean = true) =
        haProbeEach(haTestedEndpoints(draft), draft, saved, atHome) { endpoint: HaEndpoint ->
            sent += endpoint.url
            ok
        }

    @Test
    fun `a stored session is not sent to an address that was not saved`() = runTest {
        val draft = session.copy(urlExternal = ROGUE_URL)

        val results = test(draft, saved = session)

        assertEquals(HaProbe.Unsaved, results[HaEndpointKind.AWAY])
        assertEquals(listOf(HOME_URL), sent)
    }

    @Test
    fun `a stored long-lived token is not sent to an address that was not saved`() = runTest {
        val draft = longLived.copy(urlInternal = ROGUE_URL, urlExternal = ROGUE_URL)

        val results = test(draft, saved = longLived)

        assertEquals(mapOf(HaEndpointKind.HOME to HaProbe.Unsaved, HaEndpointKind.AWAY to HaProbe.Unsaved), results)
        assertEquals(emptyList<String>(), sent)
    }

    @Test
    fun `saved addresses are probed, whatever else changed in the draft`() = runTest {
        val draft = session.copy(urlExternal = "$AWAY_URL/ ", homeNetworks = listOf("Chata"))

        val results = test(draft, saved = session)

        assertEquals(mapOf(HaEndpointKind.HOME to ok, HaEndpointKind.AWAY to ok), results)
        assertEquals(listOf(HOME_URL, AWAY_URL), sent)
    }

    @Test
    fun `a typed token goes to the address it was typed for`() = runTest {
        val draft = HaConfig(urlInternal = HOME_URL, urlExternal = ROGUE_URL, token = "typed-token")

        assertEquals(ok, test(draft, saved = session)[HaEndpointKind.AWAY])
        assertEquals(ok, test(draft.copy(token = "another"), saved = longLived)[HaEndpointKind.AWAY])
        assertEquals(listOf(HOME_URL, ROGUE_URL, HOME_URL, ROGUE_URL), sent)
    }

    @Test
    fun `a first setup with a typed token is tested before anything is saved`() = runTest {
        val draft = HaConfig(urlExternal = ROGUE_URL, token = "typed-token")

        assertEquals(mapOf(HaEndpointKind.AWAY to ok), test(draft, saved = HaConfig()))
        assertEquals(listOf(ROGUE_URL), sent)
    }

    @Test
    fun `a cleartext home address off the home network stays untried`() = runTest {
        val results = test(session, saved = session, atHome = false)

        assertEquals(HaProbe.Blocked, results[HaEndpointKind.HOME])
        assertEquals(listOf(AWAY_URL), sent)
    }

    @Test
    fun `an untried home address does not make away the network's working endpoint`() = runTest {
        val draft = longLived.copy(urlInternal = ROGUE_URL)

        val results = test(draft, saved = longLived)

        assertEquals(mapOf(HaEndpointKind.HOME to HaProbe.Unsaved, HaEndpointKind.AWAY to ok), results)
        assertNull(haWorkingKind(results))
    }

    @Test
    fun `the only address that answered is remembered`() {
        val results = mapOf(HaEndpointKind.HOME to HaProbe.Timeout, HaEndpointKind.AWAY to ok)

        assertEquals(HaEndpointKind.AWAY, haWorkingKind(results))
    }

    private companion object {

        const val HOME_URL = "http://homeassistant.local:8123"

        const val AWAY_URL = "https://ha.example.com"

        const val ROGUE_URL = "https://rogue.example.net"

        val session = HaConfig(
            urlInternal = HOME_URL,
            urlExternal = AWAY_URL,
            refreshToken = "refresh-token",
            oauthClientId = "http://127.0.0.1:1234/cue-launcher",
        )

        val longLived = HaConfig(urlInternal = HOME_URL, urlExternal = AWAY_URL, token = "stored-token")
    }
}
