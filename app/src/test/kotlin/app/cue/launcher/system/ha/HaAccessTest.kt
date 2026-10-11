package app.cue.launcher.system.ha

import app.cue.launcher.data.ha.HaConfig
import app.cue.launcher.data.ha.HaEndpoint
import app.cue.launcher.data.ha.HaEndpointKind
import app.cue.launcher.data.ha.HaSessionTokens
import app.cue.launcher.data.ha.HaTokenResult
import app.cue.launcher.data.ha.HaTokens
import app.cue.launcher.engine.WallClock
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// The access token of a signed-in session is the one credential the launcher holds in memory only, so
// what it does with it is worth pinning down: how long it keeps one, when it asks for a new one, and
// what it makes of a refusal.
class HaAccessTest {

    private class Tokens(private vararg val answers: HaTokenResult) : HaSessionTokens {

        val refreshed = mutableListOf<String>()

        val revoked = mutableListOf<String>()

        var revokedBy: String? = null

        override suspend fun refresh(
            endpoint: HaEndpoint,
            refreshToken: String,
            clientId: String,
        ): HaTokenResult {
            refreshed += refreshToken
            return answers.getOrElse(refreshed.size - 1) { answers.last() }
        }

        override suspend fun revoke(endpoint: HaEndpoint, refreshToken: String): Boolean {
            revoked += endpoint.url
            return endpoint.url == revokedBy
        }
    }

    private var now = 0L

    private val clock = WallClock { now }

    private fun granted(accessToken: String, expiresInSeconds: Long = SESSION_SECONDS) =
        HaTokenResult.Granted(HaTokens(accessToken, REFRESH_TOKEN, expiresInSeconds))

    private fun access(tokens: Tokens) = HaAccess(tokens, clock)

    @Test
    fun `a long-lived token is what it is, with nothing to fetch`() = runTest {
        val tokens = Tokens(granted("never-asked"))

        val bearer = access(tokens).bearer(longLived, away)

        assertEquals(HaBearer.Token("pasted-token"), bearer)
        assertTrue(tokens.refreshed.isEmpty())
    }

    @Test
    fun `without a credential there is no bearer`() = runTest {
        assertEquals(HaBearer.None, access(Tokens()).bearer(HaConfig(urlExternal = URL), away))
    }

    // Every call would otherwise cost a round trip to the token endpoint before the call it is for.
    @Test
    fun `an access token is fetched once and then reused`() = runTest {
        val tokens = Tokens(granted("first"), granted("second"))
        val access = access(tokens)

        assertEquals(HaBearer.Token("first"), access.bearer(session, away))
        now += SESSION_SECONDS * MILLIS / 2
        assertEquals(HaBearer.Token("first"), access.bearer(session, away))

        assertEquals(listOf(REFRESH_TOKEN), tokens.refreshed)
    }

    // Fetched again before it expires, not after: a token that dies in flight would read as a rejected
    // call, and the user would see a tap that did nothing.
    @Test
    fun `a token close to expiry is replaced before it is used`() = runTest {
        val tokens = Tokens(granted("first"), granted("second"))
        val access = access(tokens)

        assertEquals(HaBearer.Token("first"), access.bearer(session, away))
        now += (SESSION_SECONDS - EXPIRY_MARGIN_SECONDS) * MILLIS

        assertEquals(HaBearer.Token("second"), access.bearer(session, away))
        assertEquals(2, tokens.refreshed.size)
    }

    @Test
    fun `signing in again does not leave the token of the old session behind`() = runTest {
        val tokens = Tokens(granted("first"), granted("second"))
        val access = access(tokens)

        assertEquals(HaBearer.Token("first"), access.bearer(session, away))
        val signedInAgain = session.copy(refreshToken = "another-refresh-token")

        assertEquals(HaBearer.Token("second"), access.bearer(signedInAgain, away))
        assertEquals(listOf(REFRESH_TOKEN, "another-refresh-token"), tokens.refreshed)
    }

    @Test
    fun `a dropped cache asks for a new token even while the old one would still do`() = runTest {
        val tokens = Tokens(granted("first"), granted("second"))
        val access = access(tokens)

        assertEquals(HaBearer.Token("first"), access.bearer(session, away))
        access.invalidate()

        assertEquals(HaBearer.Token("second"), access.bearer(session, away))
    }

    // A refused session is not a network problem: only signing in again fixes it, and reporting it as
    // an unreachable instance would send the user looking for the wrong thing.
    @Test
    fun `a refused grant reads as unauthorized and an unreachable one as a network failure`() = runTest {
        assertEquals(HaBearer.Unauthorized, access(Tokens(HaTokenResult.Refused)).bearer(session, away))
        assertEquals(HaBearer.Unreachable, access(Tokens(HaTokenResult.Unreachable)).bearer(session, away))
    }

    @Test
    fun `a refusal is not cached, so the next call tries again`() = runTest {
        val tokens = Tokens(HaTokenResult.Unreachable, granted("second"))
        val access = access(tokens)

        assertEquals(HaBearer.Unreachable, access.bearer(session, away))
        assertEquals(HaBearer.Token("second"), access.bearer(session, away))
    }

    // Ending the session in Home Assistant too, so the row in the user's profile goes away with it.
    // The addresses are tried in order and stop at the one that answers.
    @Test
    fun `removing the connection revokes the session at the first address that answers`() = runTest {
        val tokens = Tokens(granted("first"))
        tokens.revokedBy = URL

        assertTrue(access(tokens).revoke(session, listOf(home, away)))

        assertEquals(listOf(HOME_URL, URL), tokens.revoked)
    }

    @Test
    fun `a long-lived token has no session to revoke`() = runTest {
        val tokens = Tokens(granted("first"))

        assertTrue(access(tokens).revoke(longLived, listOf(home, away)))

        assertTrue(tokens.revoked.isEmpty())
    }

    // The caller tells the user the session is still valid on the instance only from this answer.
    @Test
    fun `a session no address would end is reported as still valid`() = runTest {
        val tokens = Tokens(granted("first"))

        assertFalse(access(tokens).revoke(session, listOf(home, away)))
        assertFalse(access(tokens).revoke(session, emptyList()))
        assertEquals(listOf(HOME_URL, URL), tokens.revoked)
    }

    @Test
    fun `a refresh token stored without its client id is still revoked`() = runTest {
        val tokens = Tokens(granted("first"))
        tokens.revokedBy = URL

        assertTrue(access(tokens).revoke(session.copy(oauthClientId = ""), listOf(away)))
        assertEquals(listOf(URL), tokens.revoked)
    }

    private companion object {

        const val URL = "https://ha.example.com"

        const val HOME_URL = "http://homeassistant.local:8123"

        const val REFRESH_TOKEN = "refresh-token"

        const val SESSION_SECONDS = 1_800L

        const val EXPIRY_MARGIN_SECONDS = 60L

        const val MILLIS = 1_000L

        val away = HaEndpoint(HaEndpointKind.AWAY, URL)

        val home = HaEndpoint(HaEndpointKind.HOME, HOME_URL)

        val session = HaConfig(
            urlExternal = URL,
            refreshToken = REFRESH_TOKEN,
            oauthClientId = "http://127.0.0.1:1234/cue-launcher",
        )

        val longLived = HaConfig(urlExternal = URL, token = "pasted-token")
    }
}
