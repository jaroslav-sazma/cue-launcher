package app.cue.launcher.baselineprofile

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Condition
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

const val CUE_PACKAGE = "app.cue.launcher"

private const val CUE_ACTIVITY = "$CUE_PACKAGE.MainActivity"

private const val LAUNCH_TIMEOUT_MS = 10_000L
private const val ONBOARDING_TIMEOUT_MS = 5_000L
private const val SEED_CHECK_TIMEOUT_MS = 5_000L
private const val IMPORT_TIMEOUT_MS = 10_000L
private const val DRAWER_TIMEOUT_MS = 3_000L

private const val DRAWER_ATTEMPTS = 3

private const val SWIPE_STEPS = 12

private const val SETTLE_MS = 700L

// The way the system starts a home screen. startActivityAndWait() without an intent would take the
// LAUNCHER entry, the one another launcher uses while Cue: is only being tried out.
fun cueHomeIntent(): Intent = Intent(Intent.ACTION_MAIN)
    .addCategory(Intent.CATEGORY_HOME)
    .setComponent(ComponentName(CUE_PACKAGE, CUE_ACTIVITY))

fun UiDevice.awaitCue(): Boolean = wait(Until.hasObject(By.pkg(CUE_PACKAGE).depth(0)), LAUNCH_TIMEOUT_MS)

// What the home button does. startActivityAndWait() adds FLAG_ACTIVITY_CLEAR_TASK and would recreate
// the activity; a plain start hands the running singleTask instance to onNewIntent instead. That
// holds because the measured build is not the default home: its instance sits in an ordinary task
// the shell started, not in the home task the system keeps.
fun UiDevice.goHome() {
    executeShellCommand(
        "am start -W -a ${Intent.ACTION_MAIN} -c ${Intent.CATEGORY_HOME} -n $CUE_PACKAGE/$CUE_ACTIVITY",
    )
    check(awaitCue()) { "Cue: did not come back on the HOME intent" }
    settle()
}

fun UiDevice.dismissOnboardingIfPresent() {
    val done = wait(
        Until.findObject(cue().text(Pattern.compile("Zatím jen vyzkoušet|Pokračovat|Just try it out|Continue"))),
        ONBOARDING_TIMEOUT_MS,
    ) ?: return
    done.click()
    settle()
}

// An import adds scenarios alongside the ones already there, so it runs only on a home screen with
// no trace of the seed. The scenarios are saved before the configuration, so the tile (a scenario)
// and the folder (the configuration) together mark a complete seed; one without the other is an
// import that broke off, and importing again would only add the scenarios a second time.
fun UiDevice.seedHome() {
    pressHome()
    goHome()
    dismissOnboardingIfPresent()
    if (waitFor(SEED_CHECK_TIMEOUT_MS) { hasObject(SEEDED_FOLDER) || hasObject(SEEDED_TILE) }) {
        check(waitFor(SEED_CHECK_TIMEOUT_MS) { hasSeed() }) {
            "Only part of the seed is on the home screen (folder: ${hasObject(SEEDED_FOLDER)}, tile: " +
                "${hasObject(SEEDED_TILE)}). Uninstall $CUE_PACKAGE from the device and run again; " +
                "another import would duplicate the scenarios."
        }
    } else {
        importSeed()
    }
    pressHome()
    executeShellCommand("am force-stop $CUE_PACKAGE")
}

private fun UiDevice.importSeed() {
    val testApk = InstrumentationRegistry.getInstrumentation().context
    // The restore replaces the home screen, the docks, the widget panel, the appearance, the gestures
    // and the drawer settings with the hidden apps. Only a build signed with the test APK's own debug
    // key is seeded, never a release app.cue.launcher that is somebody's daily home screen.
    val signatures = testApk.packageManager.checkSignatures(testApk.packageName, CUE_PACKAGE)
    check(signatures == PackageManager.SIGNATURE_MATCH) {
        "$CUE_PACKAGE here is not signed with the test APK's key, so it is not seeded: the import would " +
            "replace its home screen and settings"
    }
    testApk.startActivity(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(seedFixtureUri(testApk), JSON_MIME)
            .setComponent(ComponentName(CUE_PACKAGE, CUE_ACTIVITY))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )
    // The heading stands above a failure as much as above the preview.
    checkNotNull(wait(Until.findObject(IMPORT_TITLE), IMPORT_TIMEOUT_MS)) {
        "The seed import did not reach its preview"
    }
    waitForIdle()
    check(!hasObject(IMPORT_READ_FAILED)) {
        "Cue: could not read $SEED_FIXTURE: the read grant, FixtureProvider " +
            "or the asset in the test APK failed"
    }
    val restore = checkNotNull(scrollTo(RESTORE_BACKUP)) {
        "The import preview offers no restore — is $SEED_FIXTURE still a whole configuration?"
    }
    restore.click()
    check(wait(Until.gone(RESTORE_BACKUP), IMPORT_TIMEOUT_MS)) { "The seed import did not finish" }
    goHome()
    check(waitFor(IMPORT_TIMEOUT_MS) { hasSeed() }) {
        "The seeded home screen did not appear after the import"
    }
}

