# Security audit

**Subject:** Cue: React & Act (`app.cue.launcher`) 1.0.0 · **Date:** 2026-10-11 · **Method:** source
review of the whole `app/` module, the merged release manifest, the Home Assistant blueprints, the
build and CI configuration, the resolved release dependencies and the repository history, with the
conclusions pinned down by unit tests and the import preview checked on an emulator. The Mac bridge
(section 12), which came after 1.0.0, was reviewed within the reviews of its development phases,
2026-10-12 to 2026-10-14, by the same method, against `docs/bridge-protocol.md`.

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
| `BridgeCompanionService` (exported service) | nothing | guarded by `BIND_COMPANION_DEVICE_SERVICE`, which only the system holds; declared disabled and enabled only while the Mac bridge is on and paired (section 12) |
| `ProcessTextActivity` (exported, `PROCESS_TEXT` and `SEND` on `text/plain`) | while a Mac is paired, send a text to the user's own Mac clipboard | declared disabled and enabled only while the Mac bridge is on and paired; the text is read through the typed getter alone, an unreadable extras bundle is dropped, the text is capped at 100 KB, and the Mac announces it (section 12) |

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

The home screen reads one thing more, and only while a visible tile has a Home Assistant button
without an icon of its own: Home Assistant's default icons for the domains of those buttons, asked
with `frontend/get_icons` over the WebSocket API, with the credential the launcher already holds,
of the address listed first; the socket closes once the answer is in or after five seconds. The
table holds nothing but icon names per domain, device class and state, and decides nothing but
which glyph a tile draws. It is stored outside the backups, under a SHA-256 digest of the
instance's identity rather than its addresses, and read again only for a domain it was not read for
or when a live channel's handshake with the same instance announces another Home Assistant version.
A refused request counts as an answer with no icons; a read that got no answer is not retried for
15 minutes unless a live channel authenticates in the meantime. The `device_class` and `icon`
attributes the table is matched with come with the state reads the tiles already make.

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
event log, stored notification text, the launch history, the home networks, the Mac bridge's pairing
— moves to another phone.

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
| settings, Mac bridge | the switch, the clipboard choices (whether clips from the Mac are taken, and their age limit) and the package names of the apps kept off the Mac; while paired the Mac's name, address and pinned fingerprint, the companion-device association and `K_ble` sealed with an AES-GCM Keystore key; beside them, in a file of their own, the sequence state — the bound of the outgoing numbers reserved and the incoming replay window — tagged with a hash of the `K_ble` it counts for | the pairing until the bridge is turned off or unpaired, which deletes it together with the Keystore keys |
| Android Keystore | the bridge's TLS identity, an EC P-256 key generated inside it, and the AES key that seals `K_ble` | as the row above |
| AI chat description, in the app's cache | the scenario format and the inventory described in section 11 | one file, overwritten by the next send |

Contacts, calendar entries, media metadata and covers, notification badge state, the reply text,
Home Assistant entity states, the entity and service lists the action form offers, the Czech service
names, the inventory read for an AI chat and the scenario text pasted back are held in memory only,
and so are the title and text of the posted notifications from apps a scenario listens to, while the
notification listener is bound; other apps' notifications are not kept, once the scenarios are
loaded shortly after the listener binds.

The Mac bridge's mirror state, icons, clipboard texts and replies, and the Mac's last reported lock
state, are held in memory only. A clip from the Mac that arrives while the phone is locked is kept,
the newest one alone, until the user unlocks the phone or the companion service's window closes,
and is dropped at the unlock if it has outgrown the user's age limit.

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
are recorded, without the code it claimed). Values pulled from notifications are never logged. The
Mac bridge logs its protocol events under `CueBridge` at debug level, stripped from release, with
message types, key hashes and fingerprint prefixes only, never notification, reply or clipboard
text, a commitment or a key; its warnings name an exception's class only. Reading it requires
`READ_LOGS` or `adb`, which no ordinary app has.

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
`USE_BIOMETRIC`, and for the Mac bridge `REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE`,
`REQUEST_COMPANION_RUN_IN_BACKGROUND` and `REQUEST_COMPANION_USE_DATA_IN_BACKGROUND`, which act
only for an association the user created in the system's own dialog; the system, not the launcher,
scans for the paired Mac. The bridge's runtime permissions are `BLUETOOTH_CONNECT` (*nearby
devices*, usually granted inside that dialog) and `ACCESS_LOCAL_NETWORK`, both requested while
pairing. `RECEIVE_BOOT_COMPLETED` is declared only with `tools:node="remove"`, so no library can
make the launcher start at boot; none asks for it today.

