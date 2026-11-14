package app.cue.launcher.system.ha

import app.cue.launcher.data.ha.HaConfig
import app.cue.launcher.data.ha.HaCredential
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

    suspend fun test(draft: HaConfig, saved: HaConfig): Map<HaEndpointKind, HaProbe> {
        val endpoints = haTestedEndpoints(draft)
        if (endpoints.isEmpty()) return emptyMap()
        val network = selector.network()
        val atHome = selector.atHome(draft, network)

        val results = haProbeEach(endpoints, draft, saved, atHome) { endpoint ->
            when (val bearer = access.bearer(draft, endpoint)) {
                is HaBearer.Token -> gateway.diagnose(endpoint, client.probe(endpoint, bearer.value), draft)
                else -> bearer.toFailureProbe()
            }
        }

        haWorkingKind(results)?.let { kind -> selector.remember(network, kind) }
        return results
    }
}

// An untried address says nothing about which one works here: remembering the other one as the
// network's last working endpoint would send every call there first until the next invalidate.
internal fun haWorkingKind(results: Map<HaEndpointKind, HaProbe>): HaEndpointKind? =
    if (results.values.any { it == HaProbe.Unsaved }) {
        null
    } else {
        results.entries.singleOrNull { (_, probe) -> probe is HaProbe.Ok }?.key
    }

internal fun haTestedEndpoints(config: HaConfig): List<HaEndpoint> = buildList {
    normalizeHaUrl(config.urlInternal).takeIf(String::isNotEmpty)
        ?.let { add(HaEndpoint(HaEndpointKind.HOME, it)) }
    normalizeHaUrl(config.urlExternal).takeIf(String::isNotEmpty)
        ?.let { add(HaEndpoint(HaEndpointKind.AWAY, it)) }
}

// Testing must obey the same rule as every other call: a cleartext home address off the home
// network is reported as untried, never probed. A test that leaked the token to a foreign LAN
// would undo exactly the protection it is supposed to confirm.
// The test runs on the draft, before Save, so a stored credential must not follow an address that
// only the picker or the keyboard put in a field: an mDNS record is anyone's to publish. A token
// typed into the field is the user's own choice for that address. A session's refresh happens
// inside send, so holding the endpoint back here keeps the refresh token home as well.
internal suspend fun haProbeEach(
    endpoints: List<HaEndpoint>,
    draft: HaConfig,
    saved: HaConfig,
    atHome: Boolean,
    send: suspend (HaEndpoint) -> HaProbe,
): Map<HaEndpointKind, HaProbe> {
    val savedUrls = haTestedEndpoints(saved).map(HaEndpoint::url).toSet()
    val storedCredential = draft.credential != HaCredential.None && draft.credential == saved.credential
    return endpoints.associate { endpoint ->
        endpoint.kind to when {
            endpoint.kind == HaEndpointKind.HOME && haIsCleartext(endpoint.url) && !atHome -> HaProbe.Blocked
            storedCredential && endpoint.url !in savedUrls -> HaProbe.Unsaved
            else -> send(endpoint)
        }
    }
}
