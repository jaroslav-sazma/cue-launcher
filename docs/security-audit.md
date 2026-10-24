# Security audit

**Subject:** Cue: (`app.cue.launcher`) 1.0.0 · **Date:** 2026-10-11 · **Method:** source review of
the whole `app/` module, the merged release manifest, the Home Assistant blueprints, the build and
CI configuration, the resolved release dependencies and the repository history, with the
conclusions pinned down by unit tests and the import preview checked on an emulator.

**Result: no open findings.** Every area below is described as it stands today; the residual risks
that remain are listed explicitly at the end, as are the things this review did not cover.

## What was reviewed, and where it stands

### 1. Entry points other apps can reach

Every component in the merged manifest was traced from the entry point to the effect.

| Surface | What a foreign app can do | Control |
|---|---|---|
| `HaEventReceiver` (exported broadcast) | send a Home Assistant event | only the pairing secret is read until it matches, compared in constant time; a broadcast without it is dropped and counted as *rejected*. See below for what is read and how much of it |
| `MainActivity` `VIEW` / `SEND` intent filters | hand over a scenario or configuration file | narrowed to `application/json` and `content:`, without `BROWSABLE`; the same rule is applied again in code, since an explicit intent skips the filters. The file lands in a preview the user confirms, nothing is applied on arrival (section 2) |
| `MainActivity` `HOME` / `MAIN` filters | start the launcher | it comes to the front and closes open overlays — what pressing Home does; background-start limits apply to the sender |
| `CueNotificationListener` (exported service) | nothing | guarded by `BIND_NOTIFICATION_LISTENER_SERVICE`, a signature-level permission only the system holds |
| `ProfileInstallReceiver` (exported, from `androidx.profileinstaller`) | nothing | guarded by `android.permission.DUMP`, which only the shell and the system hold |
| `BluetoothDisconnectReceiver` (exported broadcast) | nothing | `ACL_DISCONNECTED` is a protected broadcast no app can send, and the receiver drops any other action; it has to be exported because the Bluetooth module sends it under its own uid, not the system's, and an unexported receiver never hears it; the component is disabled entirely while no enabled scenario starts or ends on a disconnect and no running tile waits for one to end it |
| `NextAlarmReceiver` | nothing | not exported; only the system broadcasts `NEXT_ALARM_CLOCK_CHANGED`; the broadcast is only a wake-up — the alarm time is read back from `AlarmManager`, never from the intent; disabled while no enabled scenario starts or ends on an alarm and no running tile waits for one to end it |
| `AiChatFileProvider` (`FileProvider`) | nothing | not exported; opens only for the app a send grants a one-off read of the one cached AI chat description, see section 11 |
| `ZenModeStatusReceiver` | nothing | not exported; the system sends `AUTOMATIC_ZEN_RULE_STATUS_CHANGED` only to the package that owns the mode; it only removes the launcher's own Do Not Disturb mode once that is off |

`HaEventReceiver` has to stay exported — the Home Assistant companion app is a separate process and
would not reach an unexported one. A signature-level permission cannot work either, since the
companion app is signed by someone else, and a `normal` one would be granted to any app that asks.
The pairing secret is therefore the control: a 16-character value drawn from `SecureRandom` — the
first character from 24 letters so that it can never be digits only, the rest from a 32-symbol
alphabet, about 79 bits — which the launcher generates, shows in its settings and expects in every
event. It is filtered out of the event fields, so it never reaches a tile template or the event log.
The field is editable so a reinstall can take over the secret existing automations already carry;
anything shorter than 8 characters, carrying a character the companion app splits on, whitespace,
non-ASCII, or digits only, is refused.

What the receiver reads is bounded because every extra is the sender's to choose. The generic
`Bundle.get` would instantiate whatever class a value names — an unknown `Parcelable` throws out of
`onReceive` and takes the process down, a `Serializable` runs its own `readObject` — so it is not
used at all. Before the secret matches, only the secret and the code are read, through the typed
`getString`/`getInt`-style getters, which cast what unparcelling already produced and leave a
`Parcelable` or `Serializable` undecoded; an extras bundle that cannot be unparcelled at all drops
the broadcast. After the secret matched, fields are read the same way and only as text, numbers or
booleans: at most 32 of them, keys of up to 64 characters, values cut at 1024, the code at 64, and
an icon name longer than 64 characters dropped.

An event only ever creates or removes a tile. Tile actions run when the user taps them, never on
arrival, so no path exists from an incoming broadcast to an executed action.

The one thing an event reaches out for is a read. When an event that passed its checks — a Home
Assistant broadcast with the right secret, or a notification some app posted that matches a trigger
— fires a scenario gated by an `ha_state` condition, the launcher reads the states of the entities
the fired scenarios' conditions name, and no others, over REST from the configured instance with its
credential, as one bounded read that is cancelled after three seconds. The sender picks neither the
entities, the address nor anything in the request; it can only make the read happen. A state read
successfully is reused for 30 seconds, and events that follow one another within five seconds share
one reading, so a burst of events or a notification reposted every second does not become a request
per event.

### 2. Untrusted file import

Scenario and configuration JSON can arrive from any app. The launcher reads it only from a
`content:` URI — a `file:` path would be read with the launcher's own rights rather than a grant the
sender holds — and an intent whose stream extra cannot be unparcelled is ignored. Reads are capped
at 4 MB, a file brings at most 500 scenarios (the rest are counted, never decoded), parsing runs off
the main thread and is failure-tolerant, and the preview is a lazy list, so a hostile file can
neither exhaust memory nor freeze the home screen.