### 8. Dangerous API classes

Searched for and not present anywhere in the app: `WebView`, `DexClassLoader` or any other dynamic
code loading, `Runtime.exec` or `ProcessBuilder`, world-readable file modes, external storage, raw
SQL (`@RawQuery`/`SimpleSQLiteQuery`/`rawQuery`/`execSQL`), `sendBroadcast`, and any `PendingIntent`
created by the app. The one content provider of the app's own is the AI chat's `FileProvider`, not
exported (section 11). Every `registerReceiver` passes an explicit export flag, except the read of
the sticky battery broadcast, which needs none. Of those, only three are exported: the one in
`BluetoothConditions`, for the reason `BluetoothDisconnectReceiver` is (section 1); the Mac bridge
window's adapter-state receiver, because Bluetooth broadcasts may come from the Bluetooth module's
own uid, which an unexported receiver would shut out; and the Mac bridge's `USER_PRESENT` receiver,
because SystemUI sends that broadcast under its own uid. Their actions are all protected, so no app
can fake them; the adapter-state receiver only reads the state the broadcast names, and the unlock
receiver only makes the launcher read the keyguard state itself. Reflection is used once, on a fixed
system class and method, to open the notification shade.

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

### 12. Mac bridge

The bridge connects the phone to the user's own Mac, which runs Cue: Relay & Reply, a menu-bar app
from a separate repository; `docs/bridge-protocol.md` is the normative description of everything
below, and both sides test against the vectors in `fixtures/bridge/`, of which the Bridge repository
keeps a copy that its tests hold byte for byte to this one.

- **Off switch.** The bridge is off by default. `BridgeCompanionService` and `ProcessTextActivity`
  are declared `android:enabled="false"` and switched on by `BridgeComponentGate` only while the
  stored state says on and paired, which also repairs whatever a process death left behind. A
  disabled service is never bound, so with the bridge off no part of it runs, and the scenario
  condition reads only the stored switch, without constructing the presence. Turning the bridge off
  or unpairing deletes the pairing, the sealed key, the TLS identity and the sequence state, and
  removes the companion-device association. The wipe runs in the application's scope and cannot be
  cancelled, so leaving the screen does not cut it short; each step runs even if one before it
  fails, and a new search, pairing or switching the bridge back on waits until it is done, so
  nothing of the old pairing is picked up by the next one.
- **Background execution.** The system binds `BridgeCompanionService` while the paired Mac's
  Bluetooth advertisement is in range; the launcher never scans and holds no wake lock and no
  foreground service. Everything the bridge does without the home screen — the GATT link and the
  L2CAP route read over it, the presence debounce, the short content sessions on either carrier, a
  clip from the Mac held for the unlock and the `USER_PRESENT` receiver registered only while one is
  held, and the receiver of the Bluetooth adapter's state that drops and restarts the GATT link with
  the radio — lives in the service's window and ends with it, and while the debounced presence says
  absent nothing but that adapter-state receiver remains even if the system keeps the service bound.
  The notification listener is held for the bridge only during that window.
