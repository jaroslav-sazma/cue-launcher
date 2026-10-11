package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TriggerPayload

private const val MILLIS_PER_SECOND = 1_000L

const val DEFAULT_INSTANT_DURATION_SEC = 60

// The trigger decides the expiry: an activation that no event carried — a state condition under
// ANY, or a plain state scenario — never expires on its own.
fun activationOf(
    scenario: Scenario,
    now: Long,
    previous: Activation? = null,
    payload: TriggerPayload = TriggerPayload.EMPTY,
    trigger: Condition.InstantCondition? = null,
): Activation? {
    if (!scenario.enabled || scenario.isFallback) return null
    return Activation(
        scenarioId = scenario.id,
        since = previous?.since ?: now,
        expiresAt = trigger?.let { now + durationMillis(it.durationSec) },
        payload = payload,
    )
}

internal fun durationMillis(durationSec: Int?): Long =
    (durationSec?.takeIf { it > 0 } ?: DEFAULT_INSTANT_DURATION_SEC) * MILLIS_PER_SECOND
