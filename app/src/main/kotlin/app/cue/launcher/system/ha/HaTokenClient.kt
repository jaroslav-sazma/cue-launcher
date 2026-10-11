package app.cue.launcher.system.ha

import android.util.Log
import app.cue.launcher.data.ha.HA_REVOKE_PATH
import app.cue.launcher.data.ha.HA_TOKEN_PATH
import app.cue.launcher.data.ha.HaEndpoint
import app.cue.launcher.data.ha.HaSessionTokens
import app.cue.launcher.data.ha.HaTokenResult
import app.cue.launcher.data.ha.HaTokens
import app.cue.launcher.data.ha.haCodeGrantBody
import app.cue.launcher.data.ha.haRefreshGrantBody
import app.cue.launcher.data.ha.haRevokeBody
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

// The OAuth end of the connection: a signed-in session trades its refresh token for a 30 minute access
// token here, and ending the session revokes it in Home Assistant.
@SingleIn(AppScope::class)
@Inject
class HaTokenClient(private val http: HaHttp) : HaSessionTokens {

    suspend fun exchangeCode(endpoint: HaEndpoint, code: String, clientId: String): HaTokenResult =
        tokensOf(endpoint, haCodeGrantBody(code, clientId))

    // The refresh grant answers with an access token only, so the refresh token the caller already
    // holds is carried over.
    override suspend fun refresh(
        endpoint: HaEndpoint,
        refreshToken: String,
        clientId: String,
    ): HaTokenResult = when (val result = tokensOf(endpoint, haRefreshGrantBody(refreshToken, clientId))) {
        is HaTokenResult.Granted -> HaTokenResult.Granted(result.tokens.copy(refreshToken = refreshToken))
        else -> result
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun revoke(endpoint: HaEndpoint, refreshToken: String): Boolean = try {
        form(endpoint.url + HA_REVOKE_PATH, haRevokeBody(refreshToken)).status.isSuccess()
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        Log.i(TAG, "Session was not revoked at ${endpoint.kind} (${cause::class.java.simpleName})")
        false
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun tokensOf(endpoint: HaEndpoint, body: String): HaTokenResult = try {
        val response = form(endpoint.url + HA_TOKEN_PATH, body)
        val tokens = if (response.status.isSuccess()) tokensFrom(response.bodyAsText()) else null
        tokens?.let(HaTokenResult::Granted) ?: refused(response)
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        Log.i(TAG, "Token exchange failed at ${endpoint.kind} (${cause::class.java.simpleName})")
        HaTokenResult.Unreachable
    }

    private fun refused(response: HttpResponse): HaTokenResult {
        Log.w(TAG, "Token endpoint refused the grant: ${response.status.value}")
        return HaTokenResult.Refused
    }

    // The token endpoint gets more time than the rest: the code is traded the moment the user comes
    // back from the browser, which is exactly when the process has just been unfrozen and the radio is
    // waking up. On the usual two-second budget that first connection can miss, and the sign-in would
    // report a refusal for what was only a slow start.
    private suspend fun form(url: String, body: String): HttpResponse = http.client.post(url) {
        contentType(ContentType.Application.FormUrlEncoded)
        timeout {
            connectTimeoutMillis = HaHttp.AUTH_CONNECT_TIMEOUT_MILLIS
            requestTimeoutMillis = HaHttp.AUTH_TIMEOUT_MILLIS
            socketTimeoutMillis = HaHttp.AUTH_TIMEOUT_MILLIS
        }

        setBody(body)
    }

    private fun tokensFrom(body: String): HaTokens? {
        val payload = runCatching { http.json.parseToJsonElement(body).jsonObject }.getOrNull()
        val access = payload?.get(ACCESS_TOKEN_FIELD)?.jsonPrimitive?.contentOrNull
        return access?.let {
            HaTokens(
                accessToken = it,
                refreshToken = refreshTokenOf(payload),
                expiresInSeconds = payload[EXPIRES_IN_FIELD]?.jsonPrimitive?.longOrNull
                    ?: DEFAULT_EXPIRY_SECONDS,
            )
        }
    }

    private fun refreshTokenOf(payload: JsonObject): String =
        payload[REFRESH_TOKEN_FIELD]?.jsonPrimitive?.contentOrNull.orEmpty()

    private companion object {

        const val DEFAULT_EXPIRY_SECONDS = 1_800L

        const val ACCESS_TOKEN_FIELD = "access_token"

        const val REFRESH_TOKEN_FIELD = "refresh_token"

        const val EXPIRES_IN_FIELD = "expires_in"

        const val TAG = "HaTokens"
    }
}
