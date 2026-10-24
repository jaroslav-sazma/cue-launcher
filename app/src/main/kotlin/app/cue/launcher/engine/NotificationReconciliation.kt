package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.Scenario
import java.time.ZoneId

// Only the notification conditions need the listener bound: what is playing is read through the
// media sessions, which the granted access opens without a binding. A running activation a
// notification is to end needs it for as long as it runs.
fun needsNotificationPosts(scenarios: List<Scenario>, activations: List<Activation>): Boolean =
    scenarios.any {
        it.enabled && (it.notificationConditions.isNotEmpty() || it.notificationEndedConditions.isNotEmpty())
    } || activations.any { it.notificationEnd != null }

// Lower-cased, as a package matches regardless of case. A disabled scenario listens to nothing. The
// end events of the definitions count too, so a post that ends a tile fired a moment ago is not
// filtered out before the running activations have reached the listener.
fun notificationPackages(scenarios: List<Scenario>, activations: List<Activation>): Set<String> = scenarios
    .filter { it.enabled }
    .flatMap { scenario ->
        scenario.notificationConditions.map { it.packageName } +
            scenario.notificationEndedConditions.map { it.packageName } +
            scenario.instantConditions.mapNotNull { (it.until as? EndEvent.Notification)?.packageName }
    }
    .plus(activations.mapNotNull { it.notificationEnd?.packageName })
    .mapTo(mutableSetOf()) { it.trim().lowercase() }

// The definitions only say a dismissal may end the scenario. Under ANY the running tile may have been
// fired by a condition with an end event, or taken over by a state; only a tile that kept the key of
// the notification that fired it goes with the dismissal, as on a replay.
fun heldByNotification(running: List<Activation>, ending: List<Scenario>): List<String> {
    val ids = ending.mapTo(mutableSetOf(), Scenario::id)
    return running.filter { it.scenarioId in ids && it.payload.notificationKey != null }.map(Activation::scenarioId)
}

data class NotificationFiring(val scenario: Scenario, val notification: PostedNotification)

// What is posted when the listener binds again. enrich reads what only the notification itself can
// give — progress, style, reply — and is asked only for the ones that fire.
class NotificationSnapshot(
    val active: List<PostedNotification>,
    val enrich: (PostedNotification) -> PostedNotification = { it },
)

data class NotificationReplay(
    val active: List<PostedNotification>,
    val since: Long,
    val now: Long,
    val zone: ZoneId,
)

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
    val dismissed = activations
        .filter { activation -> activation.payload.notificationKey?.let { it !in posted } == true }
        .mapNotNull { activation -> scenarios.firstOrNull { it.id == activation.scenarioId } }
        .filter { scenario -> scenario.dismissibleNotificationConditions.isNotEmpty() }
        .filter { scenario ->
            scenario.dismissibleNotificationConditions.none { condition ->
                active.any { notificationMatches(condition, it) }
            }
        }
        .map(Scenario::id)

    val latest = linkedMapOf<String, NotificationFiring>()
    active.filter { it.postTime > replay.since }.sortedBy(PostedNotification::postTime).forEach { notification ->
        dueNotifications(scenarios, state, notification).forEach { scenario ->
            latest[scenario.id] = NotificationFiring(scenario, notification)
        }
    }

    val running = activations.associateBy(Activation::scenarioId)
    val fire = mutableListOf<NotificationFiring>()
    val ended = mutableListOf<String>()
    (latest.keys + running.keys).forEach { id ->
        val outcome = replayed(latest[id], running[id], state, replay)
        outcome.fire?.let(fire::add)
        if (outcome.end) ended += id
    }
    return NotificationReconciliation(fire = fire, end = (dismissed + ended).distinct())
}

private class Replayed(val fire: NotificationFiring?, val end: Boolean)

// Live, an end goes before a fire of the same post, and the posts come in the order they were
// posted. So the running activation is ended by an end post up to the last firing, which then starts
// a new one, and an end post after that firing ends the new one too, leaving nothing running.
private fun replayed(
    firing: NotificationFiring?,
    activation: Activation?,
    state: SystemState,
    replay: NotificationReplay,
): Replayed {
    val endedBefore = activation?.endedByPost(replay.active, upTo = firing?.notification?.postTime) == true
    if (firing == null) return Replayed(fire = null, end = endedBefore)

    val fired = notificationTrigger(firing.scenario, firing.notification)
    val trigger = activationTrigger(firing.scenario, state, fired)
    val end = trigger?.let { instantEndOf(it, firing.notification.postTime, replay.zone) }
    val endedAfter = end.endedAfter(firing.notification, replay.active)
    val fires = !endedAfter && outlasts(end?.expiresAt, activation.takeUnless { endedBefore }, replay.now)
    return when {
        fires -> Replayed(fire = firing, end = endedBefore)
        activation == null -> Replayed(fire = null, end = false)
        else -> Replayed(fire = null, end = endedAfter || activation.endedByPost(replay.active, upTo = null))
    }
}

// A post the live callback has already seen — the listener bound while it was being read — runs to
// the same or a later expiry and is left alone.
private fun outlasts(expiresAt: Long?, running: Activation?, now: Long): Boolean = when {
    expiresAt == null -> true
    expiresAt <= now -> false
    else -> running?.expiresAt?.let { it < expiresAt } ?: true
}

private fun InstantEnd?.endedAfter(fired: PostedNotification, active: List<PostedNotification>): Boolean {
    val until = this?.until as? EndEvent.Notification ?: return false
    return active.any { it.postTime > fired.postTime && endEventMatches(until, it) }
}

private fun Activation.endedByPost(active: List<PostedNotification>, upTo: Long?): Boolean {
    val until = notificationEnd ?: return false
    return active.any {
        it.postTime > lastFiredAt && (upTo == null || it.postTime <= upTo) && endEventMatches(until, it)
    }
}