- **Presence.** The bridge screen's *In range* and the *Mac nearby* condition rest on the system's
  companion-device presence, which follows the Mac's Bluetooth advertisement: it is not
  authenticated, and a device that advertises the bridge's service under the Mac's address is in
  range as far as they are concerned. *In range, connected over Bluetooth* is authenticated: the
  phone counts its GATT connection as one to the Mac only once a frame from the Mac has opened under
  `K_ble` over it, and only until that connection ends, and only that state makes it ignore the
  system's reports of the Mac leaving. A bare connection to the Mac's address counts as the
  advertisement does, so a device answering under that address cannot hold the Mac present past the
  60-second debounce. The phone writes a `ping` after every subscription and at every session start,
  and the Mac answers its confirmation at once, with a summon or a `ping` of its own.
- **Pairing.** The Mac's user starts pairing mode for 120 seconds, during which the Mac accepts any
  client certificate and adds `pair=1` to its Bonjour record. The phone browses for that flag for at
  most 10 seconds, releasing the browse and every resolve when it ends, and connects over TLS
  accepting any server certificate, whose fingerprint it records: nothing found on the network is
  trusted. The phone sends a SHA-256 commitment to a fresh ephemeral X25519 key; only then does the
  Mac create its own fresh key and send it, and only then does the phone reveal its key, which the
  Mac checks against the commitment in constant time. Both sides derive, with HKDF-SHA256 over a
  transcript of both observed fingerprints and both ephemeral keys, the six-digit code, the frame
  key `K_ble` and a confirmation key. The phone shows the code and the Mac's user types it in, and
  the Mac compares it in constant time; a wrong entry may be retried until the 120-second
  confirmation timer runs out. Because neither side can pick its key after seeing the other's, a
  party in between makes the two codes agree with a chance of one in 10⁶ per ceremony, and each
  ceremony ends pairing mode until the user starts it again; a second `pair-request` meanwhile is
  answered `busy`. Each side's `pair-confirm` carries an HMAC proof under the confirmation key,
  checked in constant time: the phone sends its own right after the reveal, the Mac its own once it
  has accepted the typed code. Each side checks the other side's proof, so neither commits to a peer
  that derived another secret: the phone completes on the Mac's proof, the Mac once it also holds
  the accepted code. An all-zero shared secret, a malformed key or commitment, a reveal that does
  not match, a message out of order or a timeout aborts it, and either way the ephemeral private
  keys are released and the secrets derived from them are zeroed.
- **Sessions over the network.** TLS 1.3 only, mutually authenticated, without SNI, session
  resumption or 0-RTT, so both certificates are checked on every connection. Each side accepts
  exactly the peer's leaf certificate it pinned at pairing, compared by SHA-256 over its DER
  encoding; chains, expiry and host names play no part. The phone's key is an EC P-256 key generated
  inside the Android Keystore. No identity travels before the handshake completes, and the Bonjour
  record carries only the protocol version and, in pairing mode, the pairing flag. When the saved
  address stops answering, the phone resolves the Mac's Bonjour name and keeps the address it gets
  only after the pinned handshake succeeded there, so a spoofed record costs one failed attempt and
  never redirects the pairing. The Mac keeps at most eight network connections in their handshake
  and at most three from one address; a new one is never refused but closes the oldest, first among
  its own address's, so one device on the network cannot hold the slots, and a flood from many
  addresses only delays the phone's connection.
- **Sessions over Bluetooth.** Where TCP does not reach the Mac, the phone opens the LE credit-based
  L2CAP channel whose PSM the paired Mac publishes in a GATT characteristic, as the central, without
  bonding and without link-layer security. Each side sends a fresh X25519 public key, and
  HKDF-SHA256 derives one record key per direction from the shared secret, salted with `K_ble` and
  both public keys. Possession of `K_ble` is the authentication — a party in between can swap the
  public keys but can neither seal nor open a record — and the ephemeral keys give forward secrecy:
  once the record keys exist, the private key is released and the shared secret and the material
  derived from it are zeroed. Records are ChaCha20-Poly1305 with a per-direction counter as the
  nonce, under keys new to every connection; a length out of range, a record that does not open and
  a stream that ends inside one close the channel. An all-zero shared secret aborts the handshake,
  and a peer silent for 15 seconds in it is dropped. A device in radio range can hold the Mac's two
  slots for channels still in their handshake, each until its 15-second timeouts run out: a denial
  of service only, bounded by that cap, which the network's connections do not share, and nothing on
  those channels is readable. The session on top is the same as over TLS, and pairing never runs
  here. Neither the PSM nor the carrier that last worked is stored; the channel is opened only
  inside the scopes that hold a session — a visit, the home screen's session, one *Send to Mac* —
  and closed with them.
