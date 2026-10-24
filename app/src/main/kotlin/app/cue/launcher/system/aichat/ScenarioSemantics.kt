package app.cue.launcher.system.aichat

import app.cue.launcher.data.scenario.ConditionType
import app.cue.launcher.data.scenario.EXTRACT_MARKER_MAX_LENGTH
import app.cue.launcher.data.scenario.EXTRACT_MAX_RULES
import app.cue.launcher.data.scenario.EXTRACT_NAME_MAX_LENGTH
import app.cue.launcher.data.scenario.EXTRACT_RESERVED_NAMES
import app.cue.launcher.data.scenario.EndEventType
import app.cue.launcher.data.scenario.ExtractKind
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.data.scenario.placeholderFor
import app.cue.launcher.engine.DEFAULT_INSTANT_DURATION_SEC
import app.cue.launcher.engine.HA_CODE_MAX_LENGTH

// What each value of the format means, keyed by the enum or the serial name itself: a `when` over an
// enum stops compiling and a missing serial name stops the document, so a new condition, content,
// layout, action or field cannot reach the chat unexplained.

@Suppress("CyclomaticComplexMethod")
internal fun ConditionType.meaning(): String = when (this) {
    ConditionType.TIME_WINDOW ->
        "Holds between `from` and `to` on the listed `days`; a window may cross midnight (22:00 to 06:00)."
    ConditionType.BT_CONNECTED ->
        "Holds while a Bluetooth device is connected: the one with `mac`, or any device when `mac` is left " +
            "out. Needs the Bluetooth permission, which Cue: asks for."
    ConditionType.WIFI ->
        "Holds while the phone is on Wi-Fi: `mode` \"any\" for any network, \"ssid\" for the network named " +
            "in `ssid`. Reading the network name needs the location permission, which Cue: asks for."
    ConditionType.CHARGING -> "Holds while the phone is charging."
    ConditionType.AUDIO_OUTPUT -> "Holds while headphones or a speaker are connected."
    ConditionType.MEDIA_PLAYING ->
        "Holds while something plays: in one of `packages`, or in any app when it is left out. A pause of up " +
            "to a minute still counts as playing. Needs notification access."
    ConditionType.RINGER_MODE ->
        "Holds while the ringer is in `mode`: silent, vibrate, normal (ringing) or dnd (Do Not Disturb)."
    ConditionType.CALENDAR_EVENT ->
        "Holds while any calendar event is in progress. Needs the calendar permission."
    ConditionType.HA_STATE ->
        "Holds while the Home Assistant entity `entity` is in one of `states`, such as a TV's media_player " +
            "playing or paused. Cue: reads the state while its home screen is shown, and when an event of " +
            "the same scenario arrives. While Home Assistant cannot be reached or the entity is unavailable " +
            "the condition is unknown, and a tile that depends on it stays hidden, negated or not. Needs " +
            "Home Assistant connected to Cue:, which the user can still do after pasting the scenario."
    ConditionType.TIME_INSTANT -> "Fires at `at` on the listed `days`."
    ConditionType.ALARM ->
        "Fires when the phone's alarm clock rings; a snooze that rings again restarts the countdown. A " +
            "doorbell is not an alarm, it is ha_event."
    ConditionType.BT_DISCONNECTED ->
        "Fires when a Bluetooth device disconnects: the one with `mac`, or any device when `mac` is left " +
            "out. Needs the Bluetooth permission."
    ConditionType.NOTIFICATION ->
        "Fires when the app `package` posts a notification, optionally only one whose title or text " +
            "contains `contains` (any case). `extract` rules pull values out of it for the tile's texts, " +
            "and dismissing the notification ends the tile — unless the condition has an `until`, which makes " +
            "the notification only the start: the tile then ignores the dismissal and waits for its end " +
            "event. Needs notification access."
    ConditionType.HA_EVENT ->
        "Fires when Home Assistant sends an event with this `code` through Cue:'s blueprint automation: a " +
            "doorbell, a finished washer. Fields of the event's payload read as `{ha:field}`, and an end event " +
            "with the same code removes the tile early. Needs no connection from Cue: to Home Assistant, " +
            "only the blueprint set up there with the same code."
    ConditionType.NOTIFICATION_ENDED ->
        "Fires when a notification of the app `package` goes away because the app removed it or the user " +
            "dismissed it, such as Google Maps ending a navigation. The tile reads `{notif:title}` and " +
            "`{notif:text}` of its last version. Needs notification access."
    ConditionType.PLACE_SAVED ->
        "Fires when a tile's remember_place action saves where the phone is. Its tile holds that place: " +
            "show_place opens it, `{place:lat}` and `{place:lng}` read it, and it is forgotten when the tile " +
            "goes. remember_place saves nothing unless a scenario with this condition waits for it."
}

