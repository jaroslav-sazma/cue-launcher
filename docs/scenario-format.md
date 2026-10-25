# The scenario format

Cue: keeps every scenario, every backup and every example in this repository in one JSON format,
`schemaVersion 1`. This file is its reference: the fields, the values they take and the checks an
import runs. What scenarios do and how to build them in the app is in the
[README](../README.md#scenarios); this is for writing or reading a file by hand, for tools that
produce one, and for checking what a shared file will do.

The format is a **stable public contract**, because files people keep and share depend on it. Within
`schemaVersion 1` a change may only add — an optional field or a new enum value; removing or
renaming anything needs a new `schemaVersion`, and the app migrates older files when it imports
them. Files are UTF-8 JSON, and export and import are lossless both ways; how the app stores
scenarios inside is not part of the contract.

## Contents

- [One scenario](#one-scenario)
- [Conditions](#conditions)
- [Content](#content)
- [Actions](#actions)
- [Values from a notification](#values-from-a-notification)
- [Envelopes](#envelopes)
- [Placeholders](#placeholders)
- [Import validation](#import-validation)

---

## One scenario

```json
{
  "schemaVersion": 1,
  "id": "uuid",
  "name": "Evening at home",
  "enabled": true,
  "priority": 60,
  "size": "2x2",
  "match": "all",
  "conditions": [
    { "type": "time_window", "from": "18:00", "to": "23:00", "days": [1,2,3,4,5,6,7] },
    { "type": "wifi", "mode": "ssid", "ssid": "{{PLACEHOLDER:wifi_home}}", "negate": false }
  ],
  "content": {
    "kind": "template",
    "layout": "four_icons",
    "text": null,
    "text2": null,
    "items": [
      { "label": "Lights", "icon": "mdi:lightbulb-off",
        "action": { "kind": "ha", "service": "light.turn_off", "entity": "light.living_room", "data": {} } }
    ]
  }
}
```

- `size`: `"1x1" | "2x2" | "4x2" | "overlay"` — `overlay` is reserved and rejected in v1.
- `match`: `"all" | "any"`, default `"all"` — *all at once* and *any of them* in the builder.
  `"all"` accepts at most one instant condition and rejects the scenario otherwise; `"any"` takes
  any number.
- `conditions`: 0..N, joined by `match`. Empty array = static content.
- `priority`: whole number ≥ 0, no upper bound.
- `pinnedPosition`: optional, 0-based index of the slot of a fixed position — `0..2` for `1x1`, `0`
  for `2x2`, never allowed on `4x2` or on the overflow slot of a size. Several scenarios may carry
  the same value; that is a shared position, not a conflict. Absent means floating.

## Conditions

```json
{ "type": "time_window",    "from": "18:00", "to": "23:00", "days": [1,2,3,4,5,6,7], "negate": false }
{ "type": "bt_connected",   "mac": "{{PLACEHOLDER:bt_car}}", "negate": false }
{ "type": "wifi",           "mode": "any|ssid", "ssid": "…", "negate": false }
{ "type": "charging",       "negate": false }
{ "type": "audio_output",   "negate": false }
{ "type": "media_playing",  "packages": ["com.google.android.apps.youtube.music"], "negate": false }
{ "type": "ringer_mode",    "mode": "silent|vibrate|normal|dnd", "negate": false }
{ "type": "calendar_event", "negate": false }
{ "type": "ha_state",       "entity": "media_player.living_room_tv", "states": ["playing", "paused"], "negate": false }

{ "type": "time_instant",   "at": "17:00", "days": [1,2,3,4,5], "durationSec": 60 }
{ "type": "alarm",          "durationSec": 3600 }
{ "type": "bt_disconnected","mac": "{{PLACEHOLDER:bt_car}}", "durationSec": 120 }
{ "type": "notification",   "package": "com.example", "contains": "pickup",
                            "extract": [ { "name": "code", "after": "code", "kind": "number" } ],
                            "durationSec": 300 }
{ "type": "ha_event",       "code": "doorbell", "durationSec": 60 }
{ "type": "notification_ended", "package": "com.google.android.apps.maps", "ongoing": true,
                            "minLastedSec": 120, "durationSec": 1800 }
{ "type": "place_saved",    "durationSec": 86400 }

{ "type": "notification",   "package": "com.example", "contains": "out for delivery",
                            "until": { "type": "notification", "package": "com.example", "contains": "delivered" } }
{ "type": "ha_event",       "code": "washer_done", "durationSec": 43200,
                            "until": { "type": "time_instant", "at": "17:00" } }
```

| Builder | JSON `type` |
|---|---|
| Time window · Bluetooth connected · Wi-Fi · Charging · Headphones or speaker | `time_window` · `bt_connected` · `wifi` · `charging` · `audio_output` |
| Something is playing · Ringer mode / do not disturb · Calendar event · Home Assistant — state | `media_playing` · `ringer_mode` · `calendar_event` · `ha_state` |
| A moment in time (at a set time / at the alarm) · Bluetooth disconnected · Notification from an app | `time_instant` / `alarm` · `bt_disconnected` · `notification` |
| Home Assistant — event · Notification ended · A place is remembered | `ha_event` · `notification_ended` · `place_saved` |

`days` is ISO (Monday = 1). Times are `HH:mm`. `durationSec` and `until` belong **only** to the
seven instant types; `durationSec` must be positive and defaults to 60 unless the condition has an
`until`. `negate` belongs **only** to the nine state types and defaults to `false`. On
`notification_ended`, `ongoing` (default `false`) and `minLastedSec` (default none) are optional and
left out of the file when they are not set. On `ha_state`, `entity` is an entity id and `states`
holds at least one state; both are tidied on import — the entity id and the states trimmed and
lower-cased, empty and repeated states dropped.

`until` is optional and ends the tile by an event instead of the countdown: the tile stays until
that event arrives. In the builder it is the *event* choice under *How long it stays visible*, whose
*At the latest after* sets the `durationSec` alongside. It is left out of the file when it is not
set. It takes one of five types, each with the fields of the condition of the same type; a
`durationSec`, `extract`, `negate` or `until` inside it is ignored like any unknown field:

| `until.type` | Fields | Ends the tile when |
|---|---|---|
| `time_instant` | `at`, `days` (default every day) | the set time comes on one of the days |
| `alarm` | none | the alarm rings |
| `bt_disconnected` | `mac` (optional, none means any device) | a Bluetooth device disconnects |
| `notification` | `package`, `contains` (optional) | a notification from the app arrives, with `contains` in its text when given |
| `ha_event` | `code` | Home Assistant sends that event code |

With `until` the 60-second default no longer applies, so without `durationSec` the event is what
ends the tile — apart from closing it with a long-press → **Close** or a state condition failing
under `"match": "all"`. On a `notification` condition, `until` of any type makes the notification
only the start: dismissing it leaves the tile, which waits for its end, whereas the tile of a
`notification` condition without `until` also ends when the notification that fired it is
dismissed. A `durationSec` given alongside is a safety cap: whichever comes first, the event or the
end of the countdown, ends the tile. A `time_instant` end is fixed as a moment when the tile
appears, so a later time-zone change does not move it. A `notification` end that is posted and
dismissed again while Cue:'s notification listener is not connected is missed; the tile then stays
until it is closed or the cap runs out, which is what a `durationSec` alongside is for. An older
version of Cue: ignores `until` like any unknown field and ends the tile by the countdown.

## Content

| `kind` | Fields |
|---|---|
| `template` | `layout` (`icon_text` → 1×1; `text_only` / `text_two_icons` / `four_icons` → 2×2), `text`, `text2?`, `items` (1 / 1 / 2 / 4 of `{label?, icon?, action}`) |
| `launcher_widget` | `widget`: `"calendar"` (4×2) · `"clock"` (4×2) · `"media"` (4×2) · `"searchbar"` (2×1, static area only). No tap action — clicks belong to the widget |
| `app_widget` | `package`, `provider`, `appWidgetId` — always a **string**, either a placeholder or a bound id as text (`"8"`) |

`icon` is an MDI name (`"mdi:gate"`); `null` means "derive it from the action", which for an `ha`
action is the `icon` of the Home Assistant event that raised the tile, if it brought one, and
otherwise, with an entity, the icon Home Assistant draws for that entity — for `stateEntity` when it
is set — following its state. `label` names the icon: on a `1×1` it is the caption under the chip,
on `text_two_icons` and `four_icons` it sits inside the chip under the glyph. An item without one
shows the glyph alone.

## Actions

`items[].action` is an **object, or an array of objects** executed in order.

```json
{ "kind": "app",        "package": "com.spotify.music" }
{ "kind": "app",        "package": "com.microsoft.office.outlook", "profile": "work" }
{ "kind": "shortcut",   "package": "…", "shortcutId": "…" }
{ "kind": "deeplink",   "uri": "google.navigation:q=Home" }
{ "kind": "deeplink",   "uri": "intent://devices/controller#Intent;scheme=googlehome;package=com.google.android.apps.chromecast.app;end" }
{ "kind": "capability", "package": "…", "capability": "…" }
{ "kind": "activity",   "package": "…", "component": "…" }
{ "kind": "system",     "action": "ringer_silent|ringer_vibrate|ringer_normal|dnd_on|dnd_off|flashlight|tv_remote|remember_place|show_place|panel_volume|panel_internet|panel_wifi|panel_nfc|open_settings" }
{ "kind": "ha",         "service": "light.turn_off", "entity": "light.living_room", "data": {}, "confirm": false }
{ "kind": "ha",         "service": "lock.open", "entity": "lock.front_door", "confirm": true, "stateEntity": "binary_sensor.front_door_contact" }
```

`shortcut` covers both the app shortcuts and the ones picked in the app, `capability` is *What the
app can do*, `activity` an *App screen*, and `deeplink` both *Links into the app* and a custom link.
`entity`, `data`, `confirm` (default `false`) and `stateEntity` are optional on an `ha` action;
`stateEntity` is *Icon state from* and is written only when set.

`profile` is optional on `app`, `shortcut` and `capability`, the only kinds Android lets a launcher
start in another profile. Its one value is `"work"`: the action starts the app in the work profile,
and on a phone without one it reports its target missing. Left out, the action runs in the personal
profile, and that is how it is written — an action of the personal profile carries no `profile` at
all. Any other value is an unknown enum and rejects the scenario.

## Values from a notification

A `notification` condition can carry up to four rules in `extract`, each pulling one value out of
the notification's title and text for the tile's `{notif:<name>}`. The builder writes them from a
sample you tap (see the [README](../README.md#values-from-a-notification)); this is what they mean.

A rule has four fields:

| Field | Required | What it does | Example |
|---|---|---|---|
| `name` | yes | the name the value is written under: a rule named `code` fills `{notif:code}` in the tile texts. `[a-z0-9_]`, at most 32 characters, unique within the condition, not `title` or `text` | `"name": "code"` |
| `after` | no | the text the value follows, matched as whole words regardless of case and diacritics. Without it the rule takes the first value of its kind anywhere in the notification | `"after": "pickup code"` |
| `kind` | yes | what the value looks like and where it ends — one of the eight kinds below | `"kind": "number"` |
| `until` | for `until` only | the text that ends the value, matched as whole words like `after`; it is not part of the value. Ignored and dropped for any other kind | `"until": "."` |

The kinds, each with a notification it reads, the rule, and the value that ends up on the tile:

| Kind | What it reads | Notification text | Rule | Value |
|---|---|---|---|---|
| `number` | digits; a group joined by `-`, or by one space when the group is exactly three digits, stays with them, and so does a minus (`-` or `−`) touching the first digit, unless a word or a number hangs on it (`R-1234`) | `Your pickup code: 482 913. Pickup point: 5 Main St` | `after: "pickup code"` | `482 913` |
| `code` | letters, digits and `-`, with at least one digit; a word without a digit is not a code | `Your code A7K-29Q is ready` | `after: "code"` | `A7K-29Q` |
| `amount` | a sum with an optional sign and a currency before or after it — Kč, CZK, €, EUR, $, USD, £, GBP, zł, PLN, Ft, HUF, CHF; without `after` only an amount that names its currency counts | `Card payment of −€1,249.00 at Albert` | none | `−€1,249.00` |
| `time` | `H:MM` or `HH:MM`, optionally with seconds; hours 0–23, minutes 0–59 | `Train R 1234 departs 14:05 from platform 3` | `after: "departs"` | `14:05` |
| `date` | `d.m.yyyy`, `d. m. yyyy`, `d.m.`, `d. m.`, `yyyy-mm-dd`, `d/m/yyyy`; the day must exist in its month | `Collect it by 12/10/2026` | `after: "by"` | `12/10/2026` |
| `word` | one word — letters and digits, with `-` `'` `.` `_` allowed only inside it | `Card payment of −€1,249.00 at Albert` | `after: "at"` | `Albert` |
| `line` | the rest of the line, without trailing `. , ; :`; for a value at the end of its line | `Pickup point: 5 Main St, Springfield` | `after: "pickup point"` | `5 Main St, Springfield` |
| `until` | everything up to the `until` text, without trailing `. , ; :`; for a value in the middle of a one-line notification. No `until` text found means no value | `Pickup point: 5 Main St, Springfield. Collect it by Friday.` | `after: "pickup point"`, `until: "."` | `5 Main St, Springfield` |

When the value itself contains the `until` text, a `"."` cuts it short: in the notification
`Pickup point: unit no. 5, Springfield. Collect it by Friday.` it stops at the abbreviation `no.`.
Ending the value at the next sentence instead, with `until: "Collect"`, reads
`unit no. 5, Springfield` — and that is the rule the builder proposes for it.

`after` is matched as whole words, regardless of case, diacritics and how many spaces stand between
them (`cafe` finds `Café:` but not `cafeteria`). After it, spaces, one abbreviation dot,
`: - – — # =`, opening quotes and brackets, and at most one line break are skipped, and the value
has to follow right there; a minus or a sign touching the digits of a `number` or an `amount` is
part of the value, not a separator. Every occurrence of `after` is tried in turn. The text read is
the first 2000 characters of title and body; a value is at most 200 characters and comes out exactly
as written. A rule that finds nothing adds no value, and a tile that needs it does not show. Reading
walks the text with no pattern engine behind it, so no rule — picked, typed or imported — can make
it slow.

## Envelopes

- **One scenario:** the object above; it carries its own `schemaVersion`, so a scenario lifted out
  of a set is a valid file.
- **A set:** `{ "schemaVersion": 1, "name": "…", "scenarios": [ … ] }`
- **A whole configuration:** the set plus any of `staticZone`, `appearance`, `drawer`, `gestures`,
  `widgets`, recognised by the fields present. `staticZone` carries the sparse grid, both docks and
  the widget page; positions are never renumbered, so the gap left by an uninstalled app is part of
  the backup. `widgets` carries the settings of the launcher's own widgets that make sense on
  another phone — for now `{ "mediaApp": "com.spotify.music" }`, the player the media widget
  follows. `appearance` carries the icon mode, the per-app icon exceptions and the accent:
  `"accent"` is `primary`, `tertiary` or `custom` — *Main*, *Second* and *Custom* in the settings —
  and `"customAccent"` the custom colour as `"#RRGGBB"`, kept even while another accent is chosen.
  An accent this version does not know reads as `primary`, and so does `custom` without a colour
  that parses.

```json
"staticZone": {
  "grid": [
    { "position": 0, "kind": "shortcut",        "package": "com.android.chrome", "component": "…" },
    { "position": 1, "kind": "folder",          "name": "Tools", "items": [ { "package": "…", "component": "…" } ] },
    { "position": 2, "kind": "launcher_widget", "widget": "searchbar" },
    { "position": 4, "kind": "app_widget",      "package": "…", "provider": "…",
                     "appWidgetId": "{{PLACEHOLDER:widget_8}}", "columnSpan": 4, "rowSpan": 2 }
  ],
  "dock": [ { "position": 0, "kind": "shortcut", "package": "…", "component": "…" } ],
  "widgetPanel": {
    "widgets": [ { "position": 0, "kind": "launcher_widget", "widget": "clock" },
                 { "position": 1, "kind": "app_widget",      "package": "…", "provider": "…",
                   "appWidgetId": "{{PLACEHOLDER:widget_9}}", "rowSpan": 3 } ],
    "dock":    [ { "position": 3, "kind": "shortcut", "package": "…", "component": "…" } ]
  }
}
```

In `widgetPanel.widgets`, `rowSpan` is the height an app widget was given on the widget page — 1 or
more rows, 2 when the field is absent. The page is always 4 columns wide, so there is no
`columnSpan`.

## Placeholders

Anything tied to one phone — an `appWidgetId`, a Bluetooth MAC, an SSID — is exported as
`{{PLACEHOLDER:<key>}}`, and the app asks for it after the import; a scenario with an unfilled
placeholder is imported disabled, not rejected. Placeholder keys are conventionally `wifi_*`, `bt_*`
and `widget_*`, but an unknown key is not an error: a placeholder is a property of any string value,
not a typed field. An imported `appWidgetId` that is a bound id rather than a placeholder is treated
as a placeholder, so a file cannot point a tile at a widget already on this screen. What a shared
file carries, and what it never does, is listed in the README under
[Placeholders and portability](../README.md#placeholders-and-portability).

## Import validation

1. `schemaVersion` — older is migrated, newer is refused with a readable message.
2. Unknown **enum** value — an `until.type` as much as a condition `type` → that scenario is
   rejected, the rest of the set still imports. Unknown **fields** are ignored (forward
   compatibility).
3. At most one instant condition under `"match": "all"`; `durationSec` and `until` only on instant
   ones, and `durationSec` positive.
4. `layout` × item count (1/1/2/4), `layout` × `size`, widget × `size`.
5. `priority` is a whole number ≥ 0, and `pinnedPosition` — when present — is a slot its size can
   actually pin.
6. `id` collisions are **added alongside** with a new id, never overwritten silently. There is a
   preview before anything is written. A pinned position an existing scenario already uses is not a
   collision at all — positions are shared.
7. `items[].action` must carry at least one action.
8. `from` / `to` / `at` must be `HH:mm` and `days` within 1–7, inside `until` too, where `days` must
   not be empty either; `wifi` with `mode: "ssid"` needs a non-empty `ssid`, `ha_state` needs an
   entity id (or a placeholder) and at least one state, an `until` of type `notification` a
   non-empty `package` and one of type `ha_event` a non-empty `code`; `id` must be non-empty.
9. `extract` holds at most four rules; a `name` is `[a-z0-9_]`, at most 32 characters, unique and
   not `title` or `text`; a rule's `after` and `until` are at most 64 characters, and `until` is
   required for the `until` kind and dropped from any other.
10. A file larger than 4 MB is refused as a whole, and one file brings at most 500 scenarios; the
    preview says how many more it held and skipped.
