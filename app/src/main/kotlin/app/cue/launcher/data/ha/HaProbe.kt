package app.cue.launcher.data.ha

import java.net.ConnectException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

sealed interface HaProbe {

    data class Ok(val entities: Int, val millis: Long) : HaProbe

    data class Http(val status: Int) : HaProbe

    data class Network(val detail: String?) : HaProbe

    // A request that went out but was not answered in time. A service call runs to completion before
    // Home Assistant replies, so a timeout is not proof that nothing happened — it must never be
    // retried on the other endpoint.
    data object Timeout : HaProbe

    // A cleartext home address off the home network is not contacted at all.
    data object Blocked : HaProbe

    // Android 17 gates every connection to a local address behind ACCESS_LOCAL_NETWORK. Without it
    // the socket dies in a connect timeout, which on its own reads like an unreachable instance.
    data object LocalNetworkDenied : HaProbe

    data object NotConfigured : HaProbe
}

fun haFallbackAllowed(probe: HaProbe): Boolean = when (probe) {
    is HaProbe.Network, HaProbe.Blocked, HaProbe.LocalNetworkDenied -> true
    else -> false
}

// Whether the request never made it out of the phone. Reading a state may be repeated on the other
// address whatever went wrong, but a service call may not: Home Assistant runs one to completion
// before it answers, so a failure that arrived after the request had left could be a gate that is
// already open. Only a connection that was never established says nothing happened.
fun haNeverSent(cause: Throwable): Boolean =
    cause is ConnectException || cause is UnknownHostException || cause is SSLException

// Android 17 answers a blocked local connection with a connect timeout, which on its own reads like an
// instance that is switched off. Everything needed to tell the two apart is here. Whether the permission
// is held is asked last, and only when it could still change the answer: an instance approved in the
// system picker is reached without it, so there is nothing to report to the user in that case.
fun haDiagnose(
    probe: HaProbe,
    local: Boolean,
    paired: Boolean,
    granted: () -> Boolean,
): HaProbe = when {
    probe !is HaProbe.Network -> probe
    !local || paired -> probe
    granted() -> probe
    else -> HaProbe.LocalNetworkDenied
}

fun haAuthorizationHeader(token: String): String = "Bearer ${token.trim()}"

const val HA_API_PATH = "/api/"

const val HA_STATES_PATH = "/api/states"

const val HA_SERVICES_PATH = "/api/services"

const val HA_WEBSOCKET_PATH = "/api/websocket"

const val HA_AUTHORIZE_PATH = "/auth/authorize"

const val HA_TOKEN_PATH = "/auth/token"

const val HA_REVOKE_PATH = "/auth/revoke"
