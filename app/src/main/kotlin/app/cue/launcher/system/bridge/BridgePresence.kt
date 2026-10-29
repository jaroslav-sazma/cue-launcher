package app.cue.launcher.system.bridge

import app.cue.launcher.BuildConfig
import app.cue.launcher.data.bridge.BridgeConfigRepository
import app.cue.launcher.data.bridge.BridgeSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class BridgeEndpoint(val host: String, val port: Int)

// Where the paired Mac can be reached right now, or null while it is not around.
interface BridgePresence {

    fun endpoint(): Flow<BridgeEndpoint?>
}

// The emulator has no Bluetooth, so in debug builds the Mac counts as present whenever an endpoint
// is configured, and its signals travel inside the session instead of over GATT.
@SingleIn(AppScope::class)
@Inject
class DevPresence(private val config: BridgeConfigRepository) : BridgePresence {

    override fun endpoint(): Flow<BridgeEndpoint?> = if (!BuildConfig.DEBUG) {
        flowOf(null)
    } else {
        config.settings.map { settings ->
            settings.devEndpoint
                ?.takeIf { settings.enabled && settings.pairing != null }
                ?.let { BridgeEndpoint(it.host, it.port) }
        }.distinctUntilChanged()
    }
}

enum class MacPresence { ABSENT, NEARBY, LINKED }

// Written only by the companion window while the system keeps it bound, and ABSENT otherwise.
@SingleIn(AppScope::class)
@Inject
class BridgeNearby {

    private val current = MutableStateFlow(MacPresence.ABSENT)

    val state: StateFlow<MacPresence> = current.asStateFlow()

    internal fun publish(presence: MacPresence) {
        current.value = presence
    }
}

// The endpoint saved at pairing, for as long as the debounced presence has the Mac nearby.
@SingleIn(AppScope::class)
@Inject
class CdmPresence(
    private val config: BridgeConfigRepository,
    private val nearby: BridgeNearby,
) : BridgePresence {

    override fun endpoint(): Flow<BridgeEndpoint?> =
        combine(nearby.state, config.settings) { presence, settings ->
            settings.pairing
                ?.takeIf { settings.enabled && presence != MacPresence.ABSENT }
                ?.let { BridgeEndpoint(it.host, it.port) }
        }.distinctUntilChanged()
}

internal fun usesDevPresence(settings: BridgeSettings): Boolean = BuildConfig.DEBUG && settings.devEndpoint != null

// A debug pairing made over the dev endpoint has no association to be present through.
@SingleIn(AppScope::class)
@Inject
class SelectedPresence(
    private val config: BridgeConfigRepository,
    private val dev: DevPresence,
    private val cdm: CdmPresence,
) : BridgePresence {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun endpoint(): Flow<BridgeEndpoint?> =
        config.settings.map(::usesDevPresence)
            .distinctUntilChanged()
            .flatMapLatest { useDev -> if (useDev) dev.endpoint() else cdm.endpoint() }
}