private fun UiDevice.hasSeed(): Boolean = hasObject(SEEDED_FOLDER) && hasObject(SEEDED_TILE)

private fun UiDevice.waitFor(timeoutMs: Long, predicate: UiDevice.() -> Boolean): Boolean =
    wait(Condition<UiDevice, Boolean> { it.predicate() }, timeoutMs) == true

private fun UiDevice.scrollTo(selector: BySelector): UiObject2? {
    repeat(MAX_SCROLLS) {
        findObject(selector)?.let { return it }
        swipe(x(HALF), y(SCROLL_FROM), x(HALF), y(SCROLL_TO), SWIPE_STEPS)
        settle()
    }
    return findObject(selector)
}

// The panel is the page left of home, so it opens on a left-to-right swipe and closes on the way
// back. Swiping the other way first would arm the configurable action instead and leave the journey
// on the wrong page.
fun UiDevice.widgetPanelRoundTrip() {
    val y = displayHeight / 2
    swipe(x(LEFT_FRACTION), y, x(RIGHT_FRACTION), y, SWIPE_STEPS)
    settle()
    swipe(x(RIGHT_FRACTION), y, x(LEFT_FRACTION), y, SWIPE_STEPS)
    settle()
}

fun UiDevice.folderRoundTrip() = overlayRoundTrip(SEEDED_FOLDER, "The seeded folder is missing")

fun UiDevice.dynamicFolderRoundTrip() = overlayRoundTrip(DYNAMIC_FOLDER, "The 1×1 dynamic folder is missing")

// Up steps to the next page, down back to the first, so the seeded tile is on top again. The swipe
// runs inside the tile, in line with its text and at the height of its page indicator.
fun UiDevice.stackRoundTrip() {
    val indicator = checkNotNull(wait(Until.findObject(stackPage(1)), LAUNCH_TIMEOUT_MS)) {
        "The seeded 2×2 stack is missing"
    }
    val x = checkNotNull(findObject(SEEDED_TILE)) { "The seeded tile is not on top of the stack" }.visibleCenter.x
    val y = indicator.visibleCenter.y
    val half = (STACK_SWIPE_DP * displayDensity() / 2).toInt()
    swipe(x, y + half, x, y - half, SWIPE_STEPS)
    check(wait(Until.hasObject(stackPage(2)), LAUNCH_TIMEOUT_MS)) { "The stack did not step to its next page" }
    settle()
    swipe(x, y - half, x, y + half, SWIPE_STEPS)
    check(wait(Until.hasObject(stackPage(1)), LAUNCH_TIMEOUT_MS)) { "The stack did not step back" }
    settle()
}

private fun UiDevice.overlayRoundTrip(chip: BySelector, missing: String) {
    checkNotNull(wait(Until.findObject(chip), LAUNCH_TIMEOUT_MS)) { missing }.click()
    check(wait(Until.hasObject(FOLDER_OVERLAY), LAUNCH_TIMEOUT_MS)) { "The folder did not open" }
    settle()
    pressBack()
    check(wait(Until.gone(FOLDER_OVERLAY), LAUNCH_TIMEOUT_MS)) { "The folder did not close on back" }
    settle()
}

// The keyboard comes up with the drawer, so the first back would only put it away; the HOME intent
// closes the drawer the way the home button does.
fun UiDevice.drawerRoundTrip() {
    openDrawer()
    swipe(x(HALF), y(LIST_TOP), x(HALF), y(LIST_BOTTOM), SWIPE_STEPS)
    settle()
    swipe(x(HALF), y(LIST_BOTTOM), x(HALF), y(LIST_TOP), SWIPE_STEPS)
    settle()
    goHome()
}