The preview lists accepted, rejected, skipped and incomplete entries, and for every scenario what
its tiles do: each action in full rather than its label; a Home Assistant call with its service, its
target and every `*_id` key of its data in full, the rest of the data shortened; a link parsed by
the same code the executor uses, its target app, action, address and fallback shown before the raw
text, so padding inside an `intent:` link cannot push the target out of view, and a link the
launcher would refuse or cannot parse marked as such. A Home Assistant call that will run without
the confirmation prompt is marked in red, because the file — not the importing device — decides that
flag. Each scenario that pulls values out of notifications names the app it reads and every rule.
A whole configuration additionally names what it replaces: the gestures and the actions they run,
the hidden apps, icon overrides and the media widget's player. Nothing is written before the
user confirms. Scenarios that still contain unresolved placeholders are stored disabled, and a
concrete `appWidgetId` in a file is treated as a placeholder, so an import cannot point a tile at a
widget the launcher already hosts. Nothing in an imported file can overwrite the Home Assistant
addresses, credential or pairing secret — those are not part of the format.

### 3. Actions built from scenario data

Scenario data decides what a tap does, so it was checked for what it can reach. Activity launches go
through `ComponentName`, where Android's export rules apply to other apps' components and a
`SecurityException` is caught and reported as a failed action; the launcher's own only activity is
the exported `MainActivity`, so nothing internal is reachable that way. Deep links reject the
`file:` scheme. A deep link may also be a serialized intent (`intent:` or `android-app:`), which is
parsed back into the intent it describes instead of being opened as an address — that is the only
form that reaches an app with a package and extras attached, and it is the form Home Assistant hands
out. Because such a string can come from anywhere, the parsed intent is cut down before it is
started: URI permission grants (`FLAG_GRANT_*_URI_PERMISSION`) are cleared, so the target cannot be
handed access to data only the launcher can read; the selector is dropped, since it would replace
the target after it was checked; and an intent whose package, component or data points at `file:` or
back into the launcher itself is refused outright. The `browser_fallback_url` an intent may carry is
followed only when the target app is missing, and only when it is `http://` or `https://`. Contacts,
calendar entries and the drawer's web and assistant rows open fixed intents built from the row, not
from free text, and so does *About → Report a bug*: it opens GitHub's new-issue form in the default
browser, with only the device model, the Android build and the app version in the address.

Shortcut and capability launches go through `LauncherApps`, which enforces its own rules. A shortcut
that comes from an app's own picker is never executed as the intent that app handed back: the modern
result is accepted only as a `PinItemRequest` the launcher pins and stores by package, id and
profile, and a legacy result is mapped into a URI or a component, and only when it carries no extras
and no action beyond `VIEW`/`MAIN` — anything else is refused, and so is any legacy result of a work
app. Template values from an event payload are substituted into tile *text* only — the action of a
tile is never templated, so payload data cannot redirect where a tap goes.

An app, shortcut or capability action can name the work profile, picked or imported alike. It then
starts only what `LauncherApps` starts there — an app's main activity, its shortcuts and its
capabilities — which is the reach the drawer already has over work apps; links and app screens go
out as intents and never carry a profile. The work profile is found by its user type, so a clone
profile some phones show to the launcher never receives a work action, and a phone without a work
profile reports the target missing. The import preview names such an action with *(work)*.

A notification condition can pull values out of every matching notification, by rules that come
from scenario data and possibly from an imported file. A rule is not a pattern language but a short
description — an optional text the value follows, matched as whole words, one of eight fixed
kinds, a name — read by plain character scans over at most the first 2000 characters of title and
body, with no regular-expression engine anywhere on that path. Its cost is bounded by the text and
a 64-character anchor whatever the rule says, so no rule, typed or imported, can make the
evaluation backtrack or stall a thread. At most four rules run per notification, each value is
capped at 200 characters, and a rule whose name would overwrite the notification's own title or
text is not run. The sample a rule is built from in the builder is held only by the open form, and
the text a built rule looks for is never taken from the sample's title line, so a sender's name
does not end up stored in a scenario.

Two features send another app's own `PendingIntent`. The inline reply on a notification tile sends
the text only from the reply dialog's *Send* button; the notification is looked up again by the key
the listener gave it, neither an import nor an event can set that key, and the reply text is neither
stored nor logged. The media widget opens the player's session activity, described next.

The media widget is the one place where another app's data steers the launcher without a scenario in
between: the session of the player picked in its settings supplies the title, the artist, the cover
and the activity a tap opens. The widget reads no other app's session. The cover is taken as a
bitmap from the session, or read from the URI the session names. That read runs with the launcher's
identity, which can reach further than the player's — the launcher may hold contacts or calendar
access the player lacks — so a `file:` URI is never read, and a `content:` URI only when
`checkUriPermission` confirms the player's own uid could read it; `android.resource:` points at
public resources. Every cover is scaled down to at most 1024 px on its longer edge, because a
player's bitmap is bounded by nothing but the player, and one too large for a canvas would bring the
home screen down. Covers are kept in memory only while the widget is shown and never stored. The
activity a tap opens is the player's own `PendingIntent`, used only when it really is an activity:
it runs with the player's rights, the launcher adds no fill-in intent to it, and the only thing it
lends is its permission to start an activity while it is on screen. The playback buttons send
nothing but the standard transport commands to that one session.

### 4. Home Assistant transport and credentials

