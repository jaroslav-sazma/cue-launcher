# Contributing to Cue: React & Act

Thank you for looking into it. This file is for anyone who wants to build Cue: React & Act, change
it or help with a release; what the launcher does and how to use it is in the [README](README.md).

## Contents

- [Reporting bugs and security issues](#reporting-bugs-and-security-issues)
- [Building](#building)
- [Rules the code follows](#rules-the-code-follows)
- [Translations](#translations)
- [Documentation](#documentation)
- [Tests and checks](#tests-and-checks)
- [The Mac bridge and its macOS app](#the-mac-bridge-and-its-macos-app)
- [The vault format and its recovery script](#the-vault-format-and-its-recovery-script)
- [When the build changes](#when-the-build-changes)
- [Release build](#release-build)
- [Baseline profile](#baseline-profile)
- [Repository layout](#repository-layout)
- [Commits and pull requests](#commits-and-pull-requests)

---

## Reporting bugs and security issues

A **bug** goes into a GitHub issue. The quickest way there is *long-press the home screen → Launcher
settings → Scenarios → ⋮ → About → Report a bug*: it opens the form in the browser with the phone,
the Android build and the version of Cue: React & Act already filled in. By hand, the Android build
is under *Settings → About phone → Android version → Build number* and the version is in the same
About sheet. Then describe the steps that lead to it and what you expected instead. If a scenario is
involved, attach it — *Launcher settings → Backups* exports it, and the README section
[Placeholders and portability](README.md#placeholders-and-portability) says what such a file carries
before you share it. For a crash, a log helps: with the phone connected to a computer and the
launcher running, `adb logcat -d --pid=$(adb shell pidof app.cue.launcher)` prints what it wrote.

A **vulnerability** never goes into a public issue. [SECURITY.md](SECURITY.md) describes how to
report it privately and what the launcher defends against.

---

## Building

Cue: React & Act is one Gradle module of Kotlin and Jetpack Compose (Material 3 Expressive), built
for Android 17 and newer (minSdk and targetSdk 37).

```bash
./gradlew assembleDebug      # APK in app/build/outputs/apk/debug/
./gradlew installDebug       # install on a connected device
```

The build needs JDK 21 — Gradle fetches it by itself through the toolchain resolver
(`gradle/gradle-daemon-jvm.properties`) — and the Android SDK with `platforms;android-37.1` and
`build-tools;37.0.0`. Android Studio writes the SDK's location into `local.properties` by itself; on
the command line `ANDROID_HOME` does the same.

The debug build is the package `app.cue.launcher.debug`, labelled *Cue: debug*. It installs next to
the release app instead of over it, with data of its own, so a phone that runs the release launcher
every day keeps it while a debug build is tried out; either can be picked as the home app.

---

## Rules the code follows

These are the conventions a change is reviewed against. Most of them exist because the launcher
runs all day on someone's phone.

- **Android 17 is the floor.** `minSdk` and `targetSdk` are both 37, so a runtime version check has
  no reason to exist, and nothing is kept working below it.
- **Visuals come from the design system.** Colours, dimensions, shapes and motion are tokens in the
  `designsystem` package; feature code uses no literal colour or size. Text takes its style from a
  `TypeSpec` token through `toTextStyle()` or from the theme's Material type scale. Only
  `designsystem/Type.kt` names a typeface — Google Sans Flex where the phone has it, `sans-serif`
  otherwise, never a bundled text font. The bundled icon font and the monospace used for codes and
  secrets are the only exceptions, and `TypeContractTest` fails when anything else names one.
- **Material 3 Expressive for the settings and dialogs.** The theme is `MaterialExpressiveTheme` on
  the alpha Compose BOM on purpose, so a new piece of UI reaches for the expressive component first
  — `ToggleButton` with the connected button-group shapes rather than `SegmentedButton`,
  `FloatingToolbar` rather than a hand-made bar, the expressive motion schemes rather than raw
  springs, and a button passes `shapes = ButtonDefaults.shapes()` so it morphs when pressed. Opting
  into `@ExperimentalMaterial3ExpressiveApi` is expected. The launcher surface itself — home screen,
  tiles, widget panel, drawer — is the exception: it follows the project's own glass design and its
  tokens, and Material components do not belong there.
- **Permissions just in time.** Nothing is requested up front. A permission is asked for at the
  moment a feature needs it, after the launcher has said what it reads and why, and a denial never
  blocks the app — only that feature stops, and it says why. In the settings screens that prompt is
  `SettingsGrantRequest`: the note on what the access is for, the message after a refusal and the
  one button that asks.
- **Nothing runs behind the home screen that could wait for it.** Whatever follows the system —
  callbacks, observers, receivers, timers, polling — lives in `ForegroundSession.run()` or behind
  `collectAsStateWithLifecycle` / `LifecycleStartEffect`, and what changed meanwhile is caught up
  when the home screen comes back. A `LaunchedEffect` never collects a flow that registers with the
  system, because a composition outlives `onStop`. The only background entry points are the manifest
  receivers and, while a scenario, the badges or the bridge's mirroring need it, the notification
  listener, each event handled in one short step, and the Mac bridge's companion service, bound by
  the system for the paired Mac's presence while the bridge is on. For as long as the system keeps
  that service bound — which it may do while the Mac is away too — it holds one receiver, of the
  Bluetooth adapter's state, because the stack reports no disconnect for the GATT link when the
  phone's Bluetooth turns off. All its other work lives in a window that opens on the Mac's
  appearance and closes on the debounced absence, and ends with it — the unlock receiver too,
  registered only while a clip from the Mac waits for a locked phone. The vault's
  `SafeDocumentsProvider` is the last one: the manifest declares it disabled, it is enabled only
  from an unlock to the lock, and only a picker or a file manager the user opens calls it; the
  thread its reads run on starts with the unlock and ends with the lock, unless another app still
  holds a descriptor from the vault then: the thread stays, idle, failing each of its reads, until
  the last such descriptor is closed. The vault's automatic lock keeps one one-shot timer, and only
  inside `ForegroundSession.run()`: while a session is open it waits for the session's limit and
  locks it then, armed anew whenever the session changes. Outside `run()` the time since the unlock
  is checked lazily, when `run()` starts, on every call of the provider and before every read of a
  descriptor it handed out, and a process that starts with the provider still enabled, left behind
  by one that died unlocked, disables it. The README section
  [Battery and background](README.md#battery-and-background) describes the result as a user sees it,
  and `scripts/background-audit.sh` checks it on an emulator.
- **The scenario format is a public contract.** `schemaVersion 1` is frozen: adding an optional
  field or an enum value is fine and keeps the version; removing or renaming anything needs the
  maintainer's agreement and a `schemaVersion` bump. Exported files people keep and share depend on
  it, and [docs/scenario-format.md](docs/scenario-format.md) describes it, so a change to the format
  updates that file too.
- **The Home Assistant blueprint stays in step.** `ha/launcher_event.yaml` and its Czech twin
  `ha/launcher_event.cs.yaml` are packaged into the APK straight from the repository. Both
  blueprints, the manifest receiver and `HA_EVENT_ACTION` have to match, and the two blueprints may
  differ only in their text; `HaEventContractTest` fails otherwise.
- **Comments are for what the code cannot say.** A workaround, a trap or a contract that is not
  obvious gets a comment; a description of what a function does does not, and there is no
  descriptive KDoc.
- **English everywhere except the UI text.** Code, identifiers, comments, commit messages,
  documentation, test names and assertion messages are in English.

---

## Translations

User-facing text never sits in Compose code: it lives in string resources, in two locales. Every
new or changed string goes into both `app/src/main/res/values/strings.xml` (English, the default)
and `app/src/main/res/values-cs/strings.xml` (Czech), under the same key. Czech has more plural
forms, so the two files differ in length; a key missing from one of them is a bug.

The Home Assistant blueprint has a Czech twin as well (see above), and a change to its text goes
into both files. A new language means a new `values-<locale>/strings.xml`, an entry in
`app/src/main/res/xml/locales_config.xml`, and the blueprint question settled — open an issue first
so it can be planned.

---

## Documentation

`README.md` is the user documentation, ordered from the general to the specific: what the launcher
is, its screens and settings, scenarios with the catalogs of conditions, tile content and actions,
the Home Assistant setup, the Mac bridge, the vault and examples, then permissions, battery,
download and the roadmap. It is written for someone using the phone — plain words, the labels the
app shows, a short example where a description alone would not do — and leaves out how the code
works. [`docs/scenario-format.md`](docs/scenario-format.md) is the reference of the scenario JSON:
every field, the notification value rules and the import checks. This file is the contributor
documentation. **Every change ends with a review of the README and this file**: a behaviour a user
sees is not finished until the README describes it, a change to the build, the checks or the rules
above is not finished until this file does, and a change to the scenario JSON not until the format
reference does. Anything a user has to know, set up or work around belongs in the README; internal
refactors belong in neither.

`SECURITY.md` (reporting and the threat model), `docs/security-audit.md` (what a review covered)
and `PRIVACY.md` (the privacy policy, also the one the planned Google Play listing will link to)
change whenever a control or a data flow they name changes.

Prose in every `.md` file is hard-wrapped at 100 characters, counted in characters rather than
bytes. Tables, fenced blocks and diagrams stay on one line however long they get, a wrap never lands
inside a `code span` or a link, and editing a sentence means re-wrapping its whole paragraph.
Examples in the documentation are in English. Prose names the launcher "Cue: React & Act", the Mac
app of the bridge "Cue: Relay & Reply" and the Mac app of the vault
"Cue: Vault & View (for Google Drive)", or "Cue Vault" for short, so they are told apart at a
glance; a bare "Cue:" only quotes what a screen shows, such as the label under the icon or *Do Not
Disturb (Cue:)*.

---

## Tests and checks

```bash
./gradlew test detekt                 # unit tests + static analysis
./gradlew lintRelease                 # Android lint on the release variant
./gradlew verifyDebugMergedManifest   # no library starts code of its own
./gradlew licensee                    # every library's license has its text in LICENSES/
scripts/background-audit.sh           # on an emulator: nothing may run behind the home screen
build/cuevault-venv/bin/python scripts/cuevault.py vectors --check fixtures/vault   # vault vectors
```

Unit tests are JUnit (Jupiter) and run on the JVM; the fixtures in `fixtures/scenarios/` double as
round-trip tests of the scenario format, and some tests read the README, the blueprints, the bug
report form and the Mac bridge protocol to keep them in step with the code. The bridge vectors in
`fixtures/bridge/` are shared: the Kotlin codec is tested against them here, and the macOS app's
tests in its own repository read an identical copy of them. Two of them belong to one side only:
`filters.json` is the phone's mirror-filter decision table, which `MirrorFilterVectorsTest` runs the
filter against, and `state-sync.json` holds the Mac's state-sync diff cases. Gradle does not see
such reads, so the files are listed as inputs of the test task in `app/build.gradle.kts`
(`testedRepositoryFiles`); a test that reads another one adds it there, or a change to only that
file leaves the tests up to date. Screenshot tests (`./gradlew validateDebugScreenshotTest`) render
the settings screens through layoutlib.

`scripts/background-audit.sh [seconds] [--with-listener] [--vault-unlocked]` sends a debug build of
the launcher behind Settings on a connected emulator and reposts a notification every two seconds,
the way a progress notification is updated. It then fails if the process holds a content observer, a
registered receiver, a binding to another app's service or one of its own other than the
notification listener, keeps the listener bound although the need it stored says nothing wants it,
is in another state than the one the listener calls for — a bound service while it is bound,
otherwise the home state or a cached one — or spent more than 0.3 s of CPU a minute. The one
receiver it lets through is the Mac bridge's single registration for the Bluetooth adapter's state
and nothing else, and only while the system binds the companion service. `--with-listener` grants
notification access for the run; the listener binds only while badges are on, a scenario watches
notifications or a running tile waits for one to end it. The vault's provider must be disabled and
its reading thread absent, unless `--vault-unlocked` says the vault was unlocked on the emulator
before the run, with an automatic lock longer than the run; then both must be there, and content
observers under `<package>.vault/document/` pass too — the cursor a file manager keeps on a vault
folder it shows, which lives in the launcher's process until the file manager lets it go or the
vault locks. The reposted notification stays in the shade under the tag `cue-audit`, as the shell
cannot cancel it.

Debug builds carry a generator of fixture notifications for the Mac bridge, so the mirror can be
exercised on the emulator without other apps. The debug section of the Mac bridge screen has a
button for each fixture, and the same fixtures can be posted over adb. The fixtures are `plain`;
`updating`, the same id posted again with a new time, so it updates in place; `grouped`, three
children and a group summary; `reply`, with an inline reply whose receiver logs only the length of
the text to `CueBridge`; `progress`, a ProgressStyle that asks to be promoted to a Live Update;
`low`, on a channel of low importance; `private`, with a public version, which the Mac gets instead
while the lock screen hides sensitive content; and `secret`, which the lock screen never shows.
`cancel` removes them all. The mirror filter exempts the fixtures' channels from the own-package
rule in debug builds only, so the summary, the progress, the low-importance and the secret fixture
show what the filter skips. The buttons ask for the notification permission on the first press; the
adb path posts nothing until it is granted and says so in the log. Its receiver is declared in the
debug manifest only and requires `android.permission.DUMP`, which the shell holds and no app a user
installs can:

```bash
adb -e shell am broadcast -a app.cue.launcher.debug.BRIDGE_FIXTURE --es fixture plain -n app.cue.launcher.debug/app.cue.launcher.debug.bridge.BridgeFixtureReceiver
adb -e shell dumpsys notification --noredact | grep 'NotificationRecord(.*pkg=app.cue.launcher.debug'
adb -e logcat -s CueBridge
```

### What CI checks

`.github/workflows/ci.yml` runs on every pull request and every push to `main`; any other branch can
be checked by hand from the Actions tab (*Run workflow*):

```bash
./gradlew assembleDebug assembleRelease lintRelease test detekt verifyDebugMergedManifest licensee
```

The release build is in there on purpose: R8 and resource shrinking only happen in release, and a
missing keep rule breaks the release build while the debug build stays green. It comes out unsigned
and goes nowhere — CI never publishes anything; a release is the separate, manual step under
[Publishing a release](#publishing-a-release). `verifyDebugMergedManifest` fails when a library
brings a provider or startup initializer into the merged manifest outside the allowlist in
`app/build.gradle.kts`. `licensee` (the Licensee plugin) reads the license every library in the app
declares and fails on any outside the allowed list in the same file, which holds exactly the
licenses whose texts are in `LICENSES/` — so a dependency update that switches license, or pulls in
a library under a new one, cannot slip through. Both are named on their own because `check` would
pull in the screenshot tests as well. Lint fails the build on any error, and its security checks
(unsafe intent launches, mutable pending intents, receivers without an export flag, trust-all TLS,
backup rules, among others) are promoted to errors. There is no lint baseline: an error is fixed, or
suppressed at the line with the reason next to it, never hidden in bulk. Warnings stay visible in
the report; the ones a security reader will look for — the exported `HaEventReceiver` and cleartext
traffic — are deliberate and explained in `SECURITY.md` and `docs/security-audit.md`. Screenshot
tests run too but do not block yet, until rendering on Linux and macOS is known to match.

A second job, beside the build, runs in a `python:3.9-slim` container pinned by digest, because 3.9
is the oldest Python the vault format's reference script supports: it installs the script's pinned
dependencies into a venv and runs `scripts/cuevault.py vectors --check fixtures/vault`, see
[The vault format and its recovery script](#the-vault-format-and-its-recovery-script). It needs no
Gradle and takes seconds. `renovate.json` lets the image take digest updates only, never another
Python.

On `main` another job submits the resolved dependency graph to GitHub, which is what lets Dependabot
alerts see vulnerable libraries in a Gradle project. Renovate (`renovate.json`) proposes the updates
themselves and opens an update with a `security` label as soon as an advisory names a version in
use. Every action in the workflow is pinned to a full commit hash with the version as a comment,
because a tag can be moved to other code and a commit cannot; Renovate keeps the pins current.
`gradle/actions` is held on v5 on purpose: from v6 its caching is a proprietary component under
Gradle's own terms of use, and `renovate.json` keeps it below 6. The workflow's token is read-only,
except for the dependency job, which needs to write the graph.

---

## The Mac bridge and its macOS app

The Mac bridge has two halves. The phone's half is in this repository, in three packages under
`app/src/main/kotlin/app/cue/launcher/`: `system/bridge/` (the companion service and its window, the
Bluetooth link, the two session carriers — TLS over the network and the L2CAP channel over
Bluetooth, tried in the order `BridgeCarriers` keeps — the protocol codec and pairing, the clipboard
and Send to Mac), `data/bridge/` (the stored pairing, the sealed key and the mirror filter) and
`feature/settings/bridge/` (the screen). The macOS half, the menu-bar app Cue: Relay & Reply, lives
in a repository of its own, [cue-bridge](https://github.com/jaroslav-sazma/cue-bridge).

- **One spec, kept here.** [`docs/bridge-protocol.md`](docs/bridge-protocol.md) is the only
  normative description of the protocol, at `bridgeProtocolVersion 1`, and `fixtures/bridge/` holds
  its test vectors: frame and message encodings, the crypto known-answer cases, the pairing
  commitment, transcript and code with whole ceremonies both sides run, the mirror-filter table and
  the state-sync diffs. The macOS repository links to the spec and never restates it. Unlike the
  rest of this repository, the spec and the vectors are dedicated to the public domain under
  CC0-1.0 (`NOTICE`, `LICENSES/CC0-1.0.txt`), so that peers on other platforms can be written
  under any license; a contribution to either is made under the same dedication.
- **Both sides test against the same vectors.** Here the Kotlin codec, the crypto, the pairing and
  the filter are tested against `fixtures/bridge/`, and `BridgeSpecParityTest` keeps the spec's
  version, types, labels, reasons and UUIDs in step with the code. The macOS repository keeps an
  identical copy of the JSON vectors, not the directory's README, so a clone of it tests on its own,
  and its tests read that copy. Vectors change here only; the macOS repository's
  `tools/sync-vectors.sh` then copies them over, and its parity test fails on any JSON file that
  differs by a single byte while a checkout of this repository sits beside it. Each side also
  asserts that its own protocol version equals the spec's header, so a version drift fails a test
  rather than a demo.
- **Each side runs a session in-process.** `SessionLoopbackTest` drives the phone's real
  conversation, framing, frame codec and mirror commands over in-memory carriers against a scripted
  Mac — closings, revisions, replies and their answers, clip ages, the visit's idle timer — and the
  macOS repository's loopback tests do the same for the Mac's half against a scripted phone. A
  change to how a session behaves comes with a case on the side it changes.
- **A protocol change lands as a pair of commits**, one in each repository, carrying the same
  `bridgeProtocolVersion`: this one updates the spec, the vectors and the Kotlin code, the other
  the Swift code and the synced copy of the vectors. The spec's *Change policy* decides the
  version: an additive change — a new optional field, message, frame type or reason — keeps it,
  anything else bumps it, and both sides refuse a peer on another version. Neither commit lands
  until both sides' contract tests pass against the same vectors.
- **Off means off.** The bridge is off by default. `BridgeCompanionService` and
  `ProcessTextActivity` are declared disabled and switched on only while the bridge is on and paired
  (`BridgeComponentGate`); with it off nothing of the bridge is constructed, which
  `scripts/background-audit.sh` checks along with the rest.

The emulator has no Bluetooth and does not see the Mac's Bonjour, so debug builds can pair without
either: **Pair without Bluetooth** on the pairing screen takes the address of the Mac the emulator
runs on (`10.0.2.2`) and the port the Mac listens on — the Debug pairing window and
`bridgectl status` show it — and the debug section of the Mac bridge screen keeps that endpoint
afterwards. Signals then travel inside the TLS session instead of over Bluetooth. Everything except
the Bluetooth link and the L2CAP channel over it, the companion-device presence, the gestures on the
Mac and battery use can be exercised that way; those need a real phone next to a real Mac. The phone
logs protocol events at debug level under the tag `CueBridge`, with message types and key hashes
only, never content.

---

## The vault format and its recovery script

The vault is one encrypted `.cuevault` file that the Mac app Cue: Vault & View (for Google Drive),
Cue Vault for short, creates and writes and the launcher reads. Cue Vault lives in a repository of
its own, [cue-vault](https://github.com/jaroslav-sazma/cue-vault). Its format is shared the way the
bridge protocol is, and lives here too.

- **One spec, kept here.** [`docs/vault-format.md`](docs/vault-format.md) is the only normative
  description of the container, at format version 1: the header, the key derivation and wrapping,
  the segments, the ustar profile, the limits, the error categories and the password rules. Like the
  bridge spec, it is CC0-1.0, and so are `scripts/cuevault.py`, its requirements file and
  `fixtures/vault/` (`NOTICE`); a contribution to any of them is made under the same dedication.
  Format version 1 is frozen: a change to any byte it describes is a new format version.
- **A reference implementation that needs neither app.** `scripts/cuevault.py` reads and writes
  vaults (`extract`, `create`), streaming them a segment at a time so that no vault has to fit in
  memory, and generates and checks the vectors (`vectors`), which are small enough to build in
  memory with the same functions. It is also the recovery path for users, so it runs on the system
  Python of macOS, 3.9, and depends on `cryptography` alone; its ustar writer and reader are its
  own, because the vectors compare bytes. Its dependencies are pinned with hashes in
  `scripts/cuevault-requirements.txt` and go into a venv under `build/`, never into the system or
  your home directory:

  ```bash
  python3 -m venv build/cuevault-venv
  build/cuevault-venv/bin/pip install --require-hashes -r scripts/cuevault-requirements.txt
  ```

  The pins are the newest versions that still support Python 3.9. Renovate cannot tell which
  release of a package still does, so `renovate.json` holds back the cffi and pycparser releases
  that dropped it and caps cryptography below 51 and typing-extensions below 4.17. Lifting a cap, or
  landing any update of the file, means checking on PyPI that every pinned version still declares
  Python 3.9 support (`Requires-Python`) and that the hashes cover the CPython wheels for macOS
  arm64, manylinux x86_64 and aarch64 and Windows x86_64. CI installs the file on Python 3.9 and
  fails when a pin no longer supports it, but only on Linux x86_64.
- **Vectors change here only.** `fixtures/vault/` holds the canonical vectors: `vectors.json` with
  every input and expected value, and the `*.cuevault` files. A vector changes by editing the inputs
  in `vectors.json`, then
  `build/cuevault-venv/bin/python scripts/cuevault.py vectors fixtures/vault` rewrites the expected
  values and the files, and `… vectors --check fixtures/vault` verifies them, as CI does;
  `fixtures/vault/README.md` describes the schema. Cue Vault's repository keeps an identical copy of
  `vectors.json` and the `*.cuevault` files, not the README, refreshed by its
  `tools/sync-vectors.sh`, and its parity test fails on any file that differs while a checkout of
  this repository sits beside it. Cue Vault's Swift writer has to reproduce the positive files byte
  for byte; a format change lands as a pair of commits, one in each repository.
- **The launcher's reader.** `engine/safe/` reads a vault in plain Kotlin, without the Android
  framework, so `VaultVectorsTest` opens the vectors in place on the JVM, through the same
  random-access reader the phone uses: every positive case with its password and with its stored
  key, every accepted case to exactly its entries, and every negative case with exactly its category
  — or, in the order the phone checks a pipe, whose length it learns only from its copy, the
  credential before the length, the alternative a case names for a reader that learns the length
  late. `SafeVaultTest` runs the negative cases through the whole unlock as well, with the
  descriptor's length (exactly the category) and as a pipe (the alternative allowed). A format
  change updates it in the same commit.
- **The vault on the phone.** `system/safe/` and `data/safe/` hold it, named `safe` because
  `feature/drawer/vault/` is the drawer's A–Z index. `SafeVault` is the one entry point: its state
  flow, the unlock with the kept key or with the password, the lock and the settings. The CEK is
  kept sealed under a Keystore key that every use has to authenticate. An unlock reads the file's
  header, checks its layout against the length when the descriptor states one, obtains the key, then
  copies the rest of the same stream into `noBackupFilesDir/safe/` (`SafeCopies`, which removes the
  copy's name at once, so only its descriptor keeps it) and has `VaultReader` verify every segment
  and index the archive in one pass. The session keeps the copy, the CEK and the index;
  `SafeDocumentsProvider` decrypts what a read touches on its one reading thread, with a small cache
  of segments, so the phone's memory grows only with the index, capped at `SAFE_MAX_ENTRIES` files
  and directories, and the vault has no size limit but the free storage. Its document ids are a
  random token drawn at every unlock and the number the session's index gives the document, never
  its path, which the system's picker would keep with its recents (`SafeDocumentIds`), so no id,
  grant or stored URI of one unlock names anything in the next, and the lock revokes every grant on
  the authority while the provider is still enabled. Disabling it then makes Android kill any app
  that still holds one of its descriptors (`REASON_DEPENDENCY_DIED`); that is accepted, and the
  README warns about it. `SafeReadGate` fails each read of a descriptor past its session's limit and
  has the main thread lock it, and a segment that no longer verifies fails its read and posts the
  lock the same way, never locking on the reading thread. A lock bumps a generation that the copy
  and the index pass check at every chunk and segment, so it stops an unlock under way at once.
  `SafeVaultTest` runs the state machine against the vectors with fakes for the Keystore, the prompt
  and the file, and a real copy in a temporary directory; the Keystore, the prompt, the provider and
  the reservation of storage need a device. `feature/settings/safe/` is the settings screen with the
  password dialog, the one place a password is typed, and the actions `vault_toggle`, `vault_unlock`
  and `vault_lock` go through `ActionExecutor` like `mac_lock`, with `vaultTapOf` deciding what a
  tap does: a tap that needs the user — no vault yet, the password, the file again, an error — opens
  that screen rather than putting Material UI on the home screen. The `vault_unlocked` condition
  reads `SafeConditions`, a provider over the session in memory that registers nothing: a session
  past its limit reads as locked there before anything locks it, and while the reading is collected
  — by the home screen, or for an event's five seconds — a wait for the limit turns it false on time
  and ends with the collection.

---

## When the build changes

What has to follow a change to the build, and when:

| Change | What has to follow |
|---|---|
| A dependency update — a Renovate pull request or by hand | CI green, release build included. After a Compose BOM, AGP, Kotlin or R8 update, also run the release APK on a device: a keep-rule problem can compile and only fail at runtime. Regenerate the baseline profile when Compose or anything on the startup path moved. |
| A new library | `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` shows what it pulls in. Nothing that reports anywhere — analytics, crash reporting, ads, Play Services — belongs in the app. A provider or startup initializer such a library adds to start itself fails `check` until it is removed in the manifest or allowlisted, and no library may bring a boot receiver or the boot permission, which `BootManifestTest` holds. If it talks to the network, `PRIVACY.md`, `SECURITY.md` and the audit change with it. If it needs keep rules its own artifact does not ship, they go into `app/proguard-rules.pro` with the reason. Its license goes into `NOTICE`, and its license text into `LICENSES/` and the app's list of licenses (`LicenseNotice`) if it is not there yet; `LicenseNoticesTest` keeps the last two in step. A license that is new to the project fails `licensee` until it is added to the `licensee` block in `app/build.gradle.kts` together with its text. |
| `compileSdk`, `compileSdkMinor` or build-tools | The `packages:` line in **both** jobs of `ci.yml` installs the same platform and build-tools. |
| The JDK | `./gradlew updateDaemonJvm --jvm-version=<n>`, `compileOptions`, detekt's `jvmTarget` in `app/build.gradle.kts` and `java-version` in both CI jobs move together. detekt 1.23.8 cannot run on JVM 25 or newer. Renovate does not propose a new major JDK, so the move is always made by hand. |
| The Gradle version | `./gradlew wrapper --gradle-version <x> --gradle-distribution-sha256-sum <sum>`, with the sum from [gradle.org/release-checksums](https://gradle.org/release-checksums) — the task refuses to drop the `distributionSha256Sum` that pins the download — and then `./gradlew wrapper` once more, so that the new version writes its own `gradle-wrapper.jar` and scripts. Never a hand edit of the URL; a Renovate pull request does exactly that and leaves the old jar behind. |
| A Room entity or DAO query that changes the schema | From the first public release on: the database version goes up, a migration comes with it, and the schema exported into `app/schemas/` is committed. A user's database is never thrown away. |
| The manifest — a permission, a component, an intent filter, an `exported` flag | `SECURITY.md` (the threat model), `docs/security-audit.md` (entry points, permissions), `PRIVACY.md` when data flows change, and the README's [Permissions](README.md#permissions) table. |
| A GitHub Action, new or updated | Pinned to a full commit hash with the version as a comment. Renovate keeps existing pins current. |
| Startup or the first interactions | Regenerate the baseline profile, see below. |
| The Home Assistant blueprint | Both `ha/launcher_event.yaml` and `ha/launcher_event.cs.yaml`, the receiver and `HA_EVENT_ACTION` stay in agreement; `HaEventContractTest` fails otherwise. |
| The Mac bridge protocol — `docs/bridge-protocol.md` or `fixtures/bridge/` | The paired commit in the macOS app's repository with the same `bridgeProtocolVersion`, bumped unless the change is additive, carrying the vectors copied over by its `tools/sync-vectors.sh` — its parity test fails until they match — see [The Mac bridge and its macOS app](#the-mac-bridge-and-its-macos-app). `SECURITY.md` and the audit change with anything in the pairing or the cryptography. |
| The vault format — `docs/vault-format.md`, `scripts/cuevault.py` or `fixtures/vault/` | Format version 1 is frozen: new vectors and changes that leave every byte as it is keep it, anything else is a new format version. The vectors are regenerated and pass `--check`, and the paired commit in Cue Vault's repository carries the Swift codec and the copy its `tools/sync-vectors.sh` makes — its parity test fails until they match — see [The vault format and its recovery script](#the-vault-format-and-its-recovery-script). |
| `scripts/cuevault-requirements.txt` | Every pinned version still supports Python 3.9, the system Python of macOS, and every hash comes from PyPI, covering the CPython wheels for macOS arm64, manylinux x86_64 and aarch64 and Windows x86_64; a fresh venv installs the file with `--require-hashes`. |
| The signing key | Never into the repository; `keystore.properties` points at it, and it is backed up in two independent places. It cannot be replaced: once Cue: React & Act is on Play, Play keeps signing with its enrolled copy, but a lost key means no APK on GitHub can ever update an installed copy again, and a new key would also change the certificate under [Verifying a download](README.md#verifying-a-download). |

---

## Release build

The release build is minified and shrunk by R8, and it carries the baseline profile — it behaves
differently from the debug build, so anything meant for real use is worth testing in this form. It
also strips verbose, debug and info logging (`app/proguard-rules.pro`); warnings and errors stay so
that a bug report still says what went wrong.

Two settings shape what gets published. The APK carries no dependency list: by default AGP embeds
one encrypted with Google's key, which nobody else can read and which stands between a published APK
and a rebuild of the same tag; the bundle for Play keeps it, since that is where Play reads it.
And the bundle is not split by language, because the launcher switches its language at runtime and
Play would otherwise install only the strings of the device's language.

Signing is driven by `keystore.properties` in the repository root: copy
`keystore.example.properties` to it and fill it in. The file is gitignored and so is the key it
points at; neither is in the repository, and without them the release build simply comes out
unsigned. The same key signs the APK published on GitHub and will sign the bundle for Google Play,
where a release is planned; it gets enrolled in Play App Signing as the app's own key rather than
one Google generates — so a copy from either source will update the other, and both carry the
certificate listed under [Verifying a download](README.md#verifying-a-download).

```bash
./gradlew assembleRelease      # APK in app/build/outputs/apk/release/
./gradlew installRelease       # install on a connected device
```

Debug and release have different application ids and install side by side, each with its own data,
settings and widgets. R8 renames classes, so `app/build/outputs/mapping/release/mapping.txt` is what
turns a stack trace from a release build back into something readable — keep the copy that belongs
to the build you are running.

### Publishing a release

1. Set `versionName` in `app/build.gradle.kts` by [Semantic Versioning](https://semver.org/):
   `1.0.1` after `1.0.0` for fixes only, `1.1.0` for new features, `2.0.0` for a change that breaks
   something users rely on — a `schemaVersion` bump of the scenario format among them. Raise
   `versionCode` by one, whatever the step: it is the number Android compares and users never see.
   Play refuses a `versionCode` it has already seen, and an installed app only updates to a higher
   one.
2. Run the full check, then both artifacts:
   `./gradlew test detekt lintRelease bundleRelease assembleRelease`.
3. Install the release APK on a device and go through what a debug build cannot show: becoming the
   home app, a Home Assistant call, an import.
4. Once Cue: React & Act is on Play, upload `app/build/outputs/bundle/release/app-release.aab`
   there. The bundle carries its own R8 mapping, so Play deobfuscates crash reports without a
   separate upload.
5. Check that the APK carries the certificate published under
   [Verifying a download](README.md#verifying-a-download)
   (`apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`), tag the commit
   `v<versionName>` and attach to the GitHub release the APK, its SHA-256 (`shasum -a 256`) and the
   `mapping.txt` of that build.
6. Read `SECURITY.md`, `PRIVACY.md` and `docs/security-audit.md` against what the release changed.

---

## Baseline profile

The profile lives in `app/src/release/generated/baselineProfiles/` and is committed, so it reaches
the APK even for someone who never runs the benchmark. It is not rebuilt as part of a normal build;
it is regenerated on demand, after changes that move startup or the first interactions:

```bash
./gradlew :app:generateBaselineProfile                           # regenerate, writes into app/src/release/
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest  # measure cold start
```

Both need a device or a running emulator — the module uses connected devices rather than a Gradle
Managed Device, so nothing is downloaded and nothing starts on its own. Commit the two changed
`.txt` files; large diffs are normal, the order of methods differs from run to run.

The journeys profile a home screen as a user has it, not the empty one of a fresh install. Before
collecting, and before measuring, `seedHome` dismisses the onboarding and imports
`fixtures/baselineprofile/home.json` through the same `ACTION_VIEW` import a shared file takes: the
test APK serves it from a provider of its own with a read grant, and the journey confirms the
preview. The fixture is a whole configuration — bento tiles that need no trigger and no permission,
with a 2×2 stack and a 1×1 dynamic folder, a folder, the search bar and a full dock, and the widget
panel as a fresh install leaves it — and `BaselineProfileFixtureTest` keeps it so, laying it out
with the real engine. Restoring it replaces the home screen, both docks, the widget panel, the
appearance, the gestures and the drawer settings with their hidden apps, so the journey seeds only
an `app.cue.launcher` signed with the same debug key as the test APK. An import only ever adds
scenarios alongside, so it runs only while neither the folder *Tools* nor the tile *Wind down* is on
the home screen; finding one without the other fails the run with a request to uninstall
`app.cue.launcher`, and so does an import that does not end on the seeded home screen.
Cue: React & Act is started through the HOME intent, never the LAUNCHER one. Besides the cold start,
the journeys cover the widget panel, the folder, paging the 2×2 stack, the dynamic folder, the
drawer's vault, a query typed letter by letter with a typo in it and launched with Enter, and coming
back from another app the way the home button does.

The connected test task removes the APKs it installed once the run is over, so every run starts from
a fresh install of the measured `app.cue.launcher` and seeds it again; the debug build keeps its own
data. The device's default home has to be another launcher — on the emulator the debug build. A
default home that is killed is started again by the system at once, so a cold start would not be
cold, `pressHome()` in the benchmark's setup would start the measured app itself, and the journeys'
way back home relies on the measured app sitting in an ordinary task rather than in the one the
system keeps for its home.

`:baselineprofile:connectedNonMinifiedReleaseAndroidTest` only collects the raw profile into
`baselineprofile/build/outputs/`; it is `:app:generateBaselineProfile` that merges it into the
committed files.

An emulator is treated as an untrusted device, so absolute timings from it mean nothing — only
before/after comparisons do. Two things in the report mislead on first reading: whichever of the two
test classes the variant disables is reported as a `<failure>` with an
`AssumptionViolatedException`, which is expected and does not fail the task, and the Gradle output
names no cause when a test really fails — that is in
`baselineprofile/build/outputs/androidTest-results/connected/<variant>/`, in `TEST-<device>.xml` and
the `logcat-*.txt` next to it.

If a run fails with `Target package app.cue.launcher is not running` and that logcat shows
`Failed to mount /data_mirror/data_de/null/0/app.cue.launcher`, the emulator's storage mirror is
broken after the install/uninstall cycles the benchmark does, not the app. Reboot the emulator and
run it again.

A release build already on the device makes the install fail with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match`, because the benchmark installs its
variants, signed with the debug key, under the same `app.cue.launcher`. The task still reports
`BUILD SUCCESSFUL` and **overwrites the committed profiles with the empty result**, so check
`git diff` afterwards: a run that collected nothing deletes both `.txt` files instead of rewriting
them. Run it on an emulator, or uninstall the release app first, which clears its data; a debug
build (`app.cue.launcher.debug`) is not in the way:

```bash
adb uninstall app.cue.launcher && adb uninstall app.cue.launcher.baselineprofile
./gradlew :app:generateBaselineProfile
```

---

## Repository layout

| Path | Contents |
|---|---|
| `app/` | the Android app — a single Gradle module |
| `README.md` | the user documentation |
| `CONTRIBUTING.md` | this file: the contributor documentation |
| `docs/scenario-format.md` | the reference of the scenario JSON, `schemaVersion 1` |
| `docs/bridge-protocol.md` | the normative protocol between the launcher and the macOS bridge app, `bridgeProtocolVersion 1`; `BridgeSpecParityTest` keeps its version, message and frame types, derivation labels, close and abort reasons, GATT UUIDs and MTU floor and vector catalog in step with the code; CC0-1.0, not GPL |
| `docs/vault-format.md` | the normative vault container format, format version 1, which Cue Vault, the launcher and `scripts/cuevault.py` implement; CC0-1.0, not GPL |
| `LICENSE`, `NOTICE`, `LICENSES/` | the GPL text; the license notice with its section 7 terms, the CC0 dedication of the bridge and vault specs, their vectors and the vault's recovery script, and the third-party material; the license texts that material and the dedication need. All three are packaged into the APK by the `licenseAssets` task and shown in the app |
| `art/icon/`, `fastlane/metadata/android/` | the icon master files; the store listing texts, screenshots and graphics. They are GPL files like the rest, but the GPL grants no trademark rights to the name and icon, see `NOTICE` |
| `fixtures/scenarios/` | example scenarios in the JSON contract; also used by round-trip tests |
| `fixtures/bridge/` | the bridge protocol's test vectors, CC0-1.0 like the spec; the canonical copy, whose JSON files the macOS app's repository mirrors with its `tools/sync-vectors.sh` and checks byte for byte |
| `fixtures/vault/` | the vault format's test vectors, `vectors.json` and the `*.cuevault` files, CC0-1.0 like the spec; the canonical copy, which Cue Vault's repository mirrors with its `tools/sync-vectors.sh` and checks byte for byte |
| `fixtures/baselineprofile/` | the whole configuration the baseline profile journeys seed the measured app with; packaged into the test APK only, not an example to import |
| `ha/launcher_event.yaml`, `ha/launcher_event.cs.yaml` | the Home Assistant blueprint in English and Czech, packaged into the APK straight from here |
| `baselineprofile/` | baseline profile generator |
| `config/detekt/detekt.yml` | deviations from the default detekt config |
| `scripts/background-audit.sh` | emulator check that nothing needless runs while the home screen is not shown |
| `scripts/cuevault.py`, `scripts/cuevault-requirements.txt` | the vault format's reference implementation and recovery tool (`extract`, `create`, `vectors`), and its dependencies pinned with hashes; CC0-1.0, not GPL |
| `gradle/libs.versions.toml` | version catalog |
| `keystore.example.properties` | the template for the gitignored `keystore.properties` that signs the release build |
| `.github/workflows/ci.yml` | CI: build, lint, tests, detekt, merged manifest and license checks; the vault vectors; dependency graph for Dependabot |
| `.github/ISSUE_TEMPLATE/`, `.github/pull_request_template.md` | the forms for bug reports and feature requests, and the pull request checklist; *About → Report a bug* fills the bug form's `device`, `android` and `version` fields by their ids and opens it at `REPOSITORY_URL` (`feature/scenarios/AboutLinks.kt`), which a fork points at its own repository; `AboutLinksTest` fails if an id goes missing |
| `renovate.json` | dependency and action updates, security updates labelled |
| `SECURITY.md` | how to report a vulnerability, and the threat model the launcher is built against |
| `docs/security-audit.md` | what a security audit covered and what it found |
| `PRIVACY.md` | the privacy policy: what the app reads, stores and sends, and to whom |
| `CLAUDE.md` | conventions for AI executors working in this repo |

---

## Commits and pull requests

- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) and are in
  English: `feat: …`, `fix: …`, `docs: …`, `refactor: …`, `test: …`, `build: …`, `ci: …`.
- Keep a pull request to one concern. Before opening it, run
  `./gradlew test detekt lintRelease verifyDebugMergedManifest licensee`, check both locales and
  review the README and this file as described under [Documentation](#documentation).
- A change to the scenario format, a permission or a data flow is worth an issue first, since it
  touches the public contract, `SECURITY.md` or `PRIVACY.md`.
- Contributions are accepted under the project's license, GPL-3.0-or-later.
