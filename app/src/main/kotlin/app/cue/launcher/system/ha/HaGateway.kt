package app.cue.launcher.system.ha

import app.cue.launcher.data.ha.HaConfig
import app.cue.launcher.data.ha.HaConfigRepository
import app.cue.launcher.data.ha.HaEndpoint
import app.cue.launcher.data.ha.HaEntityList
import app.cue.launcher.data.ha.HaIconTable
import app.cue.launcher.data.ha.HaProbe
import app.cue.launcher.data.ha.HaServiceCall
import app.cue.launcher.data.ha.HaServiceList
import app.cue.launcher.data.ha.HaServiceNamesCache
import app.cue.launcher.data.ha.HaStateRead
import app.cue.launcher.data.ha.haAttempt
import app.cue.launcher.data.ha.haDiagnose
import app.cue.launcher.data.ha.haIconInstance
import app.cue.launcher.data.ha.haInstanceKey
import app.cue.launcher.data.ha.haUrlIsLocal
import app.cue.launcher.system.BiometricGate
import app.cue.launcher.system.ConfirmationResult
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Suppress("LongParameterList")
@SingleIn(AppScope::class)
@Inject
class HaGateway(
    private val configs: HaConfigRepository,
    private val selector: HaEndpointSelector,
    private val client: HaClient,
    private val stateClient: HaStateClient,
    private val serviceClient: HaServiceClient,
    private val iconClient: HaIconClient,
    private val access: HaAccess,
    private val discovery: HaDiscovery,
    private val gate: BiometricGate,
) {

    private val names = HaServiceNamesCache()

    // The builder reaches Home Assistant through this class alone, and its Test button has to hold an
    // action marked for confirmation behind the same prompt a tap goes through.
    suspend fun confirm(): ConfirmationResult = gate.confirm()

    suspend fun callService(call: HaServiceCall): HaProbe =
        attempt(probeOf = { it }, failed = { it }) { endpoint, token ->
            client.callService(endpoint, token, call)
        }

    suspend fun states(entities: Set<String>): HaStateRead {
        if (entities.isEmpty()) return HaStateRead.NONE
        return attempt(probeOf = HaStateRead::probe, failed = ::HaStateRead) { endpoint, token ->
            stateClient.states(endpoint, token, entities)
        }
    }

    // One full GET /api/states, on demand only: nothing caches or refreshes it, a caller that wants
    // it again asks again.
    suspend fun entities(): HaEntityList =
        attempt(probeOf = HaEntityList::probe, failed = ::HaEntityList) { endpoint, token ->
            client.entities(endpoint, token)
        }

    suspend fun services(): HaServiceList =
        attempt(probeOf = HaServiceList::probe, failed = ::HaServiceList) { endpoint, token ->
            serviceClient.services(endpoint, token)
        }

    // Only labels, so one try on the address the last read succeeded on is enough; anything else
    // leaves the English names standing. domains are those of the list the names are for.
    suspend fun serviceNames(language: String, domains: Set<String>): Map<String, String> {
        val config = configs.config().takeIf(HaConfig::isConfigured) ?: return emptyMap()
        return names.names(haInstanceKey(config), language, domains) {
            val endpoint = selector.endpoints(config, selector.network()).firstOrNull()
            val token = endpoint?.let { access.bearer(config, it) as? HaBearer.Token }
            when {
                endpoint == null || token == null -> emptyMap()
                else -> serviceClient.names(endpoint, token.value, language)
            }
        }
    }

    // Read once per version of the instance, so one try on the first address is enough, as for the
    // names; a table that did not come stays as it was.
    suspend fun icons(domains: Set<String>): HaIconTable? {
        val config = configs.config().takeIf(HaConfig::isConfigured) ?: return null
        val endpoint = selector.endpoints(config, selector.network()).firstOrNull()
        val token = endpoint?.let { access.bearer(config, it) as? HaBearer.Token }
        val icons = if (endpoint != null && token != null) iconClient.icons(endpoint, token.value, domains) else null
        return icons?.let { HaIconTable(haIconInstance(config), it.version, it.domains, asked = domains) }
    }

    // The live channel runs only at home. Away, every pass across the home screen would otherwise
    // open a fresh TLS socket to the outside address and hold it with a heartbeat — radio the poll
    // fallback does not spend, for a tile whose staleness matters least exactly there. Returning
    // null here is what sends the reader down that poll path.
    suspend fun <R> live(block: suspend (HaEndpoint, String) -> R): R? {
        val config = configs.config().takeIf(HaConfig::isConfigured) ?: return null
        val network = selector.network()
        val endpoint = when {
            !selector.atHome(config, network) -> null
            else -> selector.endpoints(config, network).firstOrNull()
        }
        val token = endpoint?.let { access.bearer(config, it) as? HaBearer.Token }
        return if (endpoint != null && token != null) block(endpoint, token.value) else null
    }

    fun diagnose(endpoint: HaEndpoint, probe: HaProbe, config: HaConfig): HaProbe =
        if (probe !is HaProbe.Network) {
            probe
        } else {
            haDiagnose(
                probe = probe,
                local = haUrlIsLocal(endpoint.url),
                paired = config.isPaired,
                granted = discovery::localNetworkGranted,
            )
        }

    private suspend fun <R> attempt(
        probeOf: (R) -> HaProbe,
        failed: (HaProbe) -> R,
        call: suspend (HaEndpoint, String) -> R,
    ): R {
        val config = configs.config()
        if (!config.isConfigured) return failed(HaProbe.NotConfigured)
        val network = selector.network()
        val endpoints = selector.endpoints(config, network)
        val attempt = haAttempt(endpoints = endpoints, fallback = failed(HaProbe.NotConfigured), probeOf = probeOf) {
            endpoint ->
            when (val bearer = access.bearer(config, endpoint)) {
                is HaBearer.Token -> {
                    val result = call(endpoint, bearer.value)
                    val probe = diagnose(endpoint, probeOf(result), config)
                    if (probe == probeOf(result)) result else failed(probe)
                }

                else -> failed(bearer.toFailureProbe())
            }
        }
        if (attempt.succeeded) {
            attempt.endpoint?.let { selector.remember(network, it.kind) }
        }
        return attempt.result
    }
}
