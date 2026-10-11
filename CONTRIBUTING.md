# Contributing to Cue:

Thank you for looking into it. This file is for anyone who wants to build Cue:, change it or help
with a release; what the launcher does and how to use it is in the [README](README.md).

## Contents

- [Reporting bugs and security issues](#reporting-bugs-and-security-issues)
- [Building](#building)
- [Rules the code follows](#rules-the-code-follows)
- [Translations](#translations)
- [Documentation](#documentation)
- [Tests and checks](#tests-and-checks)
- [When the build changes](#when-the-build-changes)
- [Release build](#release-build)
- [Baseline profile](#baseline-profile)
- [Repository layout](#repository-layout)
- [Commits and pull requests](#commits-and-pull-requests)

---

## Reporting bugs and security issues

A **bug** goes into a GitHub issue. The quickest way there is *long-press the home screen → Launcher
settings → Scenarios → ⋮ → About → Report a bug*: it opens the form in the browser with the phone,
the Android build and the Cue: version already filled in. By hand, the Android build is under
*Settings → About phone → Android version → Build number* and the version is in the same About
sheet. Then describe the steps that lead to it and what you expected instead. If a scenario is
involved, attach it — *Launcher settings → Backups* exports it, and the README section
[Placeholders and portability](README.md#placeholders-and-portability) says what such a file carries
before you share it. For a crash, a log helps: with the phone connected to a computer and the
launcher running, `adb logcat -d --pid=$(adb shell pidof app.cue.launcher)` prints what it wrote.

A **vulnerability** never goes into a public issue. [SECURITY.md](SECURITY.md) describes how to
report it privately and what the launcher defends against.

---

## Building

Cue: is one Gradle module of Kotlin and Jetpack Compose (Material 3 Expressive), built for
Android 17 and newer (minSdk and targetSdk 37).

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
  `designsystem` package; feature code uses no literal colour or size.
- **Material 3 Expressive for the settings and dialogs.** The theme is `MaterialExpressiveTheme` on
  the alpha Compose BOM on purpose, so a new piece of UI reaches for the expressive component first
  — `ToggleButton` with the connected button-group shapes rather than `SegmentedButton`,
  `FloatingToolbar` rather than a hand-made bar, the expressive motion schemes rather than raw
  springs. Opting into `@ExperimentalMaterial3ExpressiveApi` is expected. The launcher surface
  itself — home screen, tiles, widget panel, drawer — is the exception: it follows the project's own
  glass design and its tokens, and Material components do not belong there.
- **Permissions just in time.** Nothing is requested up front. A permission is asked for at the
  moment a feature needs it, after the launcher has said what it reads and why, and a denial never
  blocks the app — only that feature stops, and it says why.
- **Nothing runs behind the home screen that could wait for it.** Whatever follows the system —
  callbacks, observers, receivers, timers, polling — lives in `ForegroundSession.run()` or behind
  `collectAsStateWithLifecycle` / `LifecycleStartEffect`, and what changed meanwhile is caught up
  when the home screen comes back. A `LaunchedEffect` never collects a flow that registers with the
  system, because a composition outlives `onStop`. The only background entry points are the
  manifest receivers and, while a scenario or the badges need it, the notification listener; each
  event is handled in one short step. The README section
  [Battery and background](README.md#battery-and-background) describes the result as a user sees
  it, and `scripts/background-audit.sh` checks it on an emulator.
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
the Home Assistant setup and examples, then permissions, battery, download and the roadmap. It is
written for someone using the phone — plain words, the labels the app shows, a short example where
a description alone would not do — and leaves out how the code works.
[`docs/scenario-format.md`](docs/scenario-format.md) is the reference of the scenario JSON: every
field, the notification value rules and the import checks. This file is the contributor
documentation. **Every change ends with a review of the README and this file**: a behaviour a user
sees is not finished until the README describes it, a change to the build, the checks or the rules
above is not finished until this file does, and a change to the scenario JSON not until the format
reference does. Anything a user has to know, set up or work around belongs in the README; internal
refactors belong in neither.

`SECURITY.md` (reporting and the threat model), `docs/security-audit.md` (what a review covered)
and `PRIVACY.md` (the privacy policy, also the one the planned Google Play listing will link to)
change whenever a control or a data flow they name changes.

Prose in every `.md` file is hard-wrapped at 100 characters, counted in characters rather than
bytes. Tables, fenced blocks and diagrams stay on one line however long they get, a wrap never
lands inside a `code span` or a link, and editing a sentence means re-wrapping its whole paragraph.
Examples in the documentation are in English.

---

## Tests and checks

```bash
./gradlew test detekt                 # unit tests + static analysis
./gradlew lintRelease                 # Android lint on the release variant
./gradlew verifyDebugMergedManifest   # no library starts code of its own
./gradlew licensee                    # every library's license has its text in LICENSES/
scripts/background-audit.sh           # on an emulator: nothing may run behind the home screen
```

Unit tests are JUnit (Jupiter) and run on the JVM; the fixtures in `fixtures/scenarios/` double as
round-trip tests of the scenario format, and some tests read the README, the blueprints and the bug
report form to keep them in step with the code. Gradle does not see such reads, so the files are
listed as inputs of the test task in `app/build.gradle.kts` (`testedRepositoryFiles`); a test that
reads another one adds it there, or a change to only that file leaves the tests up to date.
Screenshot tests (`./gradlew validateDebugScreenshotTest`) render the settings screens through
layoutlib.

`scripts/background-audit.sh [seconds] [--with-listener]` sends a debug build of the launcher behind
Settings on a connected emulator and reposts a notification every two seconds, the way a progress
notification is updated. It then fails if the process holds a content observer, a registered
receiver, a binding to another app's service or one of its own other than the notification listener,
keeps the listener bound although the need it stored says nothing wants it, is in another state than
the one the listener calls for — a bound service while it is bound, otherwise the home state or a
cached one — or spent more than 0.3 s of CPU a minute. `--with-listener` grants notification access
for the run; the listener binds only while badges are on or a scenario watches notifications. The
reposted notification stays in the shade under the tag `cue-audit`, as the shell cannot cancel it.

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

On `main` a second job submits the resolved dependency graph to GitHub, which is what lets
Dependabot alerts see vulnerable libraries in a Gradle project. Renovate (`renovate.json`) proposes
the updates themselves and opens an update with a `security` label as soon as an advisory names a
version in use. Every action in the workflow is pinned to a full commit hash with the version as a
comment, because a tag can be moved to other code and a commit cannot; Renovate keeps the pins
current. The workflow's token is read-only, except for the dependency job, which needs to write the
graph.

---

## When the build changes

What has to follow a change to the build, and when:

| Change | What has to follow |
|---|---|
| A dependency update — a Renovate pull request or by hand | CI green, release build included. After a Compose BOM, AGP, Kotlin or R8 update, also run the release APK on a device: a keep-rule problem can compile and only fail at runtime. Regenerate the baseline profile when Compose or anything on the startup path moved. |
| A new library | `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` shows what it pulls in. Nothing that reports anywhere — analytics, crash reporting, ads, Play Services — belongs in the app. A provider or startup initializer such a library adds to start itself fails `check` until it is removed in the manifest or allowlisted, and no library may bring a boot receiver or the boot permission, which `BootManifestTest` holds. If it talks to the network, `PRIVACY.md`, `SECURITY.md` and the audit change with it. If it needs keep rules its own artifact does not ship, they go into `app/proguard-rules.pro` with the reason. Its license goes into `NOTICE`, and its license text into `LICENSES/` and the app's list of licenses (`LicenseNotice`) if it is not there yet; `LicenseNoticesTest` keeps the last two in step. A license that is new to the project fails `licensee` until it is added to the `licensee` block in `app/build.gradle.kts` together with its text. |
| `compileSdk`, `compileSdkMinor` or build-tools | The `packages:` line in **both** jobs of `ci.yml` installs the same platform and build-tools. |
| The JDK | `./gradlew updateDaemonJvm --jvm-version=<n>`, `compileOptions`, detekt's `jvmTarget` in `app/build.gradle.kts` and `java-version` in both CI jobs move together. detekt 1.23.8 cannot run on JVM 25 or newer. |
| The Gradle version | `./gradlew wrapper --gradle-version <x>`, never a hand edit of the URL: the wrapper writes the `distributionSha256Sum` that pins the download. |
| A Room entity or DAO query that changes the schema | From the first public release on: the database version goes up, a migration comes with it, and the schema exported into `app/schemas/` is committed. A user's database is never thrown away. |
| The manifest — a permission, a component, an intent filter, an `exported` flag | `SECURITY.md` (the threat model), `docs/security-audit.md` (entry points, permissions), `PRIVACY.md` when data flows change, and the README's [Permissions](README.md#permissions) table. |
| A GitHub Action, new or updated | Pinned to a full commit hash with the version as a comment. Renovate keeps existing pins current. |
| Startup or the first interactions | Regenerate the baseline profile, see below. |
| The Home Assistant blueprint | Both `ha/launcher_event.yaml` and `ha/launcher_event.cs.yaml`, the receiver and `HA_EVENT_ACTION` stay in agreement; `HaEventContractTest` fails otherwise. |
| The signing key | Never into the repository; `keystore.properties` points at it, and it is backed up in two independent places. It cannot be replaced: once Cue: is on Play, Play keeps signing with its enrolled copy, but a lost key means no APK on GitHub can ever update an installed copy again, and a new key would also change the certificate under [Verifying a download](README.md#verifying-a-download). |

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
4. Once Cue: is on Play, upload `app/build/outputs/bundle/release/app-release.aab` there. The bundle
   carries its own R8 mapping, so Play deobfuscates crash reports without a separate upload.
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
`app.cue.launcher`, and so does an import that does not end on the seeded home screen. Cue: is
started through the HOME intent, never the LAUNCHER one. Besides the cold start, the journeys cover
the widget panel, the folder, paging the 2×2 stack, the dynamic folder, the drawer's vault, a query
typed letter by letter with a typo in it and launched with Enter, and coming back from another app
the way the home button does.

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
| `LICENSE`, `NOTICE`, `LICENSES/` | the GPL text; the license notice with its section 7 terms and the third-party material; the license texts that material needs. All three are packaged into the APK by the `licenseAssets` task and shown in the app |
| `art/icon/`, `fastlane/metadata/android/` | the icon master files; the store listing texts, screenshots and graphics. They are GPL files like the rest, but the GPL grants no trademark rights to the name and icon, see `NOTICE` |
| `fixtures/scenarios/` | example scenarios in the JSON contract; also used by round-trip tests |
| `fixtures/baselineprofile/` | the whole configuration the baseline profile journeys seed the measured app with; packaged into the test APK only, not an example to import |
| `ha/launcher_event.yaml`, `ha/launcher_event.cs.yaml` | the Home Assistant blueprint in English and Czech, packaged into the APK straight from here |
| `baselineprofile/` | baseline profile generator |
| `config/detekt/detekt.yml` | deviations from the default detekt config |
| `scripts/background-audit.sh` | emulator check that nothing needless runs while the home screen is not shown |
| `gradle/libs.versions.toml` | version catalog |
| `keystore.example.properties` | the template for the gitignored `keystore.properties` that signs the release build |
| `.github/workflows/ci.yml` | CI: build, lint, tests, detekt, merged manifest and license checks; dependency graph for Dependabot |
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
