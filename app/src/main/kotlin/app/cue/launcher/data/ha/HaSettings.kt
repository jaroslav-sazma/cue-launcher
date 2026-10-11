package app.cue.launcher.data.ha

import kotlinx.serialization.Serializable

@Serializable
data class HaSettings(
    val urlInternal: String = "",
    val urlExternal: String = "",
    val token: HaSealedToken? = null,
    val homeNetworks: List<String> = emptyList(),

    // Not sealed like the token: it authorizes nothing outside this device, it has to be shown to
    // the user to be copied into the blueprint, and a keystore that failed to open would silently
    // stop every event instead of asking for the value again.
    val eventSecret: String? = null,

    // The refresh token replaces the long-lived token when the user signs in. Sealed the same way —
    // it is the one long-lived secret of that flow.
    val refreshToken: HaSealedToken? = null,
    val oauthClientId: String? = null,

    // The instance the user approved in the system picker. The uuid identifies it on mDNS, which is
    // how "am I home?" is answered without the location permission.
    val instanceUuid: String? = null,
    val instanceName: String? = null,
)

@Serializable
data class HaSealedToken(val iv: String, val data: String)

fun HaSettings.toConfig(openToken: (HaSealedToken) -> String?): HaConfig = HaConfig(
    urlInternal = urlInternal,
    urlExternal = urlExternal,
    token = token?.let(openToken).orEmpty(),
    homeNetworks = homeNetworks,
    refreshToken = refreshToken?.let(openToken).orEmpty(),
    oauthClientId = oauthClientId.orEmpty(),
    instanceUuid = instanceUuid.orEmpty(),
    instanceName = instanceName.orEmpty(),
)