- **Bluetooth frames.** Signals are sealed with ChaCha20-Poly1305 under `K_ble`, with the frame type
  as associated data and a nonce made of the direction and the sequence number, so a frame can never
  be accepted by its own sender. A sender takes its sequence numbers from blocks of 64 whose bound
  is persisted before the first of them leaves, so a crash skips numbers but never uses one twice
  under a key. The receiver keeps a 64-entry sliding window — the highest number accepted and which
  of the 63 below it were — persisted with every accepted frame, so frames the two carriers deliver
  out of order are both taken while each number is accepted at most once, and anything further back
  is dropped. `BridgeReplayGuard` is the single place that checks and records it, for GATT and the
  session alike on either carrier, so a frame is accepted at most once however it arrives. The
  sequence state lives in a file of its own, so a frame never rewrites the settings the launcher
  collects, and it is tagged with a hash of the `K_ble` it counts for: a key whose state is missing,
  unreadable or another key's — a commit cut short, a corrupted file — opens no pairing, so nothing
  is sealed or accepted under it until the next pairing, and its counters never start from zero
  again. The GATT characteristics require no link-layer security, which they do not need: a foreign
  central can connect and subscribe, but its writes fail the AEAD, and the Mac counts a subscriber
  as the phone only once a frame that subscriber wrote itself has been accepted — on `doorbell`, or
  as a record on an L2CAP channel it opened — never because of a session or another central's frame.
  An unconfirmed subscriber is notified nothing but the sealed `sync-request` that summons the
  phone, and only while no session is up and no subscriber is confirmed; closings, replies, clips,
  the clip announcement and the Mac's lock state go to confirmed subscribers alone. A subscriber
  whose MTU is below 185 is left out of every audience, so a central with the smallest MTU cannot
  hold back the frames the phone receives.
- **What crosses.** To the Mac: the notifications the mirror filter lets through (no ongoing, group
  summary, promoted, media, non-clearable, low-importance or own notifications, none of an app the
  user keeps off the Mac, and none the lock screen does not show — secret by the app's or the user's
  choice for its channel) with app label, icon, title, text, time and revision; their removal; the
  answer to each reply; text the user sends with *Send to Mac*; a `lock` when the user taps *Lock
  the Mac*; and, in every session's `hello`, whether the user takes clips from the Mac. A
  notification whose content the lock screen redacts — the user's choice for its channel says
  private, or the app's does while the lock screen hides sensitive content — crosses as the lock
  screen shows it: its public version, or the app's name over a generic "content hidden". The
  phone's clipboard is never read by the bridge. From the Mac: closings of mirrored notifications,
  which cancel exactly the matching notification and only the post the user closed — one meant for a
  post the phone has replaced since is ignored; replies, which fill that notification's own
  `RemoteInput` and send its action without any background-activity-launch allowance; clipboard
  text; and whether the Mac's screen is locked. Every closing and reply, a `reply-text` naming its
  full key included, is resolved through the ledger of what the mirror actually sent — the
  session's, or, without a session, the companion window's, which holds what the filter lets through
  — so the Mac reaches no notification it was not shown, and one never mirrored is answered exactly
  like one that is gone, which keeps the rest of the shade from being probed. The Mac sends a clip
  only while the phone's last `hello` took clips and its own user's **Send Clipboard to Phone** is
  on — until a pairing's first session it sends none, and with either switch off it does not read
  its pasteboard at all — and never one marked concealed, transient or auto-generated, which it
  skips without reading the text. The phone applies a clip with the sensitive flag when the clip
  carries it, only while the user takes clips from the Mac, only once the keyguard is down — one
  arriving behind it waits for the unlock — and only while its age, as the Mac reports it plus the
  time it waited on the phone, is within the user's limit of 1 to 60 minutes. The Mac reports the
  age of a clip that waited for a way to the phone on both paths, in the Bluetooth frame as in the
  session message, so a copy that sat on the Mac does not land as fresh. Clipboard text is capped at
  100 KB in both directions.
