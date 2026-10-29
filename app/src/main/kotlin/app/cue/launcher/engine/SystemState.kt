package app.cue.launcher.engine

import app.cue.launcher.data.ha.haEntityIdOrNull
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.RingerModeState
import app.cue.launcher.data.scenario.Scenario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class SystemState(

    val clock: ClockState? = null,

    val charging: Boolean? = null,

    val audioOutput: Boolean? = null,

    val bluetooth: BluetoothState? = null,

    val wifi: WifiState? = null,

    val ringerMode: RingerModeState? = null,

    val doNotDisturb: Boolean? = null,

    val media: MediaActivity? = null,

    val calendarEvent: Boolean? = null,

    val ha: HaStates? = null,

    val macBridge: MacBridgeState? = null,

    val macLock: MacLockState? = null,
) {

    fun mergedWith(patch: SystemState): SystemState = SystemState(
        clock = patch.clock ?: clock,
        charging = patch.charging ?: charging,
        audioOutput = patch.audioOutput ?: audioOutput,
        bluetooth = patch.bluetooth ?: bluetooth,
        wifi = patch.wifi ?: wifi,
        ringerMode = patch.ringerMode ?: ringerMode,
        doNotDisturb = patch.doNotDisturb ?: doNotDisturb,
        media = patch.media ?: media,
        calendarEvent = patch.calendarEvent ?: calendarEvent,
        ha = patch.ha ?: ha,
        macBridge = patch.macBridge ?: macBridge,
        macLock = patch.macLock ?: macLock,
    )
}

// OFF is the bridge switched off or no Mac paired, where the Mac never counts as nearby.
enum class MacBridgeState { OFF, AWAY, NEARBY }

// UNREPORTED is everything but a present Mac's own word: the bridge off, the Mac away, or no
// lock-state from it yet. The Mac counts as locked only on LOCKED and as unlocked only on UNLOCKED;
// UNREPORTED is unknown to both.
enum class MacLockState { LOCKED, UNLOCKED, UNREPORTED }

// Entity id to its state, trimmed and in lower case. An entity that is missing — Home Assistant not
// connected or out of reach, the entity unavailable or not read yet — has no state to compare.
data class HaStates(val states: Map<String, String> = emptyMap()) {

    fun stateOf(entity: String): String? = haEntityIdOrNull(entity)?.let(states::get)

    fun isIn(entity: String, wanted: List<String>): Boolean? =
        stateOf(entity)?.let { state -> wanted.any { it.trim().equals(state, ignoreCase = true) } }

    companion object {
        fun of(states: Map<String, String>): HaStates =
            HaStates(states.entries.associate { (entity, state) -> entity to state.trim().lowercase() })
    }
}

data class ClockState(

    val isoDayOfWeek: Int,

    val minuteOfDay: Int,
)

data class BluetoothState(val connectedMacs: Set<String> = emptySet()) {

    fun isConnected(mac: String?): Boolean = when (mac) {
        null -> connectedMacs.isNotEmpty()
        else -> connectedMacs.any { it.equals(mac, ignoreCase = true) }
    }
}

data class WifiState(
    val connected: Boolean,
    val ssid: String? = null,
    val bssid: String? = null,
)

enum class StateAspect { CLOCK, POWER, AUDIO, BLUETOOTH, WIFI, RINGER, CALENDAR, MEDIA, HA, MAC }

fun Condition.StateCondition.aspect(): StateAspect = when (this) {
    is Condition.TimeWindow -> StateAspect.CLOCK
    is Condition.Charging -> StateAspect.POWER
    is Condition.AudioOutput -> StateAspect.AUDIO
    is Condition.BtConnected -> StateAspect.BLUETOOTH
    is Condition.Wifi -> StateAspect.WIFI
    is Condition.RingerMode -> StateAspect.RINGER
    is Condition.CalendarEvent -> StateAspect.CALENDAR
    is Condition.MediaPlaying -> StateAspect.MEDIA
    is Condition.HaState -> StateAspect.HA
    is Condition.MacNearby -> StateAspect.MAC
    is Condition.MacLocked -> StateAspect.MAC
}

fun stateAspectsOf(scenarios: List<Scenario>): Set<StateAspect> =
    scenarios.flatMap(Scenario::stateConditions).mapTo(mutableSetOf()) { it.aspect() }

// What an event's gates read: the aspects, and of Home Assistant only the entities those gates name.
data class StateNeeds(val aspects: Set<StateAspect>, val haEntities: Set<String> = emptySet())

fun stateNeedsOf(scenarios: List<Scenario>): StateNeeds =
    StateNeeds(aspects = stateAspectsOf(scenarios), haEntities = haGateEntities(scenarios))

fun interface SystemStateSource {

    fun observeState(): Flow<SystemState>

    // Only the signals the given aspects need. Measuring every signal for an event would register a
    // network callback, query the calendar and subscribe to the media sessions on each post of a
    // notification that updates every second.
    fun observeState(aspects: Set<StateAspect>): Flow<SystemState> = observeState()

    // One reading, for an event.
    suspend fun measure(needs: StateNeeds): SystemState = observeState(needs.aspects).first()
}

interface StateProvider {

    val aspect: StateAspect

    fun observe(): Flow<SystemState>

    // What an event reads. It decides once, possibly long after the home screen was last shown, so a
    // provider that bridges its first reading with a remembered value has to wait for a real one here.
    fun observeForEvent(needs: StateNeeds): Flow<SystemState> = observe()
}
