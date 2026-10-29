package app.cue.launcher.system.bridge

import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.engine.MacBridgeState
import app.cue.launcher.engine.MacLockSource
import app.cue.launcher.engine.MacLockState
import app.cue.launcher.engine.StateAspect
import app.cue.launcher.engine.StateProvider
import app.cue.launcher.engine.SystemState
import app.cue.launcher.engine.macLockStateOf
import app.cue.launcher.engine.reportedLocked
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

// Only consumes the presence the companion window (or DevPresence) already publishes and the
// lock-states that already came in, so it adds no receiver, callback or timer of its own. With the
// bridge off or unpaired the stored switch is all it reads: neither of the two is even constructed.
// A Lock Mac tile reads the same state as the mac_locked condition, so the two never disagree.
@SingleIn(AppScope::class)
@Inject
class BridgeConditions(
    private val settings: SettingsRepository,
    private val presence: Lazy<BridgePresence>,
    private val macLock: Lazy<ReportedMacLock>,
) : StateProvider, MacLockSource {

    override val aspect: StateAspect = StateAspect.MAC

    override fun observeLocked(): Flow<Boolean?> =
        observe().map { it.macLock.reportedLocked() }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<SystemState> =
        settings.settings
            .map { it.bridge.enabled && it.bridge.pairing != null }
            .distinctUntilChanged()
            .flatMapLatest { paired ->
                if (paired) {
                    combine(presence.value.endpoint(), macLock.value.reported()) { endpoint, locked ->
                        SystemState(
                            macBridge = if (endpoint != null) MacBridgeState.NEARBY else MacBridgeState.AWAY,
                            macLock = macLockStateOf(present = endpoint != null, locked = locked),
                        )
                    }
                } else {
                    flowOf(SystemState(macBridge = MacBridgeState.OFF, macLock = MacLockState.UNREPORTED))
                }
            }
            .distinctUntilChanged()
}
