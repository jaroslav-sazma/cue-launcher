# Cue: React & Act (app.cue.launcher) — instructions for AI executors

Trigger-driven Android launcher "Cue: React & Act" (its Play name; the label under its icon is
"Cue:"). Kotlin + Compose, minSdk 37, applicationId `app.cue.launcher`. GPL-3.0-or-later.

## Hard rules
- Visuals go through `designsystem` tokens only — no literal colors or dimensions in feature code.
- UI is built from the **newest Material 3 Expressive** components. The theme is already
  `MaterialExpressiveTheme` and the alpha BOM is deliberate, so reach for the expressive element
  before the older one: `ToggleButton` + `ButtonGroupDefaults.connected*Shapes()` instead of
  `SegmentedButton`, `FloatingToolbar` instead of a hand-rolled bar, the expressive motion schemes
  instead of raw springs. Opting into `@ExperimentalMaterial3ExpressiveApi` is expected. The
  exception is the launcher surface itself (home screen, tiles, panel, drawer), which follows the
  project's own glass design and its tokens — Material components do not belong there.
- Android 17 (API 37) is the floor: minSdk and targetSdk are both 37, so runtime version checks have
  no reason to exist at all. Nothing is kept working below it.
- Comments are kept to a minimum: only workarounds, traps and non-obvious contracts. No descriptive
  KDoc.
- The scenario JSON (`schemaVersion 1`) is a frozen public contract, described in
  `docs/scenario-format.md`. Adding an optional field or an enum value is additive and keeps the
  version; removing or renaming anything needs the user's approval and a `schemaVersion` bump.
- Permissions are requested just in time, never up front. A denial must never block the app — only
  the feature that needs it stops working, and it says why.
- While the home screen is not started, nothing runs that could wait for it. Whatever follows the
  system — callbacks, observers, receivers, timers, polling — lives in `ForegroundSession.run()` or
  behind `collectAsStateWithLifecycle` / `LifecycleStartEffect`, and what changed in between is
  caught up when the session starts again. A `LaunchedEffect` never collects a flow that registers
  with the system: a composition outlives `onStop`. The background entry points are the manifest
  receivers and, while a scenario, the badges or the bridge's mirroring need it, the notification
  listener — events a trigger cannot learn about later. Each is handled in one short step that reads
  only what it needs — the state a fired scenario's gate reads stays subscribed for five seconds
  after the last event and no longer; the Bluetooth and alarm receivers are switched off while no
  scenario listens, and the listener is released while nothing needs the posts. The third entry
  point is the Mac bridge's `CompanionDeviceService`: the system binds it for the paired Mac's
  presence while the bridge is on (and, measured, may keep it bound while the Mac is away), but all
  work lives in its window, which opens on the Mac's appearance and closes on the debounced absence
  — the GATT link, the presence debounce and the short content sessions die with it. Its one
  exception is a receiver of the phone's Bluetooth state, which lives as long as the system keeps
  the service bound and does nothing until Bluetooth is switched off or on: the system does not
  report the Mac again after the phone's radio restarts, so only that receiver brings the window
  back. The one user-started exception is the Home Assistant sign-in, whose loopback server waits
  behind the browser until its own timeout. `scripts/background-audit.sh` checks this on an
  emulator.

## Language
- Everything written by hand is in English: code, identifiers, comments, commit messages, README,
  CONTRIBUTING, test names and assertion messages.
- User-facing text is the exception. It never gets hardcoded in Compose — it lives in resources and
  in **two** locales. Every new or changed string goes into both
  `app/src/main/res/values/strings.xml` (English, default) and `values-cs/strings.xml` (Czech),
  under the same key. Czech has more plural quantity items, so the two files are not the same
  length; that is fine, missing keys are not.