// The field takes the focus by itself. A literal prefix first, then a transposition only the typo
// match finds; Chrome is named the same in every language, so Enter always has it on top.
fun UiDevice.drawerSearchLaunch() {
    openDrawer()
    type(LITERAL_QUERY)
    repeat(LITERAL_QUERY.length) {
        executeShellCommand("input keyevent KEYCODE_DEL")
        waitForIdle()
    }
    type(TYPO_QUERY)
    settle()
    pressEnter()
    check(wait(Until.hasObject(By.pkg(SEARCHED_APP).depth(0)), LAUNCH_TIMEOUT_MS)) {
        "Enter in the drawer search did not launch $SEARCHED_APP"
    }
    goHome()
}

fun UiDevice.leaveAndReturn() {
    executeShellCommand("am start -W -n $OTHER_APP")
    settle()
    goHome()
}

// The keyboard the drawer left behind can still be up, or flash up once more, on the home screen the
// HOME intent brings back; a swipe that lands on it never reaches Cue:, so it is tried again.
private fun UiDevice.openDrawer() {
    repeat(DRAWER_ATTEMPTS) {
        swipe(x(HALF), y(DRAWER_SWIPE_FROM), x(HALF), y(DRAWER_SWIPE_TO), SWIPE_STEPS)
        if (wait(Until.hasObject(DRAWER_SEARCH), DRAWER_TIMEOUT_MS)) {
            settle()
            return
        }
    }
    error("The drawer did not open with its search field focused")
}

private fun UiDevice.type(text: String) {
    text.forEach { character ->
        executeShellCommand("input text $character")
        waitForIdle()
    }
}

// The debug build is the emulator's home with the same texts, so every Cue: selector is scoped to
// the measured package.
private fun cue(): BySelector = By.pkg(CUE_PACKAGE)

// Both languages the app ships: the emulator may run in either.
private val IMPORT_TITLE = cue().text(Pattern.compile("Import preview|Náhled importu"))
private val IMPORT_READ_FAILED =
    cue().text(Pattern.compile("The file could not be read\\.|Soubor se nepodařilo přečíst\\."))
private val RESTORE_BACKUP = cue().text(Pattern.compile("Restore the backup|Obnovit zálohu"))
private val FOLDER_OVERLAY = cue().desc(Pattern.compile("Close the folder|Zavřít složku"))
private val DYNAMIC_FOLDER = cue().desc(Pattern.compile("(More actions|Další akce), .+"))
private val DRAWER_SEARCH = cue().clazz("android.widget.EditText").focused(true)

private fun stackPage(page: Int): BySelector = cue().desc(Pattern.compile("(Content|Obsah) $page (of|z) \\d+"))

// Names from the fixture. BaselineProfileFixtureTest in :app lays it out with the real engine and
// checks that the folder is in the static zone, this tile is on top of the 2×2 stack and the last
// 1×1 slot is a dynamic folder. The folder chip is matched by its description, because labels step
// aside at a large font size.
private val SEEDED_FOLDER = cue().descStartsWith("Tools, ")
private val SEEDED_TILE = cue().text("Wind down")

private const val LITERAL_QUERY = "chr"
private const val TYPO_QUERY = "chorm"
private const val SEARCHED_APP = "com.android.chrome"
private const val OTHER_APP = "com.android.settings/.Settings"

private const val MAX_SCROLLS = 8

// Past the stack's 48 dp step and its touch slop, short of a second step.
private const val STACK_SWIPE_DP = 80

private const val LEFT_FRACTION = 0.2
private const val RIGHT_FRACTION = 0.8
private const val HALF = 0.5
private const val DRAWER_SWIPE_FROM = 0.75
private const val DRAWER_SWIPE_TO = 0.25
private const val LIST_TOP = 0.3
private const val LIST_BOTTOM = 0.6
private const val SCROLL_FROM = 0.75
private const val SCROLL_TO = 0.35

private fun UiDevice.x(fraction: Double) = (displayWidth * fraction).toInt()

private fun UiDevice.y(fraction: Double) = (displayHeight * fraction).toInt()

private fun displayDensity(): Float =
    InstrumentationRegistry.getInstrumentation().context.resources.displayMetrics.density

private fun UiDevice.settle() {
    waitForIdle()
    SystemClock.sleep(SETTLE_MS)
}
