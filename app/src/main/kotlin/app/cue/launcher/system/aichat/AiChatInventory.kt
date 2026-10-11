package app.cue.launcher.system.aichat

import app.cue.launcher.data.aichat.AiChatOffer
import app.cue.launcher.data.aichat.AiChatShare
import app.cue.launcher.data.aichat.InventoryApp
import app.cue.launcher.data.aichat.InventoryAppAction
import app.cue.launcher.data.aichat.InventoryDevice
import app.cue.launcher.data.aichat.InventorySnapshot
import app.cue.launcher.data.aichat.InventorySource
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.placeholderFor
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

// What the user shares of the phone, in-app actions of every app included, read once when the user
// asks for the brief. Bluetooth, Home Assistant or the apps' shortcuts kept back, or a part not offered,
// is not read at all. An app whose actions did not arrive in time is listed without them, and the
// grounding of the answer reads them again, in full, for the apps it names.
internal suspend fun fullInventory(source: InventorySource, share: AiChatShare): InventorySnapshot = coroutineScope {
    val snapshot = source.snapshot(share)
    val services = async {
        snapshot.haEntities?.takeIf { share.homeAssistant && it.isNotEmpty() }?.let { source.haServices() }
    }
    val packages = snapshot.apps.map(InventoryApp::packageName).distinct()
    val actions = async { allAppActions(source, packages, share.shortcuts) }
    snapshot.copy(appActions = actions.await(), haServices = services.await())
}

private suspend fun allAppActions(
    source: InventorySource,
    packages: List<String>,
    shortcuts: Boolean,
): Map<String, List<InventoryAppAction>> {
    val read = ConcurrentHashMap<String, List<InventoryAppAction>>()
    val permits = Semaphore(PARALLEL_ACTION_READS)
    withTimeoutOrNull(ALL_ACTIONS_TIMEOUT) {
        packages.forEach { packageName ->
            launch { permits.withPermit { source.appActions(packageName, shortcuts)?.let { read[packageName] = it } } }
        }
    }
    return read.toMap()
}

// A part the phone does not offer reads as unavailable, as one that could not be read does; only a
// part the user could have shared and did not reads as kept back.
internal fun inventorySection(
    inventory: InventorySnapshot,
    share: AiChatShare,
    offer: AiChatOffer,
    slots: WidgetSlots = emptyMap(),
): String = buildString {
    appendLine(INVENTORY_INTRO)
    appendLine()
    appendApps(inventory, share.shortcuts)
    if (inventory.unavailableSystemActions.isNotEmpty()) {
        appendLine("### System actions")
        val names = inventory.unavailableSystemActions.sorted().joinToString { "`${it.jsonName}`" }
        appendLine("Not available on this phone, so never use them: $names.")
        appendLine()
    }
    appendBluetooth(inventory.devices, shared = share.bluetooth, offered = offer.bluetooth)
    appendHomeAssistant(inventory, shared = share.homeAssistant, offered = offer.homeAssistant)
    appendLine("### Widgets")
    val widgets = inventory.widgets?.let { widgetLines(it, slots) }
    when {
        widgets == null -> appendLine(NO_WIDGET_LIST)
        widgets.isEmpty() -> appendLine(NO_WIDGETS)
        else -> appendBlock(widgets)
    }
}

private fun StringBuilder.appendBluetooth(read: List<InventoryDevice>?, shared: Boolean, offered: Boolean) {
    appendLine("### Bluetooth devices")
    val devices = read.takeIf { shared && offered }
    when {
        offered && !shared -> appendLine(BLUETOOTH_KEPT_BACK)
        devices == null -> appendLine(NO_BLUETOOTH_PERMISSION)
        devices.isEmpty() -> appendLine(NO_BLUETOOTH_DEVICES)
        else -> appendBlock(deviceLines(devices))
    }
    appendLine()
}

private fun StringBuilder.appendHomeAssistant(inventory: InventorySnapshot, shared: Boolean, offered: Boolean) {
    appendLine("### Home Assistant")
    val entities = inventory.haEntities.takeIf { shared && offered }
    when {
        offered && !shared -> appendLine(HOME_ASSISTANT_KEPT_BACK)
        entities == null -> appendLine(NO_HOME_ASSISTANT)
        entities.isEmpty() -> appendLine(NO_HA_ENTITIES)
        else -> appendBlock(haEntityLines(entities))
    }
    appendLine("Services: ${haServicesLine(entities.orEmpty(), inventory.haServices.takeIf { shared && offered })}")
    appendLine()
}

private fun StringBuilder.appendApps(inventory: InventorySnapshot, shortcuts: Boolean) {
    appendLine("### Apps (${inventory.apps.size})")
    if (inventory.apps.isEmpty()) {
        appendLine(NO_APPS)
        appendLine()
        return
    }
    val labels = inventory.apps.associate { it.packageName to it.label }
    val actions = inventory.appActions.mapValues { (packageName, entries) ->
        chatActions(entries, labels[packageName], shortcuts)
    }
    val lines = appLines(inventory.copy(appActions = actions))
    appendBlock(lines)
    when {
        !shortcuts -> appendLine(SHORTCUTS_KEPT_BACK)
        lines.none { it.startsWith("shortcut ") } -> appendLine(NO_SHORTCUTS)
    }
    appendLine(SCREENS_NOTE)
    appendLine()
}