internal fun EndEventType.meaning(): String = when (this) {
    EndEventType.TIME_INSTANT -> "Ends the tile the first time `at` comes on one of the listed `days`."
    EndEventType.ALARM -> "Ends the tile when the phone's alarm clock next rings."
    EndEventType.BT_DISCONNECTED ->
        "Ends the tile when a Bluetooth device disconnects: the one with `mac`, or any device when `mac` is " +
            "left out. Needs the Bluetooth permission."
    EndEventType.NOTIFICATION ->
        "Ends the tile when the app `package` posts a notification, optionally only one whose title or text " +
            "contains `contains` (any case). Needs notification access."
    EndEventType.HA_EVENT -> "Ends the tile when Home Assistant sends an event with this `code`."
}

internal const val END_EVENT_RULES: String =
    "Without `durationSec` the tile waits for its end event however long that takes; a `durationSec` " +
        "alongside is an upper bound, worth adding when the end could slip past unseen, such as a " +
        "notification posted and dismissed again while Cue: could not hear it. An end event equal to the trigger " +
        "only restarts the tile, so tell them apart, as with `contains`: a watch app posting \"connected\" " +
        "starts the tile and its \"offline\" ends it; dismissing \"connected\" does not. The user can always " +
        "close a waiting tile."

internal fun TemplateLayout.meaning(): String {
    val texts = when (this) {
        TemplateLayout.ICON_TEXT -> "one icon captioned by its item's `label`, no texts"
        TemplateLayout.TEXT_ONLY -> "`text` and an optional second line `text2`; the whole tile is one button"
        TemplateLayout.TEXT_TWO_ICONS -> "`text` over two icons, each tappable on its own and captioned by its `label`"
        TemplateLayout.FOUR_ICONS -> "four icons, each tappable on its own and captioned by its `label`, no texts"
    }
    return "size \"${size.jsonName}\", exactly $itemCount ${if (itemCount == 1) "item" else "items"}: $texts."
}

internal fun LauncherWidgetType.meaning(): String {
    val what = when (this) {
        LauncherWidgetType.CALENDAR -> "a month grid with the nearest events"
        LauncherWidgetType.CLOCK -> "an analogue clock with the date and the next alarm"
        LauncherWidgetType.MEDIA ->
            "what the player chosen in Cue:'s widget settings plays, with previous, play/pause and next; " +
                "needs notification access"
        LauncherWidgetType.SEARCHBAR -> "the search bar"
    }
    return size?.let { "$what, size \"${it.jsonName}\"." } ?: "$what; it cannot be a scenario tile."
}

@Suppress("CyclomaticComplexMethod")
internal fun SystemActionType.meaning(): String = when (this) {
    SystemActionType.RINGER_SILENT -> "silences the ringer; Android turns Do Not Disturb on with it"
    SystemActionType.RINGER_VIBRATE -> "sets the ringer to vibrate; ends Do Not Disturb"
    SystemActionType.RINGER_NORMAL -> "sets the ringer to ring; ends Do Not Disturb"
    SystemActionType.DND_ON -> "turns Do Not Disturb on; in a list of actions it always runs last"
    SystemActionType.DND_OFF -> "turns off the Do Not Disturb that Cue: turned on"
    SystemActionType.FLASHLIGHT -> "toggles the flashlight"
    SystemActionType.TV_REMOTE ->
        "opens the remote of the Google TV app, as its Remote tile in Quick Settings does: the user picks " +
            "a TV, then controls it; needs the Google TV app, so never use it when the inventory says it is " +
            "not available"
    SystemActionType.PANEL_VOLUME -> "opens the volume panel"
    SystemActionType.PANEL_INTERNET -> "opens the internet panel (mobile data and Wi-Fi)"
    SystemActionType.PANEL_WIFI -> "opens the Wi-Fi panel"
    SystemActionType.PANEL_NFC -> "opens the NFC panel"
    SystemActionType.OPEN_SETTINGS -> "opens the phone's settings"
    SystemActionType.REMEMBER_PLACE ->
        "reads where the phone is, once, at the tap, and fires place_saved; needs the location permission"
    SystemActionType.SHOW_PLACE -> "opens the place a place_saved tile holds in the maps app"
}

