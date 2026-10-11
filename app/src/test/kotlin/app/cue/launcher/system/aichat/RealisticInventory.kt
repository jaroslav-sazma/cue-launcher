package app.cue.launcher.system.aichat

import app.cue.launcher.data.aichat.InventoryApp
import app.cue.launcher.data.aichat.InventoryAppAction
import app.cue.launcher.data.aichat.InventoryDevice
import app.cue.launcher.data.aichat.InventorySnapshot
import app.cue.launcher.data.aichat.InventoryWidget
import app.cue.launcher.data.ha.HaEntity
import app.cue.launcher.data.ha.HaEntityFilter
import app.cue.launcher.data.ha.HaService
import app.cue.launcher.data.scenario.TapAction

// A full phone of the size a heavy user has: 150 apps with a few shortcuts each, some links and
// capabilities, a long tail of exported screens, 200 Home Assistant entities with their services,
// 5 Bluetooth devices and 30 widget providers. Names and lengths follow real packages and classes.
internal fun realisticInventory(): InventorySnapshot {
    val apps = (1..APP_COUNT).map { index ->
        val vendor = VENDORS[index % VENDORS.size]
        InventoryApp(
            label = "${WORDS[index % WORDS.size]} ${WORDS[(index * 7) % WORDS.size]}",
            packageName = "com.$vendor.android.apps.${WORDS[index % WORDS.size].lowercase()}$index",
            englishLabel = if (index % 4 == 0) "English name $index" else null,
        )
    }
    val actions = apps.mapIndexed { index, app -> app.packageName to actionsOf(index, app.packageName) }.toMap()
    val entities = (1..ENTITY_COUNT).map { index ->
        val domain = HA_DOMAINS[index % HA_DOMAINS.size]
        HaEntity("$domain.${ROOMS[index % ROOMS.size]}_${domain}_$index", "${ROOMS[index % ROOMS.size]} $domain $index")
    }
    return InventorySnapshot(
        apps = apps,
        devices = (1..DEVICE_COUNT).map { InventoryDevice("Device $it", "00:1A:7D:DA:71:%02X".format(it)) },
        haEntities = entities,
        widgets = (1..WIDGET_COUNT).map { index ->
            val app = apps[index * 3]
            InventoryWidget(
                label = "Widget ${WORDS[index % WORDS.size]}",
                appLabel = app.label,
                packageName = app.packageName,
                provider = "${app.packageName}.widget.${WORDS[index % WORDS.size]}AppWidgetProvider",
            )
        },
        appActions = actions,
        haServices = HA_SERVICES.flatMap { (domain, verbs) ->
            verbs.map { verb -> HaService("$domain.$verb", targets = listOf(HaEntityFilter(setOf(domain)))) }
        } + listOf("toggle", "turn_on", "turn_off").map { verb ->
            HaService("homeassistant.$verb", targets = listOf(HaEntityFilter()))
        },
    )
}

private fun actionsOf(index: Int, packageName: String): List<InventoryAppAction> = buildList {
    (1..SHORTCUTS_PER_APP).forEach {
        add(InventoryAppAction("Shortcut $it", TapAction.Shortcut(packageName, "shortcut_$it")))
    }
    if (index % 10 == 0) {
        add(InventoryAppAction("Search", TapAction.DeepLink("https://www.example.com/app$index/search")))
    }
    if (index % 15 == 0) {
        add(InventoryAppAction("Directions", TapAction.Capability(packageName, "actions.intent.GET_DIRECTIONS")))
    }
    if (index % 3 == 0) {
        (1..SCREENS_PER_RICH_APP).forEach { screen ->
            val component = "$packageName.ui.Screen${screen}Activity"
            val label = if (screen % 2 == 0) "Screen $screen" else "Screen${screen}Activity"
            add(InventoryAppAction(label, TapAction.ActivityLaunch(packageName, component)))
        }
    }
}

private const val APP_COUNT = 150
private const val ENTITY_COUNT = 200
private const val DEVICE_COUNT = 5
private const val WIDGET_COUNT = 30
private const val SHORTCUTS_PER_APP = 3
private const val SCREENS_PER_RICH_APP = 12

private val VENDORS = listOf("google", "samsung", "spotify", "microsoft", "whatsapp", "seznam")

private val WORDS = listOf(
    "Maps", "Music", "Calendar", "Camera", "Photos", "Wallet", "Notes", "Weather", "Clock", "Fitness",
    "Banking", "Podcasts", "Translate", "Messages", "Contacts", "Files", "Keep", "Drive", "Docs", "Mail",
)

private val ROOMS = listOf("living_room", "kitchen", "bedroom", "garage", "garden", "hall", "office", "bathroom")

private val HA_DOMAINS = listOf(
    "light", "switch", "sensor", "binary_sensor", "cover", "climate", "media_player", "lock", "script", "scene",
)

private val SWITCHING = listOf("turn_on", "turn_off", "toggle")

private val HA_SERVICES = mapOf(
    "light" to SWITCHING,
    "switch" to SWITCHING,
    "cover" to listOf("open_cover", "close_cover", "stop_cover", "toggle", "set_cover_position"),
    "climate" to listOf("set_temperature", "set_hvac_mode", "set_preset_mode", "turn_on", "turn_off"),
    "media_player" to listOf(
        "media_play_pause", "media_next_track", "media_previous_track", "volume_set", "volume_mute", "turn_on",
        "turn_off", "select_source",
    ),
    "lock" to listOf("lock", "unlock", "open"),
    "script" to SWITCHING,
    "scene" to listOf("turn_on"),
)