Addresses are parsed the way the HTTP client will parse them, with `java.net.URI` — and, for a host
name that class does not accept (an underscore, or a non-ASCII name, which is converted to its ASCII
form first), by hand as nothing but `name[:port]` — and refused wherever the two could disagree:
user info, a query, a fragment, a backslash, whitespace, an empty host or an empty or out-of-range
port each let a string read as one host to a validator and connect to another
(`http://evil.example?.local`). The address away from home is rejected unless it is `https://`. A
cleartext `http://` address is rejected in both fields unless its host is a local one — a `.local`
name, a single label starting with a letter, or a private, loopback, link-local, IPv6 unique-local
or carrier-grade-NAT address, the last of which is where a Tailscale address lives. A numeric host
counts as local only in its canonical dotted form, since the resolver reads `134744072` or
`010.010.010.010` as a public address. A public host over `http://` cannot be saved at all: being at
home says nothing about a connection that leaves the network. This is also what an address coming
from the mDNS record of a discovered instance has to pass — anything on the network can publish such
a record, so what it offers is validated exactly like typing, and the user still confirms it with
*Save*.

A cleartext local address is then used only while the device is recognised as being at home — the
Wi-Fi network is one the user listed, matched by SSID or BSSID, or the instance the user paired
through the system picker answers on the local network, matched by the `uuid` it publishes. Off that
network the address is dropped rather than tried, and the cache of "which endpoint worked last"
cannot reinstate it. The same rule governs the live channel and *Test the connection*: a test
reports a cleartext home address as untried rather than probing it, so the test cannot leak what the
rule protects. A cleartext home address with neither a listed network nor a paired instance cannot
be saved. An `https://` home address carries no such restriction, since TLS authenticates the host.

Two kinds of credential exist. A **signed-in session** is the preferred one: the OAuth flow of Home
Assistant's own auth API, with the callback on a loopback socket so no redirect ever leaves the
device, a random `state` checked on return in constant time, and only the refresh token kept —
access tokens live 30 minutes, are held in memory and are fetched as needed. Any app on the device
may connect to a loopback port, so the callback socket treats a request without that `state` as
noise: it is dropped and the wait goes on. Each connection gets 5 seconds in total and its request
line at most 8 KB, so neither a connection that drips bytes nor one that never sends a line end can
hold the socket or exhaust memory. The matching callback alone is answered; the rest of its request
is then read off and dropped, for at most one second and 64 KB, so that closing the connection does
not reset the page the browser shows. Only three things end the wait: the matching callback, the
180-second deadline, or cancelling, which closes the connection being read as well as the socket. No
number of junk connections ends it early; each is followed by a 30 ms pause, which is what bounds
the work they cause. A **long-lived token** remains available for users who prefer it. Either is
sealed at rest with an AES-GCM key generated in the Android Keystore, with a fresh IV from the
Keystore on every seal and no plaintext fallback, is sent as a bearer header, and is never written
to a log or an export.

A session that is replaced — by signing in again or by saving a long-lived token — is revoked on the
instance after the new credential is stored, and so is the session of a connection the user removes.
If no address answers at that moment, the user is told that the session is still valid in Home
Assistant and where to remove it.

TLS is left to the platform: there is no custom `TrustManager`, no `HostnameVerifier` and no
certificate handling of the project's own, and the user certificate store is not trusted.
Certificate transparency, on by default for apps targeting Android 17, is deliberately left on. A
service call goes out on a client with the OkHttp engine's silent retry on a failed connection
switched off, so whether it may be tried again is decided in one place only — never once it may have
left the phone. Reads and the token requests keep that retry: repeated, none of them can do anything
twice on the instance.

