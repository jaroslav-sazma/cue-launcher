package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TriggerPayload

private const val MILLIS_PER_SECOND = 1_000L

fun notificationEndedMatches(
    condition: Condition.NotificationEnded,
    notification: PostedNotification,
    endedAt: Long,
): Boolean {
    val samePackage = condition.packageName.trim().equals(notification.packageName.trim(), ignoreCase = true)
    val minimum = (condition.minLastedSec ?: 0).coerceAtLeast(0) * MILLIS_PER_SECOND
    return !notification.isGroupSummary &&
        samePackage &&
        (!condition.ongoing || notification.ongoing) &&
        endedAt - notification.firstPostTime >= minimum
}

// A notification first seen at a reconnect counts from its latest update, which can only make it
// look younger than it is — an end may be missed, never invented.
fun PostedNotification.following(previous: PostedNotification?): PostedNotification =
    if (previous == null) this else copy(firstPostTime = previous.firstPostTime)

val Scenario.notificationEndedConditions: List<Condition.NotificationEnded>
    get() = instantConditions.filterIsInstance<Condition.NotificationEnded>()

fun notificationEndedTrigger(
    scenario: Scenario,
    notification: PostedNotification,
    endedAt: Long,
): Condition.NotificationEnded? = scenario.notificationEndedConditions
    .firstOrNull { notificationEndedMatches(it, notification, endedAt) }

fun notificationEndedListeners(
    scenarios: List<Scenario>,
    notification: PostedNotification,
    endedAt: Long,
): List<Scenario> = scenarios.filter { scenario ->
    scenario.enabled && notificationEndedTrigger(scenario, notification, endedAt) != null
}

fun dueNotificationEnds(
    scenarios: List<Scenario>,
    state: SystemState,
    notification: PostedNotification,
    endedAt: Long,
): List<Scenario> = notificationEndedListeners(scenarios, notification, endedAt)
    .filter { instantTriggerAllowed(it, state) }

// No notification key: the notification is already gone, and a key the shade no longer holds would
// end the tile at the next replay.
fun notificationEndedPayload(notification: PostedNotification): TriggerPayload =
    TriggerPayload(fields = notificationTextFields(notification))
