package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionMatch
import app.cue.launcher.data.scenario.RingerModeState
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.WifiMode

@Suppress("CyclomaticComplexMethod")
fun stateConditionOutcome(condition: Condition.StateCondition, state: SystemState): ConditionOutcome {
    val measured = when (condition) {
        is Condition.Charging -> outcomeOf(state.charging)
        is Condition.AudioOutput -> outcomeOf(state.audioOutput)
        is Condition.MediaPlaying -> outcomeOf(state.media?.isPlaying(condition.packages))
        is Condition.CalendarEvent -> outcomeOf(state.calendarEvent)
        is Condition.BtConnected -> outcomeOf(state.bluetooth?.isConnected(condition.mac))
        is Condition.Wifi -> wifiOutcome(condition, state.wifi)
        is Condition.RingerMode -> ringerOutcome(condition, state)
        is Condition.TimeWindow -> timeWindowOutcome(condition, state.clock)
        is Condition.HaState -> outcomeOf(state.ha?.isIn(condition.entity, condition.states))
        is Condition.MacNearby -> outcomeOf(state.macBridge?.let { it == MacBridgeState.NEARBY })
        // Unlike mac_nearby, a Mac that is away or silent is unknown rather than unlocked, so the
        // negated form never holds for a Mac nobody can see.
        is Condition.MacLocked -> outcomeOf(state.macLock.reportedLocked())
    }

    return if (condition.negate) measured.flipped() else measured
}

private fun ConditionOutcome.flipped(): ConditionOutcome = when (this) {
    ConditionOutcome.HOLDS -> ConditionOutcome.FAILS
    ConditionOutcome.FAILS -> ConditionOutcome.HOLDS

    // An unmeasured signal stays unmeasured: negation must not turn a missing permission into a pass.
    ConditionOutcome.UNKNOWN -> ConditionOutcome.UNKNOWN
}

// Under ANY the event stands on its own, so the state conditions never gate it.
fun instantTriggerAllowed(scenario: Scenario, state: SystemState): Boolean =
    scenario.match == ConditionMatch.ANY || stateGateOutcome(scenario, state) == ConditionOutcome.HOLDS

// A state condition that already holds outlasts the event under ANY, so the activation must not
// take the event's expiry with it — the countdown would end a tile the state is still holding up.
fun activationTrigger(
    scenario: Scenario,
    state: SystemState,
    fired: Condition.InstantCondition?,
): Condition.InstantCondition? = fired?.takeUnless {
    scenario.match == ConditionMatch.ANY && stateGateOutcome(scenario, state) == ConditionOutcome.HOLDS
}

fun stateGateOutcome(scenario: Scenario, state: SystemState): ConditionOutcome =
    conditionMatchOutcome(scenario.stateConditions.map { stateConditionOutcome(it, state) }, scenario.match)

// An empty list is the identity of its operator: everything holds under ALL, nothing under ANY.
fun conditionMatchOutcome(outcomes: List<ConditionOutcome>, match: ConditionMatch): ConditionOutcome =
    when (match) {
        ConditionMatch.ALL -> when {
            outcomes.any { it == ConditionOutcome.FAILS } -> ConditionOutcome.FAILS
            outcomes.any { it == ConditionOutcome.UNKNOWN } -> ConditionOutcome.UNKNOWN
            else -> ConditionOutcome.HOLDS
        }

        ConditionMatch.ANY -> when {
            outcomes.any { it == ConditionOutcome.HOLDS } -> ConditionOutcome.HOLDS
            outcomes.any { it == ConditionOutcome.UNKNOWN } -> ConditionOutcome.UNKNOWN
            else -> ConditionOutcome.FAILS
        }
    }

private fun outcomeOf(value: Boolean?): ConditionOutcome = when (value) {
    null -> ConditionOutcome.UNKNOWN
    true -> ConditionOutcome.HOLDS
    false -> ConditionOutcome.FAILS
}

private fun wifiOutcome(condition: Condition.Wifi, wifi: WifiState?): ConditionOutcome = outcomeOf(
    when {
        wifi == null -> null
        condition.mode == WifiMode.ANY -> wifi.connected
        !wifi.connected -> false
        else -> wifi.ssid?.let { it == condition.ssid }
    },
)

private fun ringerOutcome(condition: Condition.RingerMode, state: SystemState): ConditionOutcome =
    when (condition.mode) {
        RingerModeState.DND -> outcomeOf(state.doNotDisturb)
        else -> outcomeOf(state.ringerMode?.let { it == condition.mode })
    }