The blueprint is **uploaded** over the WebSocket API with `blueprint/save`, on an explicit tap only,
with the credential the launcher already holds. The request is built in code — domain `automation`,
one of the two YAML files packaged in the APK (English or Czech, picked by the app's locale), and a
path of `cue/launcher_event_<code>.yaml`, where the code is eight hex characters of a salted SHA-256
of the app-scoped Android ID — so the one file it can ever write is that phone's own blueprint, and
overriding it replaces nothing but the phone's earlier upload. The Android ID itself never leaves
the device. Two values from the device go into the YAML: the pairing secret, as the default of its
input, used only once it passed validation and so carrying no whitespace; and the device name,
stripped of control characters and escaped inside the quoted title. Neither can add keys of its own.
Nothing is written on the device. The upload picks its address by the same rule as every other call.
Home Assistant accepts the call from an administrator only; the launcher reports the refusal and
does nothing else.

The action form only reads: `/api/states`, then `/api/services` once the states came through, over
REST and, when the app is in Czech, the service names over the WebSocket API with
`frontend/get_translations`, with the credential the launcher already holds. The condition form
reads `/api/states` the same way and, to show it live, the state of the one entity typed into it
from `/api/states/<entity_id>`, each time the typed id has settled. The lists pick their address by
the same rule as every other read; the names are asked once, of the address listed first, and the
socket closes once the answer is in or after five seconds. The names are kept in memory for up to a
day per instance, address and language, and are used for nothing but labelling a menu row. Creating
a scenario in an AI chat reads the same two lists over REST, never the names: for the description
the services only when the entities answered, for the check of a pasted answer only when it calls a
service, and both are held with the rest of its inventory only while its screen is in front, for at
most two minutes (section 11).

Local network access is a permission of its own since Android 17. The launcher asks for it only when
the configured address is a local one, and an instance picked in the system picker does not need it
at all — that approval covers the one service the user chose, which is less reach than the
permission grants.

A Home Assistant action can be marked to require confirmation before it is sent. The check is the
system's `BiometricPrompt` — Class 3 biometrics, with the screen lock as the fallback — so the
launcher receives a yes or a no and nothing else: no biometric data ever reaches the app, nothing
about the check is stored, and a passed prompt is not cached, so every tap asks again. *Test it now*
in the builder goes through the same prompt for a marked action. It fails closed: on a device with
neither biometrics nor a screen lock the marked action is refused with an explanation rather than
let through, and the prompt fires only on a user gesture — no automated path reaches a marked
action. What it is not is a cryptographic bind: the prompt unlocks no key, and the credential sent
to Home Assistant is the same with or without it.

### 5. Data at rest, backups and transfer

Everything lives in the app's private storage under the Android sandbox: a Room database, one
DataStore file and, once a scenario was created in an AI chat, one file in the cache.
`android:allowBackup="false"` keeps it out of cloud and `adb` backup, but for an app targeting
Android 12 or newer that attribute does not stop a device-to-device transfer, so
`android:dataExtractionRules` excludes every storage domain, credential- and device-protected alike,
from both cloud backup and device transfer. Nothing — the sealed credential, the pairing secret, the
event log, stored notification text, the launch history, the home networks — moves to another phone.

What is stored, and for how long:

| Store | Contents | Bound |
|---|---|---|
| scenarios, layout, folders, hidden apps, widget bindings | what the user configured, including Bluetooth MACs and SSIDs in conditions | until changed |
| activations | per active tile its trigger payload: for a notification trigger the title and text, the values its rules pulled out, progress, the reply key and the notification's own key (user, package, id, tag and uid), by which one dismissed while the launcher was restarted still ends the tile — unless the condition has an end event or a state condition under *any* holds the tile, either of which leaves the key out, as a dismissal does not end such a tile; for a notification that ended its last title and text, without a key; for a remembered place its coordinates and the time it was saved; for an event its fields; for a condition with an end event that event as it was when the tile appeared (an app and a text, a Bluetooth MAC or an event code); for a tile an event started, the time it last fired | until the tile expires (60 s by default or the scenario's duration), its end event arrives or it is closed, the notification is removed (without an end event or a holding state), the state holding it stops, or the scenario is deleted |
| notification listener need | one flag: whether a scenario, the badges or a running tile waiting for an ending notification need the listener bound, so a process the system restarts can decide before anything is loaded | until the need changes |
| event log | code, operation and payload of the last events | 100 rows, 10 of them rejected ones; clearable |
| pairing codes seen | code and time | only codes a scenario waits for |
| launch history | per app: last launch, launches from search | one row per app; removed with the app; clearable |
| settings | Home Assistant addresses, sealed credential, plaintext pairing secret, home networks, instance id; widget and appearance settings; whether creating scenarios in an AI chat is switched on | until changed |
| AI chat description, in the app's cache | the scenario format and the inventory described in section 11 | one file, overwritten by the next send |

Contacts, calendar entries, media metadata and covers, notification badge state, the reply text,
Home Assistant entity states, the entity and service lists the action form offers, the Czech service
names, the inventory read for an AI chat and the scenario text pasted back are held in memory only,
and so are the title and text of the posted notifications from apps a scenario listens to, while the
notification listener is bound; other apps' notifications are not kept, once the scenarios are
loaded shortly after the listener binds.

Exports of scenarios and configuration carry no credential, address or secret, Bluetooth MAC
addresses and Wi-Fi names in them are replaced by placeholders, and so is any widget id. What they
do carry as it is — scenario names, notification filters, entity ids and service data, links,
shortcut ids, the layout and the hidden apps — is listed in the README and in `PRIVACY.md`.

### 6. Sensitive data in logs

Every log call was read for what it can print, including exception messages, which often quote the
URL, URI or intent that failed. The release build strips verbose, debug and info logging through an
R8 rule; what remains are warnings and errors, and none of them prints a token, the pairing secret,
a Home Assistant address, an entity id or service name, notification or reply text, a contact or
calendar entry, an SSID, a MAC address, a link, a shortcut id or a cover URI. Exceptions that may
quote such a value are logged by class name only. What is logged is structural: which app or
component an action targeted, and that a broadcast was rejected (at most at the rate rejected rows
are recorded, without the code it claimed). Values pulled from notifications are never logged.
Reading it requires `READ_LOGS` or `adb`, which no ordinary app has.

### 7. Permissions

Every permission in the manifest was matched to the feature that needs it, and the runtime ones to
the place they are requested. `BLUETOOTH_SCAN` is absent on purpose (the launcher only picks from
paired devices), and `QUERY_ALL_PACKAGES` is absent in favour of narrow `<queries>` declarations —
among them `ACTION_ASSIST` and `VoiceInteractionService`, which let the AI chat hand-off find the
assistant role holder (section 11). There is no accessibility service, no background location, no
foreground service, no exact alarm, no SMS or call-log access and no all-files access. All runtime
permissions and special access are requested just in time, and never before the launcher has shown
what it reads, what for and that it stays on the phone: as text beside the button that asks, or
otherwise in a dialog whose confirm button (*Allow* for a runtime permission, the name of the
settings screen for special access) alone leads to the system prompt or screen, next to *Not now*.
That dialog is `ask` in `rememberRequirementResolver`, while its direct `resolve` is kept for
buttons with the rationale already beside them. A denial disables only the feature that needed it.
The install-time ones are `INTERNET`, `ACCESS_NETWORK_STATE`, `EXPAND_STATUS_BAR` and
`USE_BIOMETRIC`. `RECEIVE_BOOT_COMPLETED` is declared only with `tools:node="remove"`, so no library
can make the launcher start at boot; none asks for it today.

### 8. Dangerous API classes