internal fun ExtractKind.meaning(): String = when (this) {
    ExtractKind.NUMBER -> "digits, a group joined by - or by one space before three digits staying with them (482 913)"
    ExtractKind.CODE -> "letters, digits and -, with at least one digit (A7K-29Q)"
    ExtractKind.AMOUNT -> "a sum with an optional sign and a currency before or after it (−€1,249.00, 250 Kč)"
    ExtractKind.TIME -> "H:MM or HH:MM, optionally with seconds (14:05)"
    ExtractKind.DATE -> "d.m.yyyy, d. m. yyyy, d.m., yyyy-mm-dd or d/m/yyyy (12/10/2026)"
    ExtractKind.WORD -> "one word of letters and digits (Albert)"
    ExtractKind.LINE -> "the rest of the line, for a value at the end of its line"
    ExtractKind.UNTIL -> "everything up to the `until` text, for a value in the middle of a line"
}

internal val CONTENT_MEANINGS: Map<String, String> = mapOf(
    "template" to "One of Cue:'s own layouts, `layout` below, with its texts and tappable items.",
    "launcher_widget" to "One of Cue:'s built-in widgets, `widget` below. It has no items: taps belong to the widget.",
    "app_widget" to
        "An Android app widget from a `widget` line: `package` and `provider` as the line spells them. Cue: " +
        "cannot bind a widget from text, so `appWidgetId` is always \"${placeholderFor(WIDGET_PLACEHOLDER)}\" " +
        "and the user places the widget in Cue:'s wizard, at the tile's content, before saving. The size " +
        "is one of those the widget's line lists in brackets; when it lists more than one, ask which.",
)

internal val ACTION_MEANINGS: Map<String, String> = mapOf(
    "app" to "Opens an installed app.",
    "shortcut" to "Runs an app shortcut from a `shortcut` line, `package` and `shortcutId` exactly as listed.",
    "deeplink" to
        "Opens `uri`: a web address, an app link, any URI scheme or a serialized intent; see Links and deep links.",
    "capability" to "Runs an app capability from a `capability` line.",
    "activity" to "Opens a screen of an app directly, from an `activity` line.",
    "system" to "Runs one of the phone actions below.",
    "ha" to
        "Calls a Home Assistant service on the user's instance. Needs Home Assistant connected to Cue:, " +
        "which the user can still do after pasting the scenario.",
)

// A note keyed owner.field wins over the one keyed by the field alone. The owner is the condition
// type, content kind or action kind, or scenario, item or extract for the plain objects.
internal fun fieldNote(owner: String, field: String): String =
    FIELD_NOTES["$owner.$field"] ?: FIELD_NOTES.getValue(field)

