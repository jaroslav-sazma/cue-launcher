package app.cue.launcher.system.ha

import app.cue.launcher.data.ha.HaConfig
import app.cue.launcher.data.ha.HaEndpoint
import app.cue.launcher.data.ha.HaEndpointKind
import app.cue.launcher.data.ha.HaProbe
import app.cue.launcher.data.ha.haIsCleartext
import app.cue.launcher.data.ha.normalizeHaUrl
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@Inject
class HaConnectionTester(
    private val client: HaClient,
    private val selector: HaEndpointSelector,
    private val access: HaAccess,
    private val gateway: HaGateway,
) {

    suspend fun test(config: HaConfig): Map<HaEndpointKind, HaProbe> {
        val endpoints = buildList {
            normalizeHaUrl(config.urlInternal).takeIf(String::isNotEmpty)
                ?.let { add(HaEndpoint(HaEndpointKind.HOME, it)) }
            normalizeHaUrl(config.urlExternal).takeIf(String::isNotEmpty)
                ?.let { add(HaEndpoint(HaEndpointKind.AWAY, it)) }
        }
        if (endpoints.isEmpty()) return emptyMap()
        val network = selector.network()
        val atHome = selector.atHome(config, network)

        val results = endpoints.associate { endpoint -> endpoint.kind to probe(endpoint, config, atHome) }

        results.entries.singleOrNull { (_, probe) -> probe is HaProbe.Ok }
            ?.let { (kind, _) -> selector.remember(network, kind) }
        return results
    }

    // Testing must obey the same rule as every other call: a cleartext home address off the home
    // network is reported as untried, never probed. A test that leaked the token to a foreign LAN
    // would undo exactly the protection it is supposed to confirm.
    private suspend fun probe(endpoint: HaEndpoint, config: HaConfig, atHome: Boolean): HaProbe {
        if (endpoint.kind == HaEndpointKind.HOME && haIsCleartext(endpoint.url) && !atHome) {
            return HaProbe.Blocked
        }
        return when (val bearer = access.bearer(config, endpoint)) {
            is HaBearer.Token -> gateway.diagnose(endpoint, client.probe(endpoint, bearer.value), config)
            else -> bearer.toFailureProbe()
        }
    }
}