Searched for and not present anywhere in the app: `WebView`, `DexClassLoader` or any other dynamic
code loading, `Runtime.exec` or `ProcessBuilder`, world-readable file modes, external storage, raw
SQL (`@RawQuery`/`SimpleSQLiteQuery`/`rawQuery`/`execSQL`), `sendBroadcast`, and any `PendingIntent`
created by the app. The one content provider of the app's own is the AI chat's `FileProvider`, not
exported (section 11). Every `registerReceiver` passes an explicit export flag, except the read of
the sticky battery broadcast, which needs none. Of those, only the one in `BluetoothConditions` is
exported, for the reason `BluetoothDisconnectReceiver` is (section 1); its actions are all
protected. Reflection is used once, on a fixed system class and method, to open the notification
shade.

### 9. Local denial of service

The event log is capped, and rejected events share one budget of six rows per minute whatever code
they claim, with the code itself cut to 64 characters, so a hostile sender can neither push the
user's real events out of the log nor grow memory, the database or the log with made-up codes. The
table tracking when each pairing code last arrived is only written for codes a scenario actually
waits for. The broadcast receiver reads a bounded amount of a bounded shape (section 1), the import
is bounded in size, count and rendering (section 2), notification value rules by construction
(section 3), the sign-in callback in time and size (section 4), a pasted scenario in characters
(section 11), and every media cover in pixels. The notification badge state is bounded by the
system's own per-app notification limit.

### 10. Release build and supply chain

The release build minifies and shrinks with R8 and is not debuggable. The signing key is not in the
repository: `keystore.properties`, gitignored like the key it points at, drives signing, and without
it the release build comes out unsigned, which is what CI and every other clone get. Derived
benchmark variants are signed with the debug key. The APK carries no dependency list encrypted for
Google; the bundle for Play does.

One key — RSA 4096, valid until 2054 — signs the APKs published on GitHub. The release on Play is
planned with the same key, enrolled in Play App Signing, so every channel delivers the one
certificate the README publishes under *Verifying a download*. A copy of the key will then sit with
Google; it lets Google sign what Play delivers, and nothing else. Whoever obtained the key from the
developer could sign an APK that installs over a genuine copy outside Play, but could not publish
to Play without the developer's account.

The history of every ref was searched for keystores, property files, private keys, tokens and
passwords; none was ever committed. The fixtures contain no real addresses or networks.

The release runtime has no analytics, crash reporting or advertising code, which the R8 mapping of a
release build confirms, and the resolved release dependencies hold no Play Services or Firebase
library. The app's network library is Ktor over OkHttp, used by the Home Assistant code alone. The
Compose UI and Material 3 libraries ship as alpha releases, a deliberate choice for the Material 3
Expressive components; they run no network code.

A library's own `<provider>` or `androidx.startup` initializer would run in every process before
any app code, so a Gradle task wired into `check`, `verifyDebugMergedManifest`, fails the build when
the merged manifest gains either outside a short allowlist; a dependency update cannot bring a
self-starting entry in unnoticed.

CI builds debug and release, runs Android lint with its security checks promoted to errors and no
baseline, the unit tests, detekt, `verifyDebugMergedManifest` and the dependency license check. Its
token is read-only, every action is pinned to a commit hash, and on `main` the resolved dependency
graph is submitted to GitHub so that Dependabot alerts cover the Gradle dependencies; Renovate
proposes updates and flags those an advisory names. The Gradle wrapper is pinned by checksum.

### 11. AI chat hand-off and pasted scenarios

Creating scenarios in an AI chat is off by default, behind a switch in the Appearance & features
tab. The switch, the user's choice of what the description may include and the flag that keeps the
scenario list's card for a pending answer live in the settings store, which is kept out of backups,
transfers and exports; the configuration export is built field by field from `LauncherConfig`, which
has no field for any of them. Switching the feature off clears the flag. The launcher contains no
model and no AI client, and the only connection the feature opens is the one to the user's own Home
Assistant, when one is configured, to read its entity and service lists over REST (section 4): for
the description only while the user leaves that part ticked, for the check of a pasted answer
regardless of it. On the user's tap it writes a description of the scenario format and of the phone,
hands it to another app, and later reads back what the user pastes.

- **What the description holds.** The scenario format, fixed guidance and worked examples, and an
  inventory read on the user's tap while the screen is in front, never before it: the hand-off
  screen opens on the choice and reads nothing until *Open the AI chat* or *Show prompt preview*;
  the preview shows the description in a sheet of that screen, writes no file and hands it to no
  app. The labels and packages of the launchable apps, each app's verified links and at most four
  labelled exported activities, the widget providers the picker offers, and the system actions the
  phone cannot run (`tv_remote` without the Google TV app) always go: the apps define them and they
  carry no personal data. Three parts go only while ticked, all three ticked at first: the apps'
  shortcuts with the capabilities they carry, among them shortcuts to contacts and conversations
  whose labels may carry contacts' names, as the checkbox says; the names and MAC addresses of
  paired Bluetooth devices; the ids and names of the Home Assistant entities and the services the
  instance offers, only with a connection that answers. The Bluetooth part is offered only while the
  Bluetooth permission is already granted and the Home Assistant part only while a connection is
  configured, both looked up again on every start of the screen, which requests no permission; the
  stored choice for a part not offered is kept. A part unticked or not offered is never asked of the
  system — `DeviceInventory` reads no Bluetooth device and makes no request to Home Assistant for
  it, and without shortcuts `AppActionsRepository` makes no `LauncherApps` shortcut query: the
  shortcut pickers it keeps off the screens are found through the package manager alone, and the
  capabilities, which come from the shortcuts, are not read either. The description says the user
  chose not to share a part, in wording apart from that of a part that is not offered or could not
  be read, so the chat neither invents a value nor asks for one. The cached inventory is keyed by
  the parts read, so a snapshot that kept a part back never stands in for the whole phone, and an
  app's actions read without shortcuts are cached apart from a full read, so neither stands in for
  the other. Nothing is registered for it: the app list is read once, its callback and receiver
  ending with the read, the in-app actions are read at most four apps at a time and for at most ten
  seconds, and leaving the screen cancels a read under way and clears the cached inventory. A read
  is reused only within one screen and for at most two minutes — the description read again to be
  saved, a pasted answer checked again — so the check of a pasted answer reads the inventory again
  rather than share the description's read.
