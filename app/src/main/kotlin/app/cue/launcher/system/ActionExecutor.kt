package app.cue.launcher.system

import android.app.ActivityOptions
import android.app.Application
import android.app.PendingIntent
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.net.Uri
import android.os.UserHandle
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.util.Log
import app.cue.launcher.data.ha.HaProbe
import app.cue.launcher.data.ha.HaServiceCall
import app.cue.launcher.data.scenario.AppProfile
import app.cue.launcher.data.scenario.NotificationReply
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.engine.ActivationStore
import app.cue.launcher.engine.ConditionMonitor
import app.cue.launcher.engine.HaStateSource
import app.cue.launcher.engine.SavedPlace
import app.cue.launcher.engine.WallClock
import app.cue.launcher.engine.executionOrder
import app.cue.launcher.engine.latestSavedPlace
import app.cue.launcher.engine.placeUri
import app.cue.launcher.system.bridge.BridgeLockMac
import app.cue.launcher.system.ha.HaGateway
import app.cue.launcher.system.media.MediaPlayers
import app.cue.launcher.system.notifications.NotificationReplySender
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@Suppress("TooManyFunctions", "LongParameterList")
@SingleIn(AppScope::class)
@Inject
class ActionExecutor(
    private val application: Application,
    private val launcherApps: LauncherAppsRepository,
    private val profiles: UserProfiles,
    private val systemActions: SystemActions,
    private val ha: HaGateway,
    private val haStates: HaStateSource,

    private val defaultApps: DefaultApps,

    private val notificationReply: NotificationReplySender,

    private val mediaPlayers: MediaPlayers,

    private val biometricGate: BiometricGate,

    private val places: PlaceReader,
    private val conditionMonitor: ConditionMonitor,
    private val activations: ActivationStore,
    private val clock: WallClock,

    // Lazy, like the bridge's other parts: a phone that never taps Lock Mac never builds the link.
    private val lockMac: Lazy<BridgeLockMac>,
) {

    private val service: LauncherApps = application.getSystemService(LauncherApps::class.java)

    // One prompt covers the whole tap, however many of its actions asked for it: the user confirmed
    // the tap, and a second prompt for the second action would read as the first one failing.
    suspend fun perform(actions: List<TapAction>, sourceBounds: Rect? = null): ActionOutcome {
        confirmationOutcome(actions)?.let { return it }
        return combinedOutcome(executionOrder(actions).map { run(it, sourceBounds) })
    }

    suspend fun perform(action: TapAction, sourceBounds: Rect? = null): ActionOutcome =
        perform(listOf(action), sourceBounds)

    private suspend fun confirmationOutcome(actions: List<TapAction>): ActionOutcome? {
        if (!confirmationRequired(actions)) return null
        return when (biometricGate.confirm()) {
            ConfirmationResult.GRANTED -> null
            ConfirmationResult.DISMISSED -> ActionOutcome.CONFIRMATION_DISMISSED
            ConfirmationResult.UNAVAILABLE -> ActionOutcome.CONFIRMATION_UNAVAILABLE
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private suspend fun run(action: TapAction, sourceBounds: Rect? = null): ActionOutcome =
        when (val route = actionRouteOf(action)) {
            is ActionRoute.MainActivity -> launchApp(route.packageName, route.profile, sourceBounds)
            is ActionRoute.Activity -> start(activityIntent(route), sourceBounds)
            is ActionRoute.ViewUri -> start(Intent(Intent.ACTION_VIEW, Uri.parse(route.uri)), sourceBounds)
            is ActionRoute.IntentUri -> startIntentUri(route.uri, sourceBounds)
            is ActionRoute.Shortcut -> startShortcut(route, sourceBounds)
            is ActionRoute.Capability -> startCapability(route, sourceBounds)
            is ActionRoute.Ringer -> systemActions.setRingerMode(route.mode)
            is ActionRoute.DoNotDisturb -> systemActions.setDoNotDisturb(route.enabled)
            ActionRoute.Torch -> systemActions.toggleTorch()
            ActionRoute.TvRemote -> tvRemoteOutcome(start(tvRemoteIntent(), sourceBounds))

            is ActionRoute.SystemScreen -> start(systemScreenIntent(route.screen), sourceBounds)

            is ActionRoute.HaService -> haService(route.call)
            ActionRoute.RememberPlace -> rememberPlace()
            ActionRoute.ShowPlace -> showPlace(sourceBounds)
            ActionRoute.LockMac -> lockMacOutcome(lockMac.value.lock())
            ActionRoute.Malformed -> {
                Log.w(TAG, "No handler for a malformed ${action::class.simpleName} action")
                ActionOutcome.FAILED
            }
        }

    // Asked before the fix: with nothing waiting for a place, the wait for the satellites would buy
    // nothing.
    private suspend fun rememberPlace(): ActionOutcome {
        if (!conditionMonitor.awaitsPlace()) return ActionOutcome.PLACE_NOT_AWAITED
        return when (val reading = places.read()) {
            is PlaceReading.Found -> {
                val place = SavedPlace(reading.latitude, reading.longitude, clock.nowMillis())
                // Once the fix is in, a screen going off must not leave the place half saved.
                val taken = withContext(NonCancellable) { conditionMonitor.onPlaceSaved(place) }
                if (taken) ActionOutcome.DONE else ActionOutcome.PLACE_NOT_AWAITED
            }

            PlaceReading.PermissionMissing -> ActionOutcome.LOCATION_PERMISSION_REQUIRED
            PlaceReading.LocationOff -> ActionOutcome.LOCATION_OFF
            PlaceReading.NotFound -> ActionOutcome.PLACE_NOT_FOUND
        }
    }

    private suspend fun showPlace(sourceBounds: Rect?): ActionOutcome {
        val place = latestSavedPlace(activations.activations(), clock.nowMillis()) ?: return ActionOutcome.NO_PLACE
        return start(Intent(Intent.ACTION_VIEW, Uri.parse(placeUri(place))), sourceBounds)
    }

    // A timeout nudges too: the call was sent and may well have run, so the state is worth rereading.
    private suspend fun haService(call: HaServiceCall): ActionOutcome {
        val probe = ha.callService(call)
        if (probe is HaProbe.Ok || probe == HaProbe.Timeout) haStates.nudge()
        return haActionOutcome(probe)
    }

    private fun startIntentUri(uri: String, sourceBounds: Rect?): ActionOutcome {
        val intent = deepLinkIntent(uri, application.packageName) ?: return ActionOutcome.FAILED
        val outcome = start(intent, sourceBounds)
        val fallback = deepLinkFallbackUrl(intent)
            ?.takeIf { outcome == ActionOutcome.TARGET_MISSING }
        return if (fallback == null) {
            outcome
        } else {
            start(Intent(Intent.ACTION_VIEW, Uri.parse(fallback)), sourceBounds)
        }
    }

    // A paused work profile needs nothing here: the system puts its own prompt to turn work apps on
    // in front of the main activity.
    private suspend fun launchApp(
        packageName: String,
        profile: AppProfile?,
        sourceBounds: Rect?,
    ): ActionOutcome {
        val component = launcherApps.mainActivityOf(packageName, profile) ?: return ActionOutcome.TARGET_MISSING
        return if (launcherApps.launch(component, sourceBounds)) ActionOutcome.DONE else ActionOutcome.FAILED
    }

    suspend fun openCalendar(action: TapAction? = null, sourceBounds: Rect? = null): ActionOutcome {
        if (action != null) return perform(action, sourceBounds)
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR),
            Intent(
                Intent.ACTION_VIEW,
                CalendarContract.CONTENT_URI.buildUpon().appendPath(CALENDAR_TIME_PATH).build(),
            ),
        )
        return intents.firstNotNullOfOrNull { intent ->
            start(intent, sourceBounds).takeIf { it == ActionOutcome.DONE }
        } ?: ActionOutcome.TARGET_MISSING
    }

    // The session's own activity is the player's now-playing screen; the app's launcher entry only
    // brings back whatever screen it was left on.
    suspend fun openMedia(packageName: String, sourceBounds: Rect? = null): ActionOutcome {
        val session = mediaPlayers.sessionActivity(packageName)
        if (session != null && sendActivity(session)) return ActionOutcome.DONE
        return launchApp(packageName, profile = null, sourceBounds)
    }

    // A pending intent runs with its creator's rights, and a player sitting in the background has
    // none to start an activity; the launcher, being on screen, lends it its own.
    private fun sendActivity(intent: PendingIntent): Boolean = try {
        val options = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE,
        )
        intent.send(application, 0, null, null, null, null, options.toBundle())
        true
    } catch (canceled: PendingIntent.CanceledException) {
        Log.w(TAG, "The player's session activity is gone", canceled)
        false
    }

    fun openAlarms(sourceBounds: Rect? = null): ActionOutcome =
        start(Intent(AlarmClock.ACTION_SHOW_ALARMS), sourceBounds)

    fun openWallpaper(): ActionOutcome {
        WALLPAPER_PICKER_PACKAGES.forEach { picker ->
            val direct = Intent(Intent.ACTION_SET_WALLPAPER).setPackage(picker)
            if (start(direct, sourceBounds = null) == ActionOutcome.DONE) return ActionOutcome.DONE
        }
        return start(chooser(Intent(Intent.ACTION_SET_WALLPAPER)), sourceBounds = null)
    }

    suspend fun openSearch(action: TapAction? = null, sourceBounds: Rect? = null): ActionOutcome {

        val browser = if (action == null) defaultApps.browserPackage() else null
        return searchbarTargets(action, browser)
            .firstNotNullOfOrNull { target ->
                searchOutcome(target, sourceBounds).takeIf { it == ActionOutcome.DONE }
            }
            ?: ActionOutcome.TARGET_MISSING
    }

    private suspend fun searchOutcome(target: SearchTarget, sourceBounds: Rect?): ActionOutcome =
        when (target) {

            is SearchTarget.Action -> perform(target.action, sourceBounds)

            is SearchTarget.SearchActivity -> start(
                Intent(Intent.ACTION_MAIN)
                    .setComponent(ComponentName(target.packageName, CHROMIUM_SEARCH_ACTIVITY)),
                sourceBounds,
            )

            SearchTarget.DefaultBrowser -> start(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
                sourceBounds,
            )
        }

    fun openContact(lookupUri: String, sourceBounds: Rect? = null): ActionOutcome =
        start(Intent(Intent.ACTION_VIEW, Uri.parse(lookupUri)), sourceBounds)

    fun dialContact(phoneNumber: String, sourceBounds: Rect? = null): ActionOutcome =
        start(Intent(Intent.ACTION_DIAL, phoneUri(TEL_SCHEME, phoneNumber)), sourceBounds)

    fun messageContact(phoneNumber: String, sourceBounds: Rect? = null): ActionOutcome =
        start(Intent(Intent.ACTION_SENDTO, phoneUri(SMS_SCHEME, phoneNumber)), sourceBounds)

    fun askAi(query: String, sourceBounds: Rect? = null): ActionOutcome {
        val prompt = Intent(Intent.ACTION_SEND)
            .setType(PROMPT_MIME)
            .putExtra(Intent.EXTRA_TEXT, query)
        val direct = start(Intent(prompt).setPackage(AI_ASSISTANT_PACKAGE), sourceBounds)
        return if (direct == ActionOutcome.DONE) direct else start(chooser(prompt), sourceBounds)
    }

    fun searchWeb(query: String, sourceBounds: Rect? = null): ActionOutcome {
        val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
        val direct = start(search, sourceBounds)
        if (direct == ActionOutcome.DONE) return direct
        return start(Intent(Intent.ACTION_VIEW, Uri.parse(webSearchUrl(query))), sourceBounds)
    }

    private fun chooser(intent: Intent): Intent = Intent.createChooser(intent, null)

    suspend fun reply(reply: NotificationReply, text: String): ActionOutcome =
        notificationReply.send(reply, text)

    private fun phoneUri(scheme: String, phoneNumber: String): Uri =
        Uri.fromParts(scheme, phoneNumber, null)

    private fun activityIntent(route: ActionRoute.Activity): Intent =
        Intent(Intent.ACTION_MAIN).setComponent(ComponentName(route.packageName, route.className))

    private fun start(intent: Intent, sourceBounds: Rect?): ActionOutcome = try {
        intent.sourceBounds = sourceBounds
        application.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ActionOutcome.DONE
    } catch (missing: ActivityNotFoundException) {
        Log.w(TAG, "No activity handles ${intentTarget(intent)} (${missing.javaClass.simpleName})")
        ActionOutcome.TARGET_MISSING
    } catch (denied: SecurityException) {

        Log.w(TAG, "Starting ${intentTarget(intent)} was denied (${denied.javaClass.simpleName})")
        ActionOutcome.FAILED
    }

    // The data of a link can be a phone number or a conversation id, and both the intent's own
    // toString() and the exception messages quote it — so a log names only where the intent went.
    private fun intentTarget(intent: Intent): String =
        intent.component?.flattenToShortString()
            ?: intent.`package`
            ?: "a ${intent.data?.scheme ?: "data-less"} ${intent.action ?: "intent"}"

    private suspend fun startShortcut(route: ActionRoute.Shortcut, sourceBounds: Rect?): ActionOutcome =
        inProfile(route.profile) { user ->
            shortcutOutcome(route.packageName) {
                service.startShortcut(route.packageName, route.shortcutId, sourceBounds, null, user)
            }
        }

    private suspend fun startCapability(route: ActionRoute.Capability, sourceBounds: Rect?): ActionOutcome =
        inProfile(route.profile) { user ->
            val shortcut = withContext(Dispatchers.Default) { shortcutWith(route.packageName, route.capability, user) }
            if (shortcut == null) {
                ActionOutcome.TARGET_MISSING
            } else {
                shortcutOutcome(route.packageName) { service.startShortcut(shortcut, sourceBounds, null) }
            }
        }

    // A work action on a phone without a work profile has nowhere to go. A paused or locked profile
    // answers every shortcut query with nothing, which would read as the target being gone.
    private suspend fun inProfile(
        profile: AppProfile?,
        start: suspend (UserHandle) -> ActionOutcome,
    ): ActionOutcome {
        val user = withContext(Dispatchers.Default) { profiles.userFor(profile) }
            ?: return ActionOutcome.TARGET_MISSING
        val readable = withContext(Dispatchers.Default) { profiles.isReadable(user) }
        return if (readable) start(user) else ActionOutcome.WORK_PAUSED
    }

    private fun shortcutWith(packageName: String, capability: String, user: UserHandle): ShortcutInfo? {
        val shortcuts = service.appShortcutInfos(packageName, user, TAG)
        val id = shortcutForCapability(

            candidates = shortcuts.map { info ->
                ShortcutCandidate(
                    id = info.id,
                    capabilities = info.capabilities.map { it.name },
                    enabled = info.isEnabled,
                )
            },
            capability = capability,
        )
        return shortcuts.firstOrNull { it.id == id }
    }

    private inline fun shortcutOutcome(packageName: String, start: () -> Unit): ActionOutcome =
        startShortcutOutcome(packageName, TAG, start)

    private companion object {
        const val TAG = "ActionExecutor"

        const val CALENDAR_TIME_PATH = "time"

        const val PROMPT_MIME = "text/plain"

        const val TEL_SCHEME = "tel"
        const val SMS_SCHEME = "smsto"

        val WALLPAPER_PICKER_PACKAGES = listOf(
            "com.google.android.apps.wallpaper",
            "com.android.wallpaper",
        )
    }
}
