package app.cue.launcher.system.aichat

internal data class ScenarioExample(
    val request: String,
    val inventory: List<String>,
    val json: String,
)

// Shaped like fixtures/scenarios, the inventory lines as inventoryLines writes them;
// ScenarioContractTest runs each through the import gate.
internal val SCENARIO_EXAMPLES: List<ScenarioExample> = listOf(
    // The front door's lock is not in the inventory, so its entity is a placeholder named after the
    // user's answer.
    ScenarioExample(
        request = "When Home Assistant sends doorbell, buttons to open the gate and unlock the door for 2 minutes; " +
            "the door is the front door, and its lock is not among my Home Assistant entities",
        inventory = listOf("ha Gate → cover.gate"),
        json = """
            {
              "schemaVersion": 1,
              "id": "example-doorbell",
              "name": "Doorbell → gate, door",
              "priority": 900,
              "size": "2x2",
              "conditions": [
                { "type": "ha_event", "code": "doorbell", "durationSec": 120 }
              ],
              "content": {
                "kind": "template",
                "layout": "text_two_icons",
                "text": "At the door",
                "items": [
                  { "label": "Gate", "icon": "mdi:gate-open", "action": { "kind": "ha", "service": "cover.open_cover", "entity": "cover.gate", "confirm": true } },
                  { "label": "Door", "icon": "mdi:lock-open", "action": { "kind": "ha", "service": "lock.unlock", "entity": "{{PLACEHOLDER:front_door}}", "confirm": true } }
                ]
              }
            }
        """.trimIndent(),
    ),
    // A place the request names in an app is that app's shortcut, not the app itself.
    ScenarioExample(
        request = "When the car connects, offer Maps home and Spotify",
        inventory = listOf(
            "app Maps → com.google.android.apps.maps",
            "shortcut Home → com.google.android.apps.maps/home",
            "app Spotify → com.spotify.music",
            "bt Octavia → 00:1A:7D:DA:71:13",
        ),
        json = """
            {
              "schemaVersion": 1,
              "id": "example-car",
              "name": "Car → Home, Spotify",
              "priority": 600,
              "size": "2x2",
              "conditions": [
                { "type": "bt_connected", "mac": "00:1A:7D:DA:71:13" }
              ],
              "content": {
                "kind": "template",
                "layout": "text_two_icons",
                "text": "In the car",
                "items": [
                  { "label": "Home", "action": { "kind": "shortcut", "package": "com.google.android.apps.maps", "shortcutId": "home" } },
                  { "label": "Spotify", "action": { "kind": "app", "package": "com.spotify.music" } }
                ]
              }
            }
        """.trimIndent(),
    ),
    ScenarioExample(
        request = "Show the Clock app while charging",
        inventory = listOf("app Clock → com.google.android.deskclock"),
        json = """
            {
              "schemaVersion": 1,
              "id": "example-charging",
              "name": "Charging → Clock",
              "priority": 400,
              "size": "1x1",
              "conditions": [
                { "type": "charging" }
              ],
              "content": {
                "kind": "template",
                "layout": "icon_text",
                "items": [
                  { "label": "Clock", "icon": "mdi:clock", "action": { "kind": "app", "package": "com.google.android.deskclock" } }
                ]
              }
            }
        """.trimIndent(),
    ),
    // The duration and the size are the user's answers; the widget arrives unbound, for the builder to
    // bind.
    ScenarioExample(
        request = "When I leave the car, show my Keep note with the shopping list as 2x2 for 15 minutes",
        inventory = listOf(
            "bt Octavia → 00:1A:7D:DA:71:13",
            "widget Single note (Keep Notes) [2x2, 4x2] → " +
                "com.google.android.keep/com.google.android.keep.widget.SingleNoteWidgetProvider",
        ),
        json = """
            {
              "schemaVersion": 1,
              "id": "example-shopping",
              "name": "Car left → shopping list",
              "priority": 700,
              "size": "2x2",
              "conditions": [
                { "type": "bt_disconnected", "mac": "00:1A:7D:DA:71:13", "durationSec": 900 }
              ],
              "content": {
                "kind": "app_widget",
                "package": "com.google.android.keep",
                "provider": "com.google.android.keep.widget.SingleNoteWidgetProvider",
                "appWidgetId": "{{PLACEHOLDER:widget}}"
              }
            }
        """.trimIndent(),
    ),
    // The value the tile shows comes out of the notification through an extract rule.
    ScenarioExample(
        request = "When Packeta notifies a parcel to pick up, show its pickup code for a day; a tap opens the app",
        inventory = listOf("app Packeta → cz.zasilkovna.zasilkovna"),
        json = """
            {
              "schemaVersion": 1,
              "id": "example-parcel",
              "name": "Parcel → pickup code",
              "priority": 700,
              "size": "2x2",
              "conditions": [
                {
                  "type": "notification",
                  "package": "cz.zasilkovna.zasilkovna",
                  "contains": "pick up",
                  "extract": [ { "name": "code", "after": "pickup code", "kind": "number" } ],
                  "durationSec": 86400
                }
              ],
              "content": {
                "kind": "template",
                "layout": "text_only",
                "text": "Pickup code {notif:code}",
                "text2": "Packeta",
                "items": [
                  { "label": "Packeta", "action": { "kind": "app", "package": "cz.zasilkovna.zasilkovna" } }
                ]
              }
            }
        """.trimIndent(),
    ),
)