- **How it leaves the app.** Only on the user's tap, as an explicit `ACTION_SEND` addressed to one
  package, and only once `queryIntentActivities` confirms that package takes the `text/plain` share.
  The package is the assistant role holder, read from the `assistant` secure setting or, where that
  cannot be read, the single handler of `ACTION_ASSIST`; the Google app is replaced by the Gemini
  app, which is also the fallback for a holder that takes no share or fails to start. The apps are
  visible through `<queries>` for `ACTION_ASSIST`, the `VoiceInteractionService` interface and the
  Gemini package, not through `QUERY_ALL_PACKAGES`. The file goes as a `content:` URI with
  `FLAG_GRANT_READ_URI_PERMISSION` and a matching `ClipData`, so the read grant names that one app.
  If the file cannot be written, or no candidate takes it, nothing is sent: the user may save the
  file through the system's document picker, or copy the short message that goes with it, which
  holds no inventory.
- **The file provider.** `AiChatFileProvider`, a `FileProvider` with the authority
  `<applicationId>.aichat`, is not exported and only grants URI permissions. Its path
  configuration covers the `aichat/` directory of the app's cache, which holds the one file
  `cue-scenario-context.md`, overwritten by every send. No app can open it without a grant, and the
  only grant the launcher issues is the one-off read that goes with a send. It always reports
  `text/plain`.
- **Pasted text is untrusted input.** The clipboard is read only when the user taps *Paste from
  clipboard*, and the field takes at most 32,768 characters, saying so when it cuts a longer text.
  The first `json` code block is cut out or, without one, the first whole `{…}` object that parses —
  a brace left open before a key marks an object cut short, whose fragments are not taken — and
  passes exactly the import gate of section 2, and it must hold exactly one scenario: a set of
  several is refused and stays with the file import and its preview. Then a grounding check holds it
  to the whole phone, what the description kept back included: the packages a tile opens, or a
  notification, notification-ended or media condition watches, must be launchable apps; a MAC must
  be a paired device; an entity must be one the instance lists; a Home Assistant service must be one
  the instance offers whose target filter, applied as Home Assistant's own action editor applies it,
  takes the entity — a script's own service takes any entity, any other service without a target
  takes none, and without the services list the entity's domain decides; a shortcut, capability or
  activity must be one its app offers; an app widget's provider must be one the picker offers. A
  list that could not be read is not held against the candidate, and neither is a placeholder,
  except in a package a tile opens. A scenario with a placeholder or a widget id arrives disabled,
  the widget unbound, exactly as an import does.
- **Disclosure before anything is saved.** A candidate that passes shows a summary, notes in fixed
  strings, and the import preview's own disclosure lines: every action in full, a Home Assistant
  call with its service, target and ids and marked when it runs without confirmation, a link parsed
  by the executor's code, and the notification rules with the app they read. It opens on the
  builder's summary step and is saved only when the user saves it there; what it runs then goes
  through the checks of section 3. A candidate that fails gets its problems in fixed strings that
  name the offending identifier, and the correction text for the chat is put on the clipboard only
  when the user taps *Copy fix for the chat*. None of this writes a log line.

## Residual risks

These are known, accepted and unfixed, because fixing them would cost more than the risk they carry.

- **"At home" can be impersonated.** A cleartext home address is used when the Wi-Fi name or BSSID
  is one the user listed, or when the paired instance answers on the local network by its `uuid`.
  None of these is secret: an SSID can be copied by anyone who raises an access point, and the
  `uuid` is published on the home network to anyone who was on it. An attacker who sets either up on
  another network, and answers the address there, receives the credential in the clear. It takes a
  targeted attacker who already knows the home network, it concerns only an `http://` home address,
  and an `https://` home address removes it entirely. Since either answer is enough, a `BSSID:`
  entry does not raise the bar while an instance is paired. The README says so where the fields are
  described.
- **The pairing secret is readable with root or `adb`.** It is stored unencrypted in the app's
  private storage: it authorizes nothing outside the device, it has to be displayed so it can be
  carried over to a new phone, and sealing it in the Keystore would mean a keystore failure silently
  killing every event instead of prompting for the value again. On the Home Assistant side it sits
  in plain text in the uploaded blueprint and in any automation that carries its own copy, which is
  where Home Assistant keeps every automation input.
- **Uploading the blueprint needs an administrator credential on the phone.** Home Assistant writes
  blueprints for administrators only, so a user who wants the upload keeps an administrator's
  refresh token or long-lived token in the launcher. Stealing that credential then means full
  control of the instance rather than the service calls the launcher makes. It is sealed like any
  other credential, and nothing else the launcher does needs those rights.
- **A blueprint follows the phone, not the secret.** Its file is keyed by the app-scoped Android ID,
  so a new phone or a build signed with another key uploads a second blueprint, and automations
  built from the first one no longer follow a changed secret. The README says so; a stale default
  only makes events fail the secret check, it never lets a foreign one through.
