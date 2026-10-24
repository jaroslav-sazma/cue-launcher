package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TriggerPayload
import java.time.ZoneId
import java.time.ZonedDateTime

private const val MILLIS_PER_SECOND = 1_000L

const val DEFAULT_INSTANT_DURATION_SEC = 60

// The trigger decides the expiry: an activation that no event carried — a state condition under
// ANY, or a plain state scenario — never expires on its own, nor waits for an end event, nor has a
// fire time, nor keeps a notification key, so the notification going away cannot end what the
// state holds.
fun activationOf(
    scenario: Scenario,
    at: ZonedDateTime,
    previous: Activation? = null,
    payload: TriggerPayload = TriggerPayload.EMPTY,
    trigger: Condition.InstantCondition? = null,
): Activation? {
    if (!scenario.enabled || scenario.isFallback) return null
    val now = at.toInstant().toEpochMilli()
    val end = trigger?.let { instantEndOf(it, now, at.zone) }
    return Activation(
        scenarioId = scenario.id,
        since = previous?.since ?: now,
        expiresAt = end?.expiresAt,
        payload = payload.copy(
            notificationKey = payload.notificationKey.takeIf { trigger != null },
            until = end?.until,
            firedAt = end?.let { now },
        ),
    )
}

data class InstantEnd(val expiresAt: Long?, val until: EndEvent?)

// With an end event the default countdown no longer applies, and a duration given alongside caps
// it. A time end is a fixed moment, so it becomes the expiry and works while nothing runs. An end
// that could never arrive falls back to the countdown rather than holding the tile for good.
fun instantEndOf(trigger: Condition.InstantCondition, firedAt: Long, zone: ZoneId): InstantEnd {
    val countdown = InstantEnd(expiresAt = firedAt + durationMillis(trigger.durationSec), until = null)
    val cap = trigger.durationSec?.takeIf { it > 0 }?.let { firedAt + it * MILLIS_PER_SECOND }
    return when (val until = trigger.until) {
        null -> countdown
        is EndEvent.AtTime -> nextOccurrenceMillis(until.at, until.days, firedAt, zone)
            ?.let { at -> InstantEnd(expiresAt = cap?.let { minOf(it, at) } ?: at, until = null) }
            ?: countdown

        else -> if (endEventCanArrive(until)) InstantEnd(expiresAt = cap, until = until) else countdown
    }
}

internal fun durationMillis(durationSec: Int?): Long =
    (durationSec?.takeIf { it > 0 } ?: DEFAULT_INSTANT_DURATION_SEC) * MILLIS_PER_SECOND