## Documentation
Two prose documents, split by reader, and one reference beside them. `README.md` is the user
documentation, ordered from the general to the specific: what Cue: React & Act is and how it works,
the layout, each screen, the settings, scenarios with the catalogs of conditions, content and
actions, the Home Assistant setup and the examples; then the reference parts (backups, permissions,
battery) and last download, roadmap and contributing. It is written for someone using the phone:
plain words, the labels the app shows, and a short example wherever a description alone would not
make the point; how the code achieves it stays out. `CONTRIBUTING.md` is the contributor
documentation: building, the rules above in human terms, tests and CI, the build-change table,
release and baseline profile, the repository layout. `docs/scenario-format.md` is the reference of
the scenario JSON — every field, the notification value rules, the envelopes and the import checks;
the README links to it rather than repeating it.

Prose names the products in full, so the two never blur: the launcher is "Cue: React & Act", the
Mac app "Cue: Relay & Reply". A bare "Cue:" stays only where it quotes what a screen shows — the
label under the icon, the Mac's menu, *Do Not Disturb (Cue:)*, the blueprint's title — and where it
stands for the name shared by both, as in `NOTICE`.

**Every change ends with a review of both**, in this order:
1. Does the change touch something either file already describes? Then update that passage — a
   behaviour change is not finished until the README matches it, a change to the build, the checks
   or the rules not until CONTRIBUTING does, and a change to the scenario JSON not until
   `docs/scenario-format.md` does.
2. Does it not? Then judge whether it belongs in one of them: anything a user has to know, set up
   or work around goes into the README, anything a contributor has to know goes into CONTRIBUTING,
   internal refactors go into neither. If it belongs, write it in and say in the handover what was
   added and where.

Prose in every `.md` file here is hard-wrapped at **100 characters** — counted in characters, not
bytes, so an em dash costs one. Tables, fenced blocks and ASCII diagrams stay on one line however
long they get, and a wrap never lands inside a `code span` or a `[link](target)`, nor leaves a line
starting with `- `, `+ `, `1. `, `#` or `>`, which Markdown would read as a list, a heading or a
quote. Editing a sentence in the middle of a paragraph means **re-wrapping that whole paragraph**;
appending to the line you happened to touch is what leaves 300-character lines behind.

Three files sit outside those two: `SECURITY.md` (how to report a vulnerability, plus the threat
model), `docs/security-audit.md` (what a security review covered and the state of each area) and
`PRIVACY.md` (the store-facing privacy policy — no telemetry, on-device processing only). A change
that alters a control or a data flow any of them names has to update it. Licensing lives in
`LICENSE` (GPL text, unchanged), `NOTICE` (GPL-3.0-or-later notice, the section 7 terms on marking
and on trademark rights to the name and icon, third-party material) and `LICENSES/`; a new bundled
asset or library license goes into `NOTICE`, `LICENSES/` and `LicenseNotice`, which
`LicenseNoticesTest` checks.

## Build
```bash
./gradlew assembleDebug      # APK: app/build/outputs/apk/debug/
./gradlew installDebug       # install on every connected device (prompts, see below)
./gradlew test detekt        # before handing work over
```
Verification screenshots from the emulator belong in `build/shots/`, never in `/tmp`.

## Running commands
Bash runs in the Claude Code sandbox and needs no approval there; `.claude/settings.json` adds what
is specific to this project.
- `./gradlew`, `adb` and `emulator` run outside the sandbox: the Gradle daemon and the adb server
  talk over localhost, which the sandbox blocks. Run `./gradlew` as a call of its own, made of task
  names and only `--tests "<pattern>"`, `--console=plain`, `--continue`, `--offline`,
  `--stacktrace`, `--rerun-tasks`, `--no-configuration-cache` or `--status` —
  `.claude/hooks/gradle-allow.sh` approves exactly that. A pipe, `&&`, `cd`, a redirect, another
  flag or an unquoted `*` makes it prompt, or keeps it in the sandbox, where it fails.
- Tasks that install, uninstall or run on a device (`installDebug`, `connected…`, the baseline
  profile) always prompt, because they reach the user's daily phone too. Install on the emulator
  with `adb -e install -r app/build/outputs/apk/debug/app-debug.apk`: `adb -e` runs without a
  prompt, anything aimed at the phone asks, and so do `adb pull`, `push`, `forward` and `reverse`.