- **A hand-typed pairing secret can be weaker than a generated one.** The field enforces a minimum
  of 8 characters, not entropy, so a user who replaces the generated value with a memorable one
  lowers the bar themselves. The field exists so that moving to a new phone does not mean rewriting
  every automation.
- **A user's own Home Assistant can send any event.** The secret authenticates the channel, not the
  individual automation. Someone who controls the HA instance can already do more than the launcher
  could.
- **The confirmation on a marked HA action is a gate, not a lock.** It stops a stray tap and a
  passerby at an open home screen. Whoever holds the device unlocked can switch the flag off in the
  builder, and since the prompt unlocks no key, code inside the sandbox is not held by it either —
  both are the unlocked-device and rooted-device cases the threat model already leaves out. An
  imported file decides the flag for its own actions; the preview marks every call that will run
  without it.
- **Notification text reaches the activation store.** A notification that activates a scenario
  persists its title and text in the app's private database until the tile expires. This is the
  feature working as intended; it is bounded by the activation's lifetime and the app sandbox, and
  excluded from backup and transfer.
- **A revoked session may survive an unreachable instance.** Revocation needs the instance to
  answer. When it does not, the local credential is still deleted and the user is told to end the
  session in Home Assistant; until they do, it stays valid there.
- **Another app can slow a sign-in down.** The callback socket handles one connection at a time and
  gives each up to 5 seconds, so a local app that keeps opening silent connections to it delays the
  browser's callback, up to the 180-second deadline. It cannot read the callback or end the wait
  early; the user signs in again, or uses a long-lived token.
- **A long-lived token is never revoked by the launcher.** The user created it and may use the same
  token elsewhere, so replacing or removing it only deletes the launcher's copy; it stays valid in
  Home Assistant until the user deletes it there. The README says so where the token is described.
- **Responses from Home Assistant are not size-capped.** HTTP bodies are read whole and OkHttp's
  WebSocket offers no limit on an incoming message. Only an endpoint the launcher already trusts
  with its credential can send one, which is the Home Assistant case above.
- **An explicit `HOME` intent brings the launcher to the front.** Any app can start it; it does what
  pressing Home does, and background-start limits keep that from being used to interrupt the user.

## What this review did not cover

- **No dynamic testing.** No fuzzing of the JSON importer or the event receiver, no runtime
  instrumentation, no attempt to exploit anything on a live device. Conclusions come from reading
  the source and from tests; the import preview was checked visually on an emulator.
- **No independent vulnerability scan.** Third-party libraries were checked for how they are *used*
  (TLS configuration, retries, serialization settings) and their versions against known advisories
  by hand. The automated scanning set up in CI runs from the repository's first push to GitHub on.
- **No full review of the signed release artifact.** The R8 mapping, the merged release manifest,
  the resolved dependency list and the certificate of a signed release APK were read; a decompiled
  APK and what Play delivers were not, and builds are not yet verified to be reproducible.
- **Nothing on the Home Assistant side.** The blueprint was reviewed as the launcher's contract.
  Hardening an HA instance is out of scope.
