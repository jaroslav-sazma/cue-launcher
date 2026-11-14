package app.cue.launcher.feature.scenarios

import android.content.Intent
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.GesturesConfig
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.system.DeepLinkTarget
import app.cue.launcher.system.absoluteClassName
import app.cue.launcher.system.confirmationRequired
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class TapDisclosure(val actions: List<TapAction>, val unconfirmed: Boolean)

enum class ConfigGesture { SWIPE_LEFT, SEARCHBAR_TAP, CALENDAR_TAP }

data class GestureDisclosure(val gesture: ConfigGesture, val tap: TapDisclosure)

// The prompt guards the whole tap, so one confirming HA action covers the others in the same item.
fun tapDisclosureOf(actions: List<TapAction>): TapDisclosure = TapDisclosure(
    actions = actions,
    unconfirmed = actions.any { it is TapAction.HaAction } && !confirmationRequired(actions),
)

fun tapDisclosuresOf(content: TileContent): List<TapDisclosure> = when (content) {
    is TileContent.Template -> content.items.map { tapDisclosureOf(it.actions) }.filter { it.actions.isNotEmpty() }
    is TileContent.LauncherWidget, is TileContent.AppWidget -> emptyList()
}

fun extractDisclosuresOf(scenario: Scenario): List<Condition.NotificationFrom> =
    scenario.conditions.filterIsInstance<Condition.NotificationFrom>().filter { it.extract.isNotEmpty() }

fun gestureDisclosuresOf(gestures: GesturesConfig): List<GestureDisclosure> = listOfNotNull(
    gestures.swipeLeft?.let { GestureDisclosure(ConfigGesture.SWIPE_LEFT, tapDisclosureOf(listOf(it))) },
    gestures.searchbarTap?.let { GestureDisclosure(ConfigGesture.SEARCHBAR_TAP, tapDisclosureOf(listOf(it))) },
    gestures.calendarTap?.let { GestureDisclosure(ConfigGesture.CALENDAR_TAP, tapDisclosureOf(listOf(it))) },
)

fun haTargetOf(action: TapAction.HaAction): String? =
    action.entity?.trim()?.takeIf(String::isNotEmpty) ?: entityInData(action.data)

// The entity_id in the data is never shown here: haTargetOf already names it, or the entity field
// overrides it in the call. Every other *_id key (area_id, device_id, label_id, floor_id) widens the
// target, so those come first and in full — the limit only cuts the payload after them.
fun haDataTextOf(action: TapAction.HaAction, limit: Int = HA_DATA_PREVIEW_CHARS): String? {
    val shown = action.data - HA_ENTITY_KEY
    if (shown.isEmpty()) return null
    val (targets, payload) = shown.entries.partition { it.key.endsWith(HA_TARGET_SUFFIX) }
    val text = JsonObject((targets + payload).associate { it.key to it.value }).toString()
    val kept = if (targets.isEmpty()) {
        limit
    } else {
        JsonObject(targets.associate { it.key to it.value }).toString().length - 1 + limit
    }
    return if (text.length <= kept) text else text.take(kept) + ELLIPSIS
}

// What an action without a label of its own reaches inside its app. An activity is named whole, by
// package and absolute class: the class alone can borrow the name of another app's screen.
fun actionTargetOf(action: TapAction): String? = when (action) {
    is TapAction.Shortcut -> action.shortcutId.trim()
    is TapAction.Capability -> action.capability.trim()
    is TapAction.ActivityLaunch -> action.packageName.trim().let { "$it/${absoluteClassName(it, action.component)}" }
    is TapAction.App, is TapAction.DeepLink, is TapAction.SystemAction, is TapAction.HaAction -> null
}

// A file within the size cap can still carry megabytes in one string; laying that out freezes the
// preview, whatever the import checks let through before.
fun previewText(text: String, limit: Int = PREVIEW_TEXT_CHARS): String {
    if (text.length <= limit) return text
    val end = if (limit > 0 && text[limit - 1].isHighSurrogate()) limit - 1 else limit
    return text.take(end) + ELLIPSIS
}

enum class LinkPart { APP, ACTION, OPENS, FALLBACK }

data class LinkFact(val part: LinkPart, val value: String)

sealed interface LinkDisclosure {
    data object Invalid : LinkDisclosure

    data class Parsed(val facts: List<LinkFact>, val refused: Boolean) : LinkDisclosure
}

// Fields inside an intent: link come in any order, so the raw text can push the component behind
// any amount of padding. What the link reaches is read from the parsed intent and listed first.
fun linkDisclosureOf(target: DeepLinkTarget?): LinkDisclosure {
    target ?: return LinkDisclosure.Invalid
    val app = target.component ?: target.packageName
    val opens = target.dataScheme?.let { scheme -> target.dataHost?.let { "$scheme://$it" } ?: "$scheme:" }
    val facts = listOfNotNull(
        app?.let { LinkFact(LinkPart.APP, it) },
        target.action?.takeIf { it != Intent.ACTION_VIEW }?.let { LinkFact(LinkPart.ACTION, it) },
        opens?.let { LinkFact(LinkPart.OPENS, it) },
        target.fallbackHost?.let { LinkFact(LinkPart.FALLBACK, it) },
    )
    return LinkDisclosure.Parsed(facts = facts, refused = !target.allowed)
}

private fun entityInData(data: JsonObject): String? = when (val raw = data[HA_ENTITY_KEY]) {
    is JsonPrimitive -> raw.content.takeIf(String::isNotBlank)
    is JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.content }.joinToString().takeIf(String::isNotBlank)
    else -> null
}

private const val HA_ENTITY_KEY = "entity_id"
private const val HA_TARGET_SUFFIX = "_id"
private const val HA_DATA_PREVIEW_CHARS = 120
private const val PREVIEW_TEXT_CHARS = 500
private const val ELLIPSIS = "…"
