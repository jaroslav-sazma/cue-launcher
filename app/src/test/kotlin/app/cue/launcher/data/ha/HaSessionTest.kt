package app.cue.launcher.data.ha

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI

// Home Assistant accepts a redirect_uri without fetching anything only when it shares scheme and host
// with the client_id. If that ever drifts apart, signing in fails with "invalid redirect uri" and the
// only way left is pasting a long-lived token.
class HaSessionTest {

    @Test
    fun `the client id and the callback share scheme and host`() {
        val clientId = haClientId(41234)
        val redirect = haRedirectUri(41234)

        assertEquals("http://127.0.0.1:41234/cue-launcher", clientId)
        assertTrue(redirect.startsWith("$clientId/"))
    }

    @Test
    fun `the callback socket listens on the host the client id names`() {
        assertEquals(URI(haClientId(41234)).host, HA_CALLBACK_ADDRESS.hostAddress)
    }

    @Test
    fun `the client id has a path, which IndieAuth requires`() {
        assertTrue(haClientId(8080).removePrefix("http://").substringAfter('/').isNotEmpty())
    }

    @Test
    fun `the authorize url carries the code grant and the state`() {
        val url = haAuthorizeUrl(
            base = "https://ha.example.com/",
            clientId = haClientId(1234),
            redirectUri = haRedirectUri(1234),
            state = "abc def",
        )

        assertTrue(url.startsWith("https://ha.example.com/auth/authorize?"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("client_id=http%3A%2F%2F127.0.0.1%3A1234%2Fcue-launcher"))
        assertTrue(url.contains("redirect_uri=http%3A%2F%2F127.0.0.1%3A1234%2Fcue-launcher%2Fauth"))
        assertTrue(url.contains("state=abc+def") || url.contains("state=abc%20def"))
    }

    @Test
    fun `the grant bodies are form encoded`() {
        assertEquals(
            "grant_type=authorization_code&code=a%2Fb&client_id=x",
            haCodeGrantBody(code = "a/b", clientId = "x"),
        )
        assertEquals(
            "grant_type=refresh_token&refresh_token=r%3D%3D&client_id=x",
            haRefreshGrantBody(refreshToken = "r==", clientId = "x"),
        )
        assertEquals("token=r%3D%3D", haRevokeBody("r=="))
    }

    @Test
    fun `the callback is read out of the request line`() {
        val query = haCallbackQuery("GET /cue-launcher/auth?code=abc123&state=s%2Bx+y HTTP/1.1")

        assertEquals("abc123", query[HA_CALLBACK_CODE])
        assertEquals("s+x y", query[HA_CALLBACK_STATE])
        assertNull(query[HA_CALLBACK_ERROR])
    }

    @Test
    fun `a request line without a query yields nothing`() {
        assertTrue(haCallbackQuery("GET /cue-launcher/auth HTTP/1.1").isEmpty())
        assertTrue(haCallbackQuery("").isEmpty())
    }

    @Test
    fun `two states are not the same`() {
        assertTrue(haAuthState() != haAuthState())
        assertTrue(haAuthState().isNotEmpty())
    }

    @Test
    fun `a session credential wins over a leftover long-lived token`() {
        val config = HaConfig(
            urlExternal = "https://ha.example.com",
            token = "long-lived",
            refreshToken = "refresh",
            oauthClientId = haClientId(5000),
        )

        assertTrue(config.credential is HaCredential.Oauth)
        assertTrue(config.isConfigured)
    }

    @Test
    fun `without a client id the session is not usable`() {
        val config = HaConfig(urlExternal = "https://ha.example.com", refreshToken = "refresh")

        assertEquals(HaCredential.None, config.credential)
    }
}
