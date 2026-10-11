package app.cue.launcher.data.ha

import java.net.InetAddress
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64

data class HaTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
)

sealed interface HaTokenResult {

    data class Granted(val tokens: HaTokens) : HaTokenResult

    // Home Assistant answered and said no: the code was reused, or the session was revoked in the
    // user's profile. Retrying another address cannot help, only signing in again.
    data object Refused : HaTokenResult

    data object Unreachable : HaTokenResult
}

// The OAuth end of the connection, as the rest of the launcher needs it: what turns a stored refresh
// token into a usable access token, and what ends the session on the instance.
interface HaSessionTokens {

    suspend fun refresh(endpoint: HaEndpoint, refreshToken: String, clientId: String): HaTokenResult

    suspend fun revoke(endpoint: HaEndpoint, refreshToken: String): Boolean
}

sealed interface HaSignIn {

    data class Done(val tokens: HaTokens, val clientId: String) : HaSignIn

    data object Cancelled : HaSignIn

    data class Failed(val detail: String?) : HaSignIn
}

// Home Assistant only accepts a redirect_uri that shares scheme and host with the client_id, unless
// the client_id is a web page advertising the redirect in a link tag. A loopback client_id needs no
// page anywhere: it is the native-app pattern from RFC 8252 and works on an instance with no internet
// access at all. Home Assistant lists this string next to the session in the user's profile, so the
// path spells the launcher out.
fun haClientId(port: Int): String = "http://127.0.0.1:$port/cue-launcher"

// The socket has to answer on the very host the client id names. InetAddress.getLoopbackAddress() is
// ::1 on a device that prefers IPv6, and the browser following the redirect to 127.0.0.1 then hits a
// refused connection instead of the callback.
val HA_CALLBACK_ADDRESS: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

fun haRedirectUri(port: Int): String = "${haClientId(port)}/auth"

fun haAuthorizeUrl(base: String, clientId: String, redirectUri: String, state: String): String =
    normalizeHaUrl(base) + HA_AUTHORIZE_PATH +
        "?response_type=code" +
        "&client_id=" + encode(clientId) +
        "&redirect_uri=" + encode(redirectUri) +
        "&state=" + encode(state)

fun haCodeGrantBody(code: String, clientId: String): String =
    "grant_type=authorization_code&code=" + encode(code) + "&client_id=" + encode(clientId)

fun haRefreshGrantBody(refreshToken: String, clientId: String): String =
    "grant_type=refresh_token&refresh_token=" + encode(refreshToken) + "&client_id=" + encode(clientId)

fun haRevokeBody(refreshToken: String): String = "token=" + encode(refreshToken)

fun haAuthState(random: SecureRandom = SecureRandom()): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(STATE_BYTES).also(random::nextBytes))

// The redirect arrives as the request line of a plain GET on the loopback socket.
fun haCallbackQuery(requestLine: String): Map<String, String> {
    val query = requestLine.split(' ').getOrNull(1)?.substringAfter('?', "").orEmpty()
    return query.split('&').mapNotNull { pair ->
        val name = pair.substringBefore('=').takeIf(String::isNotEmpty)
        name?.let { it to decode(pair.substringAfter('=', "")) }
    }.toMap()
}

const val HA_CALLBACK_CODE = "code"

const val HA_CALLBACK_STATE = "state"

const val HA_CALLBACK_ERROR = "error"

private const val STATE_BYTES = 16

private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

private fun decode(value: String): String =
    runCatching { java.net.URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
