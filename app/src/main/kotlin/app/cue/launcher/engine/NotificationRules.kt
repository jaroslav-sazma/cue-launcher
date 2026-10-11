package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ExtractRule
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.NotificationReply
import app.cue.launcher.data.scenario.extractFields
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.SemanticStyle
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileProgress
import app.cue.launcher.data.scenario.TriggerPayload

fun usesNotificationListener(scenarios: List<Scenario>): Boolean = scenarios.any { scenario ->
    scenario.conditions.any {
        it is Condition.NotificationFrom || it is Condition.NotificationEnded || it is Condition.MediaPlaying
    } || (scenario.content as? TileContent.LauncherWidget)?.widget == LauncherWidgetType.MEDIA
}

const val NOTIFICATION_VALUE_NAMESPACE = "notif"

const val NOTIFICATION_FIELD_TITLE = "title"

const val NOTIFICATION_FIELD_TEXT = "text"

data class PostedNotification(
    val key: String,
    val packageName: String,
    val title: String? = null,
    val text: String? = null,
    val isGroupSummary: Boolean = false,
    val progress: TileProgress? = null,
    val semantic: SemanticStyle = SemanticStyle.UNSPECIFIED,
    val reply: NotificationReply? = null,
    val postTime: Long = 0L,
    val ongoing: Boolean = false,
    // postTime moves with every update, so how long a notification has been up is measured from
    // the first post the listener saw of it.
    val firstPostTime: Long = postTime,
)

fun notificationMatches(condition: Condition.NotificationFrom, notification: PostedNotification): Boolean {
    if (notification.isGroupSummary) return false
    val samePackage = condition.packageName.trim().equals(notification.packageName.trim(), ignoreCase = true)
    val needle = condition.contains?.trim()?.takeIf(String::isNotEmpty)
    return samePackage &&
        (needle == null || notificationHaystack(notification).contains(needle, ignoreCase = true))
}

fun notificationTrigger(
    scenario: Scenario,
    notification: PostedNotification,
): Condition.NotificationFrom? = scenario.notificationConditions
    .firstOrNull { notificationMatches(it, notification) }

fun notificationListeners(
    scenarios: List<Scenario>,
    notification: PostedNotification,
): List<Scenario> = scenarios.filter { scenario ->
    scenario.enabled && notificationTrigger(scenario, notification) != null
}

fun dueNotifications(
    scenarios: List<Scenario>,
    state: SystemState,
    notification: PostedNotification,
): List<Scenario> = notificationListeners(scenarios, notification).filter { instantTriggerAllowed(it, state) }

fun notificationEndListeners(
    scenarios: List<Scenario>,
    removed: PostedNotification,
    active: List<PostedNotification>,
): List<Scenario> {

    val remaining = active.filter { it.key != removed.key }
    return notificationEndCandidates(scenarios, removed).filter { scenario ->

        // Under ANY a scenario watches several apps; it ends only once none of them is left.
        scenario.notificationConditions.none { condition ->
            remaining.any { notificationMatches(condition, it) }
        }
    }
}

fun notificationEndCandidates(
    scenarios: List<Scenario>,
    removed: PostedNotification,
): List<Scenario> = scenarios.filter { scenario -> notificationTrigger(scenario, removed) != null }

val Scenario.notificationConditions: List<Condition.NotificationFrom>
    get() = instantConditions.filterIsInstance<Condition.NotificationFrom>()

fun notificationPayload(
    notification: PostedNotification,
    extract: List<ExtractRule> = emptyList(),
): TriggerPayload = TriggerPayload(
    fields = notificationTextFields(notification) + buildMap {
        // Reserved names never come out of extractFields, so a rule cannot replace title or text.
        extractFields(extract, notificationHaystack(notification))
            .forEach { (name, value) -> put("$NOTIFICATION_VALUE_NAMESPACE:$name", value) }
    },

    progress = notification.progress?.takeIf { progressTrack(it) != null },
    semantic = notification.semantic,
    reply = notification.reply,
    notificationKey = notification.key,
)

internal fun notificationTextFields(notification: PostedNotification): Map<String, String> = buildMap {
    notification.title?.takeIf(String::isNotBlank)
        ?.let { put("$NOTIFICATION_VALUE_NAMESPACE:$NOTIFICATION_FIELD_TITLE", it) }
    notification.text?.takeIf(String::isNotBlank)
        ?.let { put("$NOTIFICATION_VALUE_NAMESPACE:$NOTIFICATION_FIELD_TEXT", it) }
}

internal fun notificationHaystack(notification: PostedNotification): String =
    listOfNotNull(notification.title, notification.text).joinToString(separator = "\n")
