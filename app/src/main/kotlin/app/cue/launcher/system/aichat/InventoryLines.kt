package app.cue.launcher.system.aichat

import app.cue.launcher.data.aichat.InventoryApp
import app.cue.launcher.data.aichat.InventoryAppAction
import app.cue.launcher.data.aichat.InventoryDevice
import app.cue.launcher.data.aichat.InventorySnapshot
import app.cue.launcher.data.aichat.InventoryWidget
import app.cue.launcher.data.ha.HaEntity
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.system.widget.WidgetBox
import app.cue.launcher.system.widget.widgetFitsSlot

// One line per entry, label → identifier, each identifier spelled as the scenario JSON holds it. An
// app's in-app actions follow its own line: shortcuts, links, capabilities, then activities.
internal fun inventoryLines(inventory: InventorySnapshot, slots: WidgetSlots = emptyMap()): List<String> =
    appLines(inventory) +
        deviceLines(inventory.devices.orEmpty()) +
        haEntityLines(inventory.haEntities.orEmpty()) +
        widgetLines(inventory.widgets.orEmpty(), slots)

internal fun appLines(inventory: InventorySnapshot): List<String> = inventory.apps.flatMap { app ->
    listOf(appLine(app)) +
        inventory.appActions[app.packageName].orEmpty().sortedBy(::kindRank).mapNotNull(::actionLine)
}

internal fun deviceLines(devices: List<InventoryDevice>): List<String> =
    devices.map { "bt ${tidyLabel(it.name ?: UNNAMED_DEVICE)} → ${it.mac}" }

internal fun haEntityLines(entities: List<HaEntity>): List<String> = entities.map { entity ->
    entity.name?.let { "ha ${tidyLabel(it)} → ${entity.id}" } ?: "ha ${entity.id}"
}

// The sizes are the tiles whose slot the builder's widget picker offers the widget for; one that fits
// none could never be placed, so it is left out. Without slots the sizes are unknown and not listed.
internal fun widgetLines(widgets: List<InventoryWidget>, slots: WidgetSlots = emptyMap()): List<String> =
    widgets.mapNotNull { widget ->
        val sizes = slots.filterValues { slot ->
            widgetFitsSlot(widget.smallestWidthPx, widget.smallestHeightPx, slot.widthPx, slot.heightPx)
        }.keys
        if (slots.isNotEmpty() && sizes.isEmpty()) return@mapNotNull null
        val app = widget.appLabel.takeIf { !it.equals(widget.label, ignoreCase = true) }?.let { " (${tidyLabel(it)})" }
        val fits = sizes.takeIf { it.isNotEmpty() }?.joinToString(prefix = " [", postfix = "]") { it.jsonName }
        "widget ${tidyLabel(widget.label)}${app.orEmpty()}${fits.orEmpty()} → ${widget.packageName}/${widget.provider}"
    }

// The slot each tile size gives a widget on the home screen, in pixels, smallest first.
internal typealias WidgetSlots = Map<TileSize, WidgetBox>

private fun appLine(app: InventoryApp): String {
    val english = app.englishLabel?.takeIf { !it.equals(app.label, ignoreCase = true) }?.let { " (${tidyLabel(it)})" }
    return "app ${tidyLabel(app.label)}${english.orEmpty()} → ${app.packageName}"
}

// An identifier with a space or a line break in it cannot be copied off its line.
internal fun actionLine(entry: InventoryAppAction): String? {
    val kind = when (entry.action) {
        is TapAction.Shortcut -> "shortcut"
        is TapAction.DeepLink -> "deeplink"
        is TapAction.Capability -> "capability"
        is TapAction.ActivityLaunch -> "activity"
        else -> return null
    }
    return identifier(entry.action)
        ?.takeUnless(CONTROL_OR_SPACE::containsMatchIn)
        ?.let { "$kind ${tidyLabel(entry.label)} → $it" }
}

// package/target, as a widget line has it; a link names no package.
private fun identifier(action: TapAction): String? = when (action) {
    is TapAction.Shortcut -> "${action.packageName}/${action.shortcutId}"
    is TapAction.DeepLink -> action.uri
    is TapAction.Capability -> "${action.packageName}/${action.capability}"
    is TapAction.ActivityLaunch -> "${action.packageName}/${action.component}"
    else -> null
}

private fun kindRank(action: InventoryAppAction): Int =
    KIND_ORDER.indexOfFirst { it.isInstance(action.action) }.takeIf { it >= 0 } ?: KIND_ORDER.size

private val KIND_ORDER = listOf(
    TapAction.Shortcut::class,
    TapAction.DeepLink::class,
    TapAction.Capability::class,
    TapAction.ActivityLaunch::class,
)

// A label is whatever an app or a Home Assistant user named it, line breaks included.
internal fun tidyLabel(label: String): String = label.replace(CONTROL_OR_SPACE, " ").trim().take(MAX_LABEL_LENGTH)

private val CONTROL_OR_SPACE = Regex("[\\p{Cc}\\s]+")
private const val MAX_LABEL_LENGTH = 32
private const val UNNAMED_DEVICE = "Unnamed device"
