# Cue: (app.cue.launcher) — instructions for AI executors

Trigger-driven Android launcher "Cue:" (planned Play name: "Cue: React & Act"). Kotlin + Compose,
minSdk 37, applicationId `app.cue.launcher`. GPL-3.0-or-later.

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
  receivers and, while a scenario or the badges need it, the notification listener — events a
  trigger cannot learn about later. Each is handled in one short step that reads only what it needs
  — the state a fired scenario's gate reads stays subscribed for five seconds after the last event
  and no longer; the Bluetooth and alarm receivers are switched off while no scenario listens, and
  the listener is released while nothing needs the posts. The one user-started exception is the Home
  Assistant sign-in, whose loopback server waits behind the browser until its own timeout.
  `scripts/background-audit.sh` checks this on an emulator.

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
documentation, ordered from the general to the specific: what Cue: is and how it works, the layout,
each screen, the settings, scenarios with the catalogs of conditions, content and actions, the Home
Assistant setup and the examples; then the reference parts (backups, permissions, battery) and
last download, roadmap and contributing. It is written for someone using the phone: plain words,
the labels the app shows, and a short example wherever a description alone would not make the point;
how the code achieves it stays out. `CONTRIBUTING.md` is the contributor documentation: building,
the rules above in human terms, tests and CI, the build-change table, release and baseline profile,
the repository layout. `docs/scenario-format.md` is the reference of the scenario JSON — every
field, the notification value rules, the envelopes and the import checks; the README links to it
rather than repeating it.

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