private val FIELD_NOTES: Map<String, String> = mapOf(
    "scenario.id" to "any non-empty slug, such as \"evening-lights\"; Cue: gives the scenario its own id on saving",
    "scenario.name" to "a short name in the user's language, shown in Cue:'s scenario list",
    "enabled" to "leave it out; a scenario with a placeholder arrives switched off anyway",
    "priority" to
        "0 or more, default 0; among tiles of the same size the higher one goes further left. Event tiles " +
        "usually 600-900, everyday ones 300-500",
    "pinnedPosition" to
        "leave it out unless the user asks to pin the tile: 0, 1 or 2 for 1x1, 0 for 2x2, never on 4x2",
    "size" to "must fit the content (see Content); \"${TileSize.OVERLAY.jsonName}\" is reserved and refused",
    "match" to "\"all\" (default): every condition must hold; \"any\": one is enough",
    "conditions" to "an empty array makes a tile that always shows",
    "content" to "exactly one",
    "negate" to "default false; true turns the condition around",
    "durationSec" to
        "how many seconds the tile stays after the event, more than 0. Left out, it is " +
        "$DEFAULT_INSTANT_DURATION_SEC; ask the user how long rather than leave it out. With `until` it is " +
        "only an upper bound, and left out the tile waits for the end event",
    "days" to "ISO weekdays, 1 = Monday to 7 = Sunday; default every day",
    "until.days" to
        "ISO weekdays, 1 = Monday to 7 = Sunday, at least one, as an empty list is refused; default every day",
    "from" to "HH:mm, 24-hour",
    "to" to "HH:mm, 24-hour",
    "at" to "HH:mm, 24-hour",
    "mac" to "the address from a `bt` line, or a placeholder, never asked of the user; leave it out for any device",
    "wifi.mode" to "\"ssid\" needs `ssid`",
    "ringer_mode.mode" to "dnd is Do Not Disturb",
    "ssid" to
        "the network name, required with mode \"ssid\"; ask the user, and use a placeholder only when they " +
        "do not know it",
    "packages" to "packages from `app` lines; leave it out for any app",
    "package" to "a package from an `app` line",
    "app_widget.package" to "the part of a `widget` line before the slash",
    "contains" to "text the notification must contain, any case",
    "extract" to "at most $EXTRACT_MAX_RULES rules, see Values from a notification",
    "code" to
        "the pairing code: a word without commas or colons, not digits only, at most $HA_CODE_MAX_LENGTH characters",
    "ongoing" to "default false; true counts only an ongoing notification (navigation, a running timer)",
    "minLastedSec" to "a notification shown for fewer seconds does not count; 120 skips the flash as navigation starts",
    "layout" to "see Layouts",
    "text" to "the first line; may hold `{namespace:key}` values",
    "text2" to "a second line; may hold `{namespace:key}` values",
    "items" to "exactly as many as the layout takes",
    "widget" to "see Launcher widgets",
    "provider" to "the part of a `widget` line after the slash",
    "appWidgetId" to "always \"${placeholderFor(WIDGET_PLACEHOLDER)}\"",
    "label" to "a caption in the user's language, about 12 characters fit; may hold `{namespace:key}` values",
    "icon" to
        "\"mdi:<name>\" from Material Design Icons, the names Home Assistant uses (\"mdi:gate\"); leave it " +
        "out to show the icon of the app or the action",
    "item.action" to "one action object, or an array of them run in order on one tap",
    "system.action" to "see System actions",
    "shortcutId" to "the part of a `shortcut` line after the slash",
    "uri" to "see Links and deep links",
    "capability" to "the part of a `capability` line after the slash",
    "component" to "the full class name, the part of an `activity` line after the slash",
    "service" to "domain.service, such as light.turn_on; see the Home Assistant part of the inventory",
    "entity" to
        "an entity id from an `ha` line, or a placeholder, never asked of the user; leave it out for a service " +
        "that takes none",
    "data" to "service fields as a JSON object, such as {\"brightness_pct\": 40}; default {}",
    "confirm" to
        "default false; true asks for a fingerprint or the screen lock before the call. Use it for locks, " +
        "gates, garage doors and alarm panels",
    "ha_state.entity" to "an entity id from an `ha` line, or a placeholder, never asked of the user",
    "states" to
        "one or more states as Home Assistant spells them, any case: [\"playing\", \"paused\"] for a " +
        "media_player, [\"on\"] for a switch, light or binary_sensor, [\"open\"] for a cover, [\"home\"] " +
        "for a person",
    "stateEntity" to
        "an entity id from an `ha` line, or a placeholder, whose state lights the icon in the accent colour " +
        "instead of `entity`'s, such as a door's contact sensor for a lock; leave it out to follow `entity`",
    "extract.name" to
        "fills `{notif:<name>}`: 1-$EXTRACT_NAME_MAX_LENGTH characters of a-z, 0-9 and _, unique, not " +
        EXTRACT_RESERVED_NAMES.sorted().joinToString(" or "),
    "after" to
        "the words the value follows, matched as whole words in any case and without diacritics, at most " +
        "$EXTRACT_MARKER_MAX_LENGTH characters; without it the first value of the kind counts",
    "extract.kind" to "see the kinds below",
    "extract.until" to
        "only for kind until: the text that ends the value, at most $EXTRACT_MARKER_MAX_LENGTH characters",
    "until" to
        "the event that ends the tile, see End events; leave it out for a tile that ends after `durationSec`. " +
        "Use it when the user says the tile should stay until something happens",
)

internal const val WIDGET_PLACEHOLDER = "widget"
