package app.cue.launcher.data.scenario

// Far above anything a real scenario holds. A file within the size cap could otherwise carry
// megabytes in a single string, which the preview would lay out on the main thread.
const val SCENARIO_NAME_MAX_LENGTH = 200
const val SCENARIO_TEXT_MAX_LENGTH = 1000
const val SCENARIO_LINK_MAX_LENGTH = 8192
const val HA_ID_MAX_LENGTH = 255

private class Capped(val field: String, val value: String?, val max: Int)

internal fun lengthDefects(scenario: Scenario): List<ScenarioDefect> {
    val capped = listOf(
        Capped("id", scenario.id, SCENARIO_NAME_MAX_LENGTH),
        Capped("name", scenario.name, SCENARIO_NAME_MAX_LENGTH),
    ) + scenario.conditions.flatMap(::conditionCaps) + contentCaps(scenario.content)
    return tooLong(capped)
}

internal fun gestureLengthDefects(gestures: GesturesConfig): List<ScenarioDefect> = tooLong(
    listOf(
        "gestures.swipeLeft" to gestures.swipeLeft,
        "gestures.searchbarTap" to gestures.searchbarTap,
        "gestures.calendarTap" to gestures.calendarTap,
    ).flatMap { (gesture, action) ->
        action?.let(::actionCaps).orEmpty().map { Capped("$gesture.${it.field}", it.value, it.max) }
    },
)

private fun tooLong(capped: List<Capped>): List<ScenarioDefect> = capped
    .filter { (it.value?.length ?: 0) > it.max }
    .map { ScenarioDefect.TooLong(it.field, it.max) }
    .distinct()

private fun text(field: String, value: String?) = Capped(field, value, SCENARIO_TEXT_MAX_LENGTH)

private fun haId(field: String, value: String?) = Capped(field, value, HA_ID_MAX_LENGTH)

private fun conditionCaps(condition: Condition): List<Capped> = when (condition) {
    is Condition.BtConnected -> listOf(text("bt_connected.mac", condition.mac))
    is Condition.Wifi -> listOf(text("wifi.ssid", condition.ssid))
    is Condition.MediaPlaying -> condition.packages.map { text("media_playing.packages", it) }
    is Condition.HaState ->
        listOf(haId("ha_state.entity", condition.entity)) + condition.states.map { haId("ha_state.states", it) }
    is Condition.BtDisconnected -> listOf(text("bt_disconnected.mac", condition.mac))
    is Condition.NotificationFrom -> listOf(
        text("notification.package", condition.packageName),
        text("notification.contains", condition.contains),
    )
    is Condition.HaEvent -> listOf(haId("ha_event.code", condition.code))
    is Condition.NotificationEnded -> listOf(text("notification_ended.package", condition.packageName))
    else -> emptyList()
} + ((condition as? Condition.InstantCondition)?.until?.let(::untilCaps) ?: emptyList())

private fun untilCaps(event: EndEvent): List<Capped> = when (event) {
    is EndEvent.Notification ->
        listOf(text("until.package", event.packageName), text("until.contains", event.contains))
    is EndEvent.HaEvent -> listOf(haId("until.code", event.code))
    is EndEvent.BtDisconnected -> listOf(text("until.mac", event.mac))
    is EndEvent.AtTime, EndEvent.Alarm -> emptyList()
}

private fun contentCaps(content: TileContent): List<Capped> = when (content) {
    is TileContent.Template ->
        listOf(text("text", content.text), text("text2", content.text2)) + content.items.flatMap { item ->
            listOf(text("items[].label", item.label), text("items[].icon", item.icon)) +
                item.actions.flatMap(::actionCaps)
        }
    is TileContent.AppWidget -> listOf(
        text("app_widget.package", content.packageName),
        text("app_widget.provider", content.provider),
        text("app_widget.appWidgetId", content.appWidgetId),
    )
    is TileContent.LauncherWidget -> emptyList()
}

private fun actionCaps(action: TapAction): List<Capped> = when (action) {
    is TapAction.App -> listOf(text("app.package", action.packageName))
    is TapAction.Shortcut -> listOf(
        text("shortcut.package", action.packageName),
        Capped("shortcut.shortcutId", action.shortcutId, SCENARIO_LINK_MAX_LENGTH),
    )
    is TapAction.DeepLink -> listOf(Capped("deeplink.uri", action.uri, SCENARIO_LINK_MAX_LENGTH))
    is TapAction.Capability ->
        listOf(text("capability.package", action.packageName), text("capability.capability", action.capability))
    is TapAction.ActivityLaunch ->
        listOf(text("activity.package", action.packageName), text("activity.component", action.component))
    is TapAction.SystemAction -> emptyList()
    is TapAction.HaAction -> listOf(
        haId("ha.service", action.service),
        // A comma list of entity ids is a valid entity here, unlike the single id stateEntity takes.
        text("ha.entity", action.entity),
        haId("ha.stateEntity", action.stateEntity),
        // Bounded so the preview can show every target id in the data without cutting it.
        Capped("ha.data", action.data.toString(), SCENARIO_LINK_MAX_LENGTH),
    )
}
