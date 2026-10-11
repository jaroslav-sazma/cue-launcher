package app.cue.launcher.data.ha

data class HaConfig(
    val urlInternal: String = "",
    val urlExternal: String = "",
    val token: String = "",

    val homeNetworks: List<String> = emptyList(),

    val refreshToken: String = "",
    val oauthClientId: String = "",

    val instanceUuid: String = "",
    val instanceName: String = "",
) {

    val credential: HaCredential
        get() = when {
            refreshToken.isNotBlank() && oauthClientId.isNotBlank() ->
                HaCredential.Oauth(refreshToken, oauthClientId)

            token.isNotBlank() -> HaCredential.LongLived(token)
            else -> HaCredential.None
        }

    val isConfigured: Boolean
        get() = credential != HaCredential.None &&
            (urlInternal.isNotBlank() || urlExternal.isNotBlank())

    val isPaired: Boolean get() = instanceUuid.isNotBlank()
}

sealed interface HaCredential {

    data class LongLived(val token: String) : HaCredential

    // The refresh token is what is stored; access tokens live 30 minutes and are fetched on demand,
    // so an intercepted one expires on its own and the user can revoke the session in Home Assistant.
    data class Oauth(val refreshToken: String, val clientId: String) : HaCredential

    data object None : HaCredential
}

enum class HaUrlError {
    SCHEME,
    HOST,

    INSECURE_EXTERNAL,

    // http:// pointing at a public host: being at home cannot protect an address that leaves the LAN.
    INSECURE_HOME,
}

const val HA_BSSID_PREFIX = "BSSID:"

// A cleartext home address carries a bearer token, so it may only be contacted on a network the user
// has named as home or where the paired instance itself answers on mDNS. With neither there is
// nothing to compare against and the address would be tried on foreign Wi-Fi as well.
fun haHomeNetworkMissing(
    urlInternal: String,
    homeNetworks: List<String>,
    paired: Boolean = false,
): Boolean = urlInternal.isNotBlank() &&
    haIsCleartext(urlInternal) &&
    homeNetworks.isEmpty() &&
    !paired

fun haHomeNetworksOf(raw: String): List<String> = raw
    .split(',', '\n')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinctBy { it.lowercase() }

fun haHomeNetworkMatches(entries: List<String>, ssid: String?, bssid: String?): Boolean =
    entries.any { entry ->
        val trimmed = entry.trim()
        when {
            // The prefix is recognised however it was typed, so dropping it has to go by length —
            // removePrefix is case-sensitive and would leave "bssid:" sitting in front of the address.
            trimmed.startsWith(HA_BSSID_PREFIX, ignoreCase = true) ->
                bssid != null && trimmed.drop(HA_BSSID_PREFIX.length).trim().equals(bssid, ignoreCase = true)

            else -> ssid != null && trimmed.equals(ssid, ignoreCase = true)
        }
    }