- Sandboxed commands cannot write what Gradle executes — `app/src`, `baselineprofile/src`, the
  `*.gradle.kts` files, `gradle/`, `gradle.properties`, `local.properties`, `.gradle/`, `config/` —
  so nothing can plant code that the unsandboxed Gradle then runs. Change these files with
  Edit/Write; a git command that rewrites them (`checkout`, `stash`, `rebase`) needs an unsandboxed
  retry.
- Inside the sandbox `keystore.properties` and the user's key directories are unreadable, and `ps`,
  `kill` and localhost connections are blocked.

Test scenarios: `fixtures/scenarios/*.json`. Commits: Conventional Commits, in English.

`ha/launcher_event.yaml` and its Czech twin `ha/launcher_event.cs.yaml` are packaged into the APK
straight from the repo root, so both blueprints, the manifest receiver and `HA_EVENT_ACTION` have to
keep matching — `HaEventContractTest` fails if they drift apart, if the two blueprints differ in
anything but their text, and it also checks that the README still documents both. A change to the
blueprint goes into both files.

## Peer project
The macOS side of the bridge is the Bridge repo,
[cue-bridge](https://github.com/jaroslav-sazma/cue-bridge), checked out at `../../MacOS/Bridge` from
this one — the menu-bar app "Cue: Relay & Reply", bundle id `app.cue.bridge`. The `main` branches
of both repos pair, both at `bridgeProtocolVersion 1`.
- Build this side with `./gradlew assembleDebug` and install it on the emulator with
  `adb -e install -r app/build/outputs/apk/debug/app-debug.apk` — never on the owner's Pixel
  unasked.
- Build the peer from the Bridge repo root with
  `xcodebuild -scheme Bridge -configuration Debug -derivedDataPath build build`. Launching the built
  app binds ports, so it is an unsandboxed step the user approves, or the tester starts it; never
  enable local binding in the sandbox settings.
- The canonical protocol spec `docs/bridge-protocol.md` and the shared test vectors in
  `fixtures/bridge/` live here, in the launcher repo; both are CC0-1.0, not GPL (`NOTICE`). The
  Bridge repo never restates the spec. It vendors an identical copy of the JSON vectors, not their
  README, in its own `fixtures/bridge/`, which its tests read by default (`BRIDGE_VECTORS_DIR`
  overrides), and a parity test fails on any byte that differs from this repo's copy while the two
  checkouts sit side by side. Vectors change here only; `tools/sync-vectors.sh` in the Bridge repo
  then copies them over, and the copy goes into the Bridge half of the change. A protocol change
  lands as a pair of commits, one in each repo, carrying the same version.
- Logs: macOS `/usr/bin/log stream --level debug --predicate 'subsystem == "app.cue.bridge"'` —
  zsh shadows `log` with a builtin, and debug events never reach `log show`; Android
  `adb -e logcat -s CueBridge`.
- `bridgectl` is the Bridge debug CLI, built beside the app and present in Debug builds only: with
  the app running, `build/Build/Products/Debug/bridgectl status` in the Bridge repo prints the
  pairing, pairing mode, listener, Bonjour, Bluetooth, the published L2CAP channel with its PSM,
  the connection with its carrier (`tcp` or `l2cap`) and the last state-sync, and exits non-zero
  when no Debug app answers.
- End-to-end smoke: build both, install this side on the emulator, and have the owner start the
  Bridge app (the sandbox blocks `open`) and choose **Pair Phone…**. The emulator has neither
  Bluetooth nor the Mac's Bonjour, so pairing takes the debug path: in the launcher's Mac bridge
  settings turn on **Pair without Bluetooth** (the emulator reports companion-device support, so it
  starts off), keep `10.0.2.2` in the host field and enter the listener's port — shown in the Debug
  build's pairing window and by `bridgectl status`; nothing has to be set on the Mac. Tap **Pair**,
  then type the code the emulator shows into the Mac's window (the owner does, or it is read off a
  screenshot). The emulator's Wi-Fi must be on: a session dials TCP only over Wi-Fi or Ethernet.
  Verify with `bridgectl status` and both logs. Then turn the bridge off: the session ends, the
  phone's pairing is wiped, the log falls silent, and `scripts/background-audit.sh` matches a run
  with the bridge on and paired.