- **No cryptographic review.** The construction used is a standard one (AES-GCM with a Keystore key,
  the platform's TLS stack); it received no independent cryptanalysis.

## Tests that hold this in place

The properties above are not left to a future reader's memory:

- `HaEventContractTest` — the manifest, both language versions of the blueprint and the README stay
  in agreement, the two versions differ in nothing but their text, each keeps sending the pairing
  secret and keeps the one input the launcher fills it into, the receiver keeps checking it before
  it delivers, and it never decodes an extra through the generic getter.
- `HaEventRulesTest` — an event is accepted only with a matching secret, never without a stored one,
  the secret never lands in the template fields, and the code, the number of fields, their keys and
  values and the icon are held to their caps.
- `HaEventLogTest` — rejected broadcasts share one budget per window whatever code they claim, a
  flood of made-up codes writes only that budget with every code capped, and an unreadable sealed
  token reads as empty rather than falling back to anything.
- `HaEventSecretTest` — a generated secret never carries a character the companion app splits on nor
  comes out as digits only, and a hand-typed one is validated before it replaces the stored value,
  printable ASCII only.
- `HaConfigTest` — the away address must be `https://`, a cleartext address must be a local one, an
  address that hides its real host behind user info, a query, a fragment or a backslash is refused,
  a numeric host is local only in its canonical form and an IPv6 literal only in a private range, a
  cleartext home address requires a named home network or a paired instance, and an address is
  recognised as local before the launcher offers the permission for it.
- `HaInstanceTest` — what an instance publishes over mDNS is validated like typing, so a cleartext
  address to a host outside the network is never offered to be saved.
- `HaEndpointTest` — a cleartext home address is never selected off the home network, not even from
  the cache, a blocked local connection is told apart from an instance that is switched off, and a
  service call is only ever tried on the second address when the first connection never happened.
- `HaCallbackServerTest` — the sign-in callback reads a request line up to its end and no further
  than its cap, a line that never ends is no request, reading stops when the time is up, the rest of
  an answered request is drained within its own cap and time so the browser gets the whole page,
  only the exact `state` lets the callback through, and hundreds of junk connections over real
  loopback do not end the wait before the real callback arrives.
- `HaSessionTest` — the sign-in callback stays on a client id and a redirect the instance accepts
  without fetching anything, the socket listens on the very host that client id names, and the
  parsed callback keeps its `state`.
- `HaAccessTest` — a long-lived token is never traded for anything, a session's access token is
  fetched before it expires and kept in memory only, a new session drops the token of the old one,
  revocation reports a session no address could end as still valid, and a refusal reads as
  unauthorized rather than as an unreachable instance.
- `HaLiveTest` — the live channel sends the token only when asked, subscribes to the entities the
  tiles name and nothing more, treats a refused token as final, and counts as working only once an
  event has actually arrived.
- `HaBlueprintTest` — the upload saves the phone's own automation blueprint and nothing else, under
  a code that does not reveal the device id, sends the token only when asked, reads a refusal for
  lack of rights as such, and lets neither a secret that failed validation nor the device name write
  anything into the YAML beyond their own value.
- `HaServiceNamesTest` — the request for service names sends the token only when asked, asks for
  nothing but the services' translations, treats a refused token or a failed answer as final, and
  takes nothing from the answer but service names.
- `HaServiceNamesCacheTest` — names read for one instance, address or language are never reused for
  another, and a read that failed is not kept.
- `HaServiceCallTest`, `HaStateRulesTest` — a service name and an entity id have the shape that
  keeps them from reaching into a request path, and only validated entity ids are ever read.
- `ActionRoutingTest`, `ActionFormTest` — a tap asks for confirmation exactly when one of its
  actions is a Home Assistant action marked for it, and the builder's test of a marked action sends
  nothing until the prompt passes.
- `BootManifestTest` — no boot permission and no boot receiver survive the manifest merge.
- `BluetoothDisconnectManifestTest` — the disconnect receiver listens for `ACL_DISCONNECTED` alone,
  stays exported so the Bluetooth module can reach it, and asks its sender for no permission.
- `LauncherConfigTest` — an exported configuration is the same, byte for byte, with the AI chat
  switch on or off, whatever the user chose to share and with or without an answer pending.
- `LauncherSettingsSerializerTest`, `AiChatPendingTest` — the AI chat switch is off by default, the
  choice shares everything until the user changes it, both survive a roundtrip of the settings
  store, a choice stored under an earlier name falls back to the default, and switching the feature
  off clears a pending answer.
- `CandidateCheckTest`, `PastedScenarioTest` — a pasted answer passes exactly the import gate, text
  around the object is ignored, braces in it included, a cut-off object is not replaced by a
  fragment of it, a set of more than one scenario is refused, a placeholder arrives disabled, and an
  answer the gate rejects is not grounded at all.
- `PasteStateTest` — pasted text is cut at 32,768 characters and says so, a check runs only on the
  user's tap or again after a leave cut it short, and an edit cancels a check under way.
- `ScenarioGroundingTest` — invented packages, devices, entities, in-app actions and widgets are
  named, a Home Assistant service is held to what the instance offers and to the entity's target
  filter, unreadable lists and placeholders are not held against a candidate, the services are asked
  for only when a draft calls one on a reachable instance, the check reads the whole phone whatever
  the description kept back, and a shortcut or capability the description left out, and did not
  read, still passes.
- `ProblemMessageTest`, `AiChatMessagesTest` — every problem reads as its own line naming what the
  phone lacks, and the correction asks for the whole scenario again.
- `AiChatContextTest` — the description names every part of the format, its examples pass the import
  gate, the inventory travels verbatim, what could not be read or is not offered says so, what the
  user kept back says that instead and leaves no line of it behind, and shortcuts kept back take the
  capabilities with them and leave the apps' links and screens in place.
- `AiChatInventoryTest`, `HandoffStateTest` — Bluetooth, Home Assistant and the shortcuts kept back
  or not offered are not read at all, Home Assistant services and the capabilities included, shared
  shortcuts bring their capabilities, the brief is read for what the phone offered at the screen's
  last start, every read starts from the user's tap on send or preview — the re-read of a preview
  the user left open included — nothing is sent but on a send, and a preview reads the brief
  without ever sending it.
- `AiChatHandoffTest` — the assistant role holder is read from its setting, the Google app stands
  for Gemini, which is also the fallback for a holder that takes no share or fails to start, nothing
  is started when no candidate takes it or the file cannot be written, and the share is an explicit
  `ACTION_SEND` to one package carrying the file as `text/plain` with a read grant.
- `CachedInventoryTest` — one inventory read is shared only within its lifetime and is read again
  after a clear, a snapshot that kept a part back never stands in for the whole phone, and an app's
  actions read without shortcuts and a full read never stand in for each other.
- `IncomingDocumentTest` — only a `content:` URI is taken for import.
- `ImportSafetyTest` — a file brings at most its cap of scenarios and the rest are counted, never
  decoded, and a concrete widget id is imported unbound exactly like the exported placeholder.
- `ImportDisclosureTest` — the preview discloses every action of every item, flags a Home Assistant
  call that runs without confirmation, names its target and data, and lists the gestures a
  configuration sets.
- `ExtractAnchorTest`, `ExtractFieldsTest` and one class per kind (`ExtractNumberTest` …
  `ExtractUntilTest`) — every kind reads what it promises in Czech and English text, anchors match
  whole words regardless of case, diacritics and spacing, the 2000- and 200-character caps hold,
  invalid rules never run nor overwrite the title or text, and a worst-case text against a maximal
  anchor finishes at once.
- `InferExtractRuleTest`, `SampleTokensTest`, `ExtractNameTest`, `ExtractMatchTest` — a tapped
  value in a sample becomes a rule that reads exactly that value, checked through the same
  evaluator the runtime uses, and the title line never becomes the text a rule looks for.
- `NotificationRoutingTest` — a notification is reported as a match only when a scenario listens to
  it.

Run them with `./gradlew test detekt`; CI runs them together with the release build and lint.
