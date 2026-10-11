package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Scenario

// Only the notification conditions need the listener bound: what is playing is read through the
// media sessions, which the granted access opens without a binding.
fun needsNotificationPosts(scenarios: List<Scenario>): Boolean = scenarios.any {
    it.enabled && (it.notificationConditions.isNotEmpty() || it.notificationEndedConditions.isNotEmpty())
}

// Lower-cased, as a package matches regardless of case. A disabled scenario listens to nothing.
fun notificationPackages(scenarios: List<Scenario>): Set<String> = scenarios
    .filter { it.enabled }
    .flatMap { scenario ->
        scenario.notificationConditions.map { it.packageName } +
            scenario.notificationEndedConditions.map { it.packageName }
    }
    .mapTo(mutableSetOf()) { it.trim().lowercase() }

data class NotificationFiring(val scenario: Scenario, val notification: PostedNotification)

// What is posted when the listener binds again. enrich reads what only the notification itself can
// give — progress, style, reply — and is asked only for the ones that fire.
class NotificationSnapshot(
    val active: List<PostedNotification>,
    val enrich: (PostedNotification) -> PostedNotification = { it },
)

data class NotificationReplay(val active: List<PostedNotification>, val since: Long, val now: Long)

data class NotificationReconciliation(
    val fire: List<NotificationFiring> = emptyList(),
    val end: List<String> = emptyList(),
)

// What the live callbacks would have done while the listener was not bound, worked out from what is
// still posted when it binds again. A notification posted and dismissed in between would have ended
// its own activation anyway, and an update moves postTime, so the countdown restarts from the last
// one. The one difference from a live post: a state gate is judged now, not at the moment of posting.
fun notificationReconciliation(
    scenarios: List<Scenario>,
    activations: List<Activation>,
    state: SystemState,
    replay: NotificationReplay,
): NotificationReconciliation {
    val active = replay.active
    val posted = active.map(PostedNotification::key).toSet()
    val end = activations
        .filter { activation -> activation.payload.notificationKey?.let { it !in posted } == true }
        .mapNotNull { activation -> scenarios.firstOrNull { it.id == activation.scenarioId } }
        .filter { scenario -> scenario.notificationConditions.isNotEmpty() }
        .filter { scenario ->
            scenario.notificationConditions.none { condition -> active.any { notificationMatches(condition, it) } }
        }
        .map(Scenario::id)

    val latest = linkedMapOf<String, NotificationFiring>()
    active.filter { it.postTime > replay.since }.sortedBy(PostedNotification::postTime).forEach { notification ->
        dueNotifications(scenarios, state, notification).forEach { scenario ->
            latest[scenario.id] = NotificationFiring(scenario, notification)
        }
    }
    // A post the live callback has already seen — the listener bound while it was being read — runs
    // to the same or a later expiry and is left alone.
    val running = activations.associateBy(Activation::scenarioId)
    val fire = latest.values.filter { firing ->
        val fired = notificationTrigger(firing.scenario, firing.notification)
        val trigger = activationTrigger(firing.scenario, state, fired)
        val expiresAt = trigger?.let { firing.notification.postTime + durationMillis(it.durationSec) }
        when {
            expiresAt == null -> true
            expiresAt <= replay.now -> false
            else -> running[firing.scenario.id]?.expiresAt?.let { it < expiresAt } ?: true
        }
    }
    return NotificationReconciliation(fire = fire, end = end)
}
