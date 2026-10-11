package app.cue.launcher.data.ha

enum class HaEndpointKind {
    HOME,
    AWAY,
}

data class HaEndpoint(val kind: HaEndpointKind, val url: String)

data class HaNetwork(
    val connected: Boolean = false,
    val ssid: String? = null,
    val bssid: String? = null,
)

fun haNetworkKey(network: HaNetwork?): String? =
    network?.ssid?.takeIf { network.connected }?.lowercase()

fun haEndpointOrder(
    config: HaConfig,
    network: HaNetwork?,
    lastWorking: HaEndpointKind?,
): List<HaEndpoint> = haEndpointOrder(
    config = config,
    atHome = haHomeNetworkMatches(config.homeNetworks, network?.ssid, network?.bssid),
    lastWorking = lastWorking,
)

fun haEndpointOrder(
    config: HaConfig,
    atHome: Boolean,
    lastWorking: HaEndpointKind?,
): List<HaEndpoint> {
    val configured = config.urlInternal.let(::normalizeHaUrl)
        .takeIf(String::isNotEmpty)
        ?.let { HaEndpoint(HaEndpointKind.HOME, it) }
    val away = config.urlExternal.let(::normalizeHaUrl)
        .takeIf(String::isNotEmpty)
        ?.let { HaEndpoint(HaEndpointKind.AWAY, it) }

    // A cleartext home address is dropped outright off the home network — lastWorking must not be
    // able to resurrect it, or the token would travel in the clear to whatever answers that name on
    // a foreign LAN. An https home address needs no such gate; TLS authenticates the host.
    val home = configured?.takeIf { atHome || !haIsCleartext(it.url) }
    if (home == null || away == null) return listOfNotNull(home, away)

    val first = when {
        lastWorking == HaEndpointKind.HOME -> home
        lastWorking == HaEndpointKind.AWAY -> away
        atHome -> home
        else -> away
    }
    return listOf(first, if (first == home) away else home)
}

data class HaAttempt<R>(
    val endpoint: HaEndpoint?,
    val result: R,
    val succeeded: Boolean = false,
)

suspend fun <R> haAttempt(
    endpoints: List<HaEndpoint>,
    fallback: R,
    probeOf: (R) -> HaProbe,
    call: suspend (HaEndpoint) -> R,
): HaAttempt<R> {
    var last = HaAttempt(endpoint = null, result = fallback)
    for (endpoint in endpoints) {
        val result = call(endpoint)
        val probe = probeOf(result)
        last = HaAttempt(endpoint, result, succeeded = probe is HaProbe.Ok)
        if (last.succeeded || !haFallbackAllowed(probe)) return last
    }
    return last
}