// An app exports activities by the dozen, most of them screens that need their caller's data. One
// without a label of its own, or with just the app's, is almost always such a screen and tells the
// chat nothing; the rest are capped per app.
internal fun chatActions(
    entries: List<InventoryAppAction>,
    appLabel: String?,
    shortcuts: Boolean = true,
): List<InventoryAppAction> {
    val (screens, rest) = entries.filter { shortcuts || !fromShortcuts(it.action) }
        .partition { it.action is TapAction.ActivityLaunch }
    return rest + screens.filter { telling(it, appLabel) }.take(MAX_SCREENS_PER_APP)
}

private fun fromShortcuts(action: TapAction): Boolean =
    action is TapAction.Shortcut || action is TapAction.Capability

private fun telling(entry: InventoryAppAction, appLabel: String?): Boolean =
    entry.label != (entry.action as TapAction.ActivityLaunch).component.substringAfterLast('.') &&
        !entry.label.equals(appLabel, ignoreCase = true)

private fun StringBuilder.appendBlock(lines: List<String>) {
    appendLine("```text")
    lines.forEach(::appendLine)
    appendLine("```")
}

private val INVENTORY_INTRO = """
    ## This phone
    What Cue: read on this phone just now. One entry per line, `<kind> <label> → <identifier>`; copy the
    identifier after the arrow exactly.
    - `app <label> (<English label>) → <package>`: an installed app; the English label shows when it
      differs. The app's in-app actions follow its line:
      - `shortcut <label> → <package>/<shortcutId>`: kind shortcut;
      - `deeplink <label> → <uri>`: kind deeplink, a link this app is verified to open;
      - `capability <label> → <package>/<capability>`: kind capability;
      - `activity <label> → <package>/<component>`: kind activity, a screen of the app.
    - `bt <name> → <MAC>`: a paired Bluetooth device, for `mac`.
    - `ha <name> → <entity_id>`: a Home Assistant entity, for `entity` and `{ha:<entity_id>}`.
    - `widget <label> (<app>) [<sizes>] → <package>/<provider>`: an app widget, for app_widget's
      `package` and `provider`; the app shows when it differs from the label, and the brackets list
      the tile sizes the widget fits.
""".trimIndent()

private const val NO_APPS = "No apps could be read; only system actions, links and Home Assistant are available."

private const val NO_SHORTCUTS =
    "No app shortcuts are listed: Android shows them only to the default home app, and Cue: either is not " +
        "it right now or no app publishes any. Do not use kind shortcut or capability; open the app, or " +
        "reach the place through a link."

private const val SHORTCUTS_KEPT_BACK =
    "The user chose not to share the apps' shortcuts with you, so neither shortcuts nor capabilities are " +
        "listed. Do not ask for them and do not use kind shortcut or capability; open the app (kind app), " +
        "or use one of its links or screens listed above."

private const val MAX_SCREENS_PER_APP = 4

private const val SCREENS_NOTE =
    "At most $MAX_SCREENS_PER_APP labelled screens per app are listed. Cue: accepts any other screen " +
        "the app exports too, so an activity you know of may work; Cue: checks it."

private val NO_BLUETOOTH_PERMISSION =
    "Cue: has no Bluetooth permission, so no device is known. Leave out `mac` to react to any device, or " +
        "use `${placeholderFor("bt_<name>")}` for one device; the user picks it in Cue:, which asks for " +
        "the permission then."

private val BLUETOOTH_KEPT_BACK =
    "The user chose not to share their Bluetooth devices with you, so none is listed. Do not ask for a " +
        "device's name or address: leave out `mac` to react to any device, or use " +
        "`${placeholderFor("bt_<name>")}` for one device, which the user then picks in Cue:."

private const val NO_BLUETOOTH_DEVICES = "No Bluetooth device is paired with this phone; leave out `mac`."

private val NO_HOME_ASSISTANT =
    "Home Assistant is not connected to Cue:, or it could not be reached just now, so no entity is known. " +
        "An ha action or an ha_state condition can still be drafted with `${placeholderFor("<key>")}` as its " +
        "`entity`; the user then connects Home Assistant in Cue: and fills it in. ha_event needs no " +
        "connection, only Cue:'s blueprint in Home Assistant."

private val HOME_ASSISTANT_KEPT_BACK =
    "The user chose not to share their Home Assistant entities and services with you, so none is listed. " +
        "Do not ask for entity ids: use `${placeholderFor("<key>")}` as the `entity` of an ha action or an " +
        "ha_state condition, which the user then fills in in Cue:, and pick the service by the entity's " +
        "domain. ha_event needs no entity, only Cue:'s blueprint in Home Assistant."

private const val NO_HA_ENTITIES = "Home Assistant answered but lists no entities."

private const val NO_WIDGET_LIST =
    "The widget list could not be read, so no app widget is known; prefer a template or a launcher widget."

private const val NO_WIDGETS =
    "No installed app offers a widget that fits a tile; use a template or a launcher widget."

private const val PARALLEL_ACTION_READS = 4
private val ALL_ACTIONS_TIMEOUT = 10.seconds