- **Remote lock.** Locking its screen is the one thing the phone can make the Mac do, and there is
  deliberately no way to unlock it from the phone. The phone sends a `lock` only when the user runs
  *Lock the Mac*, with a tap, a swipe or wherever else they put it; a scenario, imported or not,
  never runs it by itself, and a button whose only action it is greys out unless the Mac is present
  and reports its screen unlocked. The `lock` goes over GATT, the session or a short-lived
  connection of its own, and the Mac answers with its lock state. The phone applies a `lock-state`
  only once it has opened under `K_ble` with a sequence number above that of the last one applied
  under the same key, only while the bridge is on, the Mac it names is the paired one and that Mac
  is present; it forgets the state when the presence ends and when the pairing is wiped, and until
  one arrives the state is unknown and neither *Mac locked* nor *Mac not locked* holds. How the Mac
  locks, and its own **Lock When Phone Leaves**, which locks it once the phone's confirmed Bluetooth
  link has been gone for the chosen delay, are described in the Bridge repository's
  [`docs/security.md`](https://github.com/jaroslav-sazma/cue-bridge/blob/main/docs/security.md).
- **Permissions.** As in section 7: three install-time companion-device permissions and the runtime
  `BLUETOOTH_CONNECT` and `ACCESS_LOCAL_NETWORK`, requested while pairing; a denial stops only the
  part of the bridge that needs it, and the screen says which. Without `ACCESS_LOCAL_NETWORK` a
  paired phone's sessions run over the L2CAP channel alone.

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
- **An app in the foreground can put text on the paired Mac's clipboard.** `ProcessTextActivity` has
  to be exported for other apps' selection menus and the share sheet, so while a Mac is paired any
  app the user has in front can start it with a text of its choosing, which then replaces the Mac's
  clipboard. Background-start limits keep an app out of sight from doing it, and the Mac shows a
  notice for every such text.
- **The paired Mac is trusted as the user's own device.** Whoever controls it reads the mirrored
  notifications, can close notifications on the phone, reply through the phone's apps and set the
  phone's clipboard. That is the feature; the code typed into the Mac is what keeps any other device
  out of the Mac's side of the pairing, and the certificate shown on both sides is how the user
  compares the phone's pin with the Mac.
- **The phone completes on the Mac's proof.** The code is typed into the Mac and compared there. The
  phone's completion does not depend on the code: it pins the certificate of the peer on the pairing
  connection whose `pair-confirm` proof verifies. The code binds the Mac's side, and the certificate
  prefix shown on both sides — in the phone's bridge settings and in the Mac's pairing window — is
  how the two pins are compared.
- **The Mac's Bluetooth advertisement is recognisable.** It carries the bridge's fixed service UUID,
  so a scanner nearby can tell that a Mac running the bridge is in range, though not whose; macOS
  rotates the Bluetooth address. The Bonjour record carries the Mac's host name, which macOS already
  announces on the local network, and while pairing mode is on it tells the network that the Mac is
  waiting for a phone.
- **Mac nearby follows an unauthenticated advertisement.** A device in radio range that advertises
  the bridge's service under the paired Mac's Bluetooth address makes the phone see the Mac as in
  range, and with it the *Mac nearby* condition and the last lock state the Mac itself reported,
  until the advertisement stops and the debounce runs out. It reaches nothing of the bridge: it
  cannot complete a session or report a lock state, is never shown as connected over Bluetooth, and
  the frames the phone writes to it are sealed.
- **A captured `lock` can lock the Mac once.** A device posing as the paired Mac that way receives
  the sealed `lock` the phone writes to the doorbell. It can neither read nor change the frame, but
  the frame carries a sequence number the real Mac has not seen yet, so relaying it to that Mac
  locks its screen once; every further copy is dropped as a replay.
- **Frames far out of order are dropped.** The receiver's window spans 64 sequence numbers, so a
  frame that arrives after 64 newer ones from the same sender is dropped as a replay. A missed
  signal costs only a delay, since every connection begins with a full state-sync.
- **A lost sequence state costs a pairing.** If the phone's sequence file becomes unreadable, or the
  sealed key can no longer be opened, the stored pairing stops working until the user pairs again;
  the bridge screen says *Pairing unusable — pair again* and offers the new pairing.
- **The lock screen's sensitive-content switch is a hidden setting.** Whether the lock screen hides
  sensitive content is read from a secure setting Android lets apps read but does not document.
  Should a release refuse it, an app's own private notifications cross in full, while the user's
  per-channel choices and secret notifications still hold; the refusal is logged.

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
- **No cryptographic review.** The constructions used are standard ones (AES-GCM with a Keystore
  key, the platform's TLS stack, and for the Mac bridge X25519, HKDF-SHA256, HMAC-SHA256 and
  ChaCha20-Poly1305 checked against known-answer vectors); they received no independent
  cryptanalysis.
- **The macOS app.** What the Mac side of the bridge stores, how it is signed and bound to its Mac,
  and its own residual risks are described in the Bridge repository's
  [`docs/security.md`](https://github.com/jaroslav-sazma/cue-bridge/blob/main/docs/security.md), the
  menu-bar app's own; here the Mac appears only as the peer the protocol describes. Its Bluetooth
  link and the gestures on the Mac were tested on real hardware, not reviewed further here.

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
- `HaIconsTest` — the request for icons sends the token only when asked, asks for nothing but the
  component icons of the domains named, treats a refused token or a refused request as final, takes
  nothing from the answer but icon names, and records the instance only as a digest that does not
  contain its address.
- `HaIconReaderTest` — the icon table is read only while a tile needs it, never kept for an instance
  other than the one set up, read again only for a missing domain or a new version of the same
  instance, and a failed read waits before the next unless a handshake has come through since.
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
- `CompanionManifestTest`, `ProcessTextManifestTest` — both bridge components are declared disabled
  and switched together by the gate, only the system can bind the companion service, nothing scans,
  and *Send to Mac* takes plain text alone.
- `BridgeConfigRepositoryTest`, `BridgeSequencesTest` — the bridge is off and stores nothing by
  default, `K_ble` is kept only sealed, nothing is stored while the bridge is off, a pairing opens
  only with the sequence state of its own key, a commit cut short leaves the previous pairing
  unusable rather than counting from zero, frames never rewrite the settings, outgoing numbers are
  reserved 64 at a time with the bound on disk before the first of them leaves, a restart continues
  above that bound, nothing is handed out past the last usable number, the incoming window lets each
  number in once, an unreadable sequence file is treated as corrupt, turning off or unpairing
  wipes the pairing, the key and the sequence state, a pairing that no longer opens is reported
  unusable, and the apps kept off the Mac outlive an unpair but not turning off.
- `TeardownQueueTest` — a wipe whose screen is left and whose job is cancelled midway still runs
  every step, a failing step does not stop the ones after it, and what waits for a wipe starts only
  once it is over.
- `BridgeConditionsTest` — with the bridge off or unpaired neither the presence nor the lock state
  is constructed, a Mac away counts as neither locked nor unlocked, and a tile reads the same lock
  state as the condition, the presence included.
- `MacLockCacheTest`, `MacLockRulesTest`, `BridgeMacLockTest` — the Mac's lock state is unknown
  until the first `lock-state`, one sealed before the last applied changes nothing, a new key starts
  the order over, the state is kept only while the paired Mac is present and never for a pairing
  that is gone, a `lock-state` whose settings were read before an unpair stays forgotten, and a
  button that only locks the Mac can be tapped only while the Mac is known to be unlocked.
- `BridgeLockMacTest` — a `lock` goes over GATT first, the session next and a connection of its own
  last, and on that connection the `lock-state` the Mac sends behind the `bye` is still read.
- `PinnedServerTrustTest` — only the pinned leaf certificate is trusted on a session connection, a
  pairing connection takes any leaf, an empty chain is refused, and the phone never trusts a client.
- `PhonePairingCeremonyTest`, `PairingVectorsTest` — the phone sends its commitment before it sees
  the Mac's key and reveals its key only after a usable one, holds up its side of the shared vector
  ceremonies, a low-order or malformed Mac key aborts before the reveal, the phone's proof follows
  its reveal, a confirmation without a valid proof, a message out of order and a timeout all abort,
  the Mac's verified proof commits, `busy` and the other reasons reach the user, and both sides
  derive the vectors' commitment, keys and code.
- `BridgeBrowserTest` — only a Mac advertising both this protocol version and the pairing flag is a
  target; none, one and several found lead to the hint, the pairing and the pick.
- `CryptoVectorsTest`, `BleFrameVectorsTest`, `BridgeMessageVectorsTest` — the primitives match
  their RFC vectors and refuse a tampered tag or a small-order key, the frames and the messages
  match the shared vectors, a rejected frame fails for its stated reason, the replay window decides
  every shared case as expected, and the sequence number stops at its limit.
- `L2capChannelTest`, `L2capVectorsTest` — the channel keys, records and transcripts match the
  shared vectors, a low-order key aborts the handshake, a record from another pairing fails
  authentication, an oversized or truncated record is fatal, and a silent Mac is given up on with
  the channel closed.
- `BridgeCarriersTest`, `L2capDialTest`, `SendToMacCarriersTest` — TCP goes first unless Bluetooth
  carried the last connection, the memory goes with the window, no route means no Bluetooth
  attempt, a pin mismatch outranks other failures, a dial cancelled mid-handshake or mid-connect
  closes its socket, and *Send to Mac* reports the Mac unreachable only when neither carrier took
  the text.
- `BridgeReplayGuardTest`, `GattSenderTest` — a frame arriving over GATT and over the session at
  once is accepted exactly once, one overtaken on the other path is still accepted once, a frame
  that fails the AEAD records nothing, a frame for a key without its own sequence state is dropped,
  and concurrent sends leave in sequence order.
- `PresenceMachineTest`, `GattPingsTest` — a bare GATT connection never counts as a link to the Mac
  nor holds it present past the debounce, only a frame from the Mac that opened over a connection
  that is up links it, the link ends with that connection, and the phone pings after every
  subscription and at every session start.
- `MirrorFilterVectorsTest`, `MirrorReadTest`, `ClipboardHoldTest` — the mirror filter decides as
  the shared table says, excluded apps and secret notifications included, the lock screen's view is
  resolved as the lock screen resolves it, a redacted notification goes as its public version or as
  the app's name over the hidden text, and clipboard text respects the size cap in UTF-8 bytes,
  keeps its sensitivity while it waits behind the keyguard, is replaced by a newer clip, and is
  dropped once its age outgrows the limit.
- `SessionLoopbackTest`, `GattWriteTimeoutTest` — one session runs end to end in-process over
  in-memory carriers: a closing cancels the notification the set listed and is never sent back, a
  closing for a replaced post is ignored and comes again at the new revision, a reply is answered
  with `reply-ok` or `reply-error`, a reply to a key never mirrored reaches nothing and is answered
  exactly like one to a key that is gone, a clip that waited keeps its age, and a visit stays open
  while a frame trickles in byte by byte; a GATT write the stack never answers gives up after five
  seconds.
- `MirrorCommandsTest` — a closing or a reply, a `reply-text` included, acts only on a key the
  ledger holds, before the first set nothing does, and in the companion window a notification the
  filter keeps back can be neither answered nor closed.
- `BridgeSpecParityTest` — the protocol version, message and frame types, labels, reasons and UUIDs
  in the spec match the code.

Run them with `./gradlew test detekt`; CI runs them together with the release build and lint.
