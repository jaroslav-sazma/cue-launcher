package app.cue.launcher.system.ha

import app.cue.launcher.data.ha.HaConfig
import app.cue.launcher.data.ha.HaCredential
import app.cue.launcher.data.ha.HaEndpoint
import app.cue.launcher.data.ha.HaProbe
import app.cue.launcher.data.ha.HaSessionTokens
import app.cue.launcher.data.ha.HaTokenResult
import app.cue.launcher.engine.WallClock
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface HaBearer {

    data class Token(val value: String) : HaBearer

    // The session exists but Home Assistant refused it — revoked in the profile, or the instance was
    // rebuilt. Only signing in again fixes it, so it must not read as a network problem.
    data object Unauthorized : HaBearer

    data object Unreachable : HaBearer

    data object None : HaBearer
}

// Token carries no failure; callers branch on it before mapping the rest.
fun HaBearer.toFailureProbe(): HaProbe = when (this) {
    is HaBearer.Token -> error("Token is not a failure")
    HaBearer.Unauthorized -> HaProbe.Http(HTTP_UNAUTHORIZED)
    HaBearer.Unreachable -> HaProbe.Network(null)
    HaBearer.None -> HaProbe.NotConfigured
}

internal const val HTTP_UNAUTHORIZED = 401

// A long-lived token is used as it is; a signed-in session trades its refresh token for a 30 minute
// access token and keeps it in memory only, so nothing long-lived travels on every call.
@SingleIn(AppScope::class)
@Inject
class HaAccess(
    private val tokens: HaSessionTokens,
    private val clock: WallClock,
) {

    private val lock = Mutex()

    // Written under the lock, but dropped from wherever the connection changed, so the next reader has
    // to see that rather than a token the user has just revoked.
    @Volatile
    private var cached: Cached? = null

    suspend fun bearer(config: HaConfig, endpoint: HaEndpoint): HaBearer =
        when (val credential = config.credential) {
            is HaCredential.LongLived -> HaBearer.Token(credential.token)
            is HaCredential.Oauth -> access(credential, endpoint)
            HaCredential.None -> HaBearer.None
        }

    // True when nothing is left valid on the instance: the session was ended there, or there was none.
    // Revoking needs the refresh token alone, so one stored without its client id is ended as well.
    suspend fun revoke(config: HaConfig, endpoints: List<HaEndpoint>): Boolean {
        val refreshToken = config.refreshToken.takeIf(String::isNotBlank) ?: return true
        invalidate()
        return endpoints.any { endpoint -> tokens.revoke(endpoint, refreshToken) }
    }

    fun invalidate() {
        cached = null
    }

    private suspend fun access(credential: HaCredential.Oauth, endpoint: HaEndpoint): HaBearer = lock.withLock {
        val now = clock.nowMillis()
        val valid = cached?.takeIf { it.refreshToken == credential.refreshToken && now < it.expiresAt }
        if (valid != null) return@withLock HaBearer.Token(valid.accessToken)
        when (val result = tokens.refresh(endpoint, credential.refreshToken, credential.clientId)) {
            is HaTokenResult.Granted -> {
                cached = Cached(
                    refreshToken = credential.refreshToken,
                    accessToken = result.tokens.accessToken,
                    expiresAt = now + margin(result.tokens.expiresInSeconds),
                )
                HaBearer.Token(result.tokens.accessToken)
            }

            HaTokenResult.Refused -> HaBearer.Unauthorized
            HaTokenResult.Unreachable -> HaBearer.Unreachable
        }
    }

    private fun margin(expiresInSeconds: Long): Long =
        (expiresInSeconds - EXPIRY_MARGIN_SECONDS).coerceAtLeast(0) * MILLIS

    private data class Cached(
        val refreshToken: String,
        val accessToken: String,
        val expiresAt: Long,
    )

    private companion object {

        const val EXPIRY_MARGIN_SECONDS = 60L

        const val MILLIS = 1_000L
    }
}
