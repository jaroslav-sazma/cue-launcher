package app.cue.launcher.data.bridge

import android.app.Notification
import android.app.NotificationManager

data class MirrorCandidate(
    val packageName: String,
    val channelId: String = "",
    val ongoing: Boolean = false,
    val foregroundService: Boolean = false,
    val groupSummary: Boolean = false,
    val promotedOngoing: Boolean = false,
    val category: String? = null,
    val mediaSession: Boolean = false,
    val importance: Int = NotificationManager.IMPORTANCE_DEFAULT,
    val clearable: Boolean = true,
    val lockscreen: Int = Notification.VISIBILITY_PUBLIC,
)

enum class MirrorSkip(val wire: String) {
    OWN_PACKAGE("own-package"),
    EXCLUDED("excluded"),
    SECRET("secret"),
    GROUP_SUMMARY("group-summary"),
    ONGOING("ongoing"),
    PROMOTED_ONGOING("promoted-ongoing"),
    MEDIA("media"),
    NOT_CLEARABLE("not-clearable"),
    LOW_IMPORTANCE("low-importance"),
}

// The checks run in this order and the first that matches names the reason. A ranking the listener
// has not delivered yet reads as unspecified and passes, so a race never hides a notification.
// The fixture generator's channels are exempt from the own-package rule in debug builds only.
fun mirrorSkip(
    candidate: MirrorCandidate,
    ownPackage: String,
    fixturesExempt: Boolean,
    excluded: Set<String> = emptySet(),
): MirrorSkip? {
    val fixture = fixturesExempt && candidate.channelId.startsWith(BRIDGE_FIXTURE_CHANNEL_PREFIX)
    return when {
        candidate.packageName == ownPackage && !fixture -> MirrorSkip.OWN_PACKAGE
        candidate.packageName in excluded -> MirrorSkip.EXCLUDED
        candidate.lockscreen == Notification.VISIBILITY_SECRET -> MirrorSkip.SECRET
        else -> kindSkip(candidate)
    }
}

private fun kindSkip(candidate: MirrorCandidate): MirrorSkip? {
    val lowImportance = candidate.importance != NotificationManager.IMPORTANCE_UNSPECIFIED &&
        candidate.importance < NotificationManager.IMPORTANCE_DEFAULT
    return when {
        candidate.groupSummary -> MirrorSkip.GROUP_SUMMARY
        candidate.ongoing || candidate.foregroundService -> MirrorSkip.ONGOING
        candidate.promotedOngoing -> MirrorSkip.PROMOTED_ONGOING
        candidate.category == Notification.CATEGORY_TRANSPORT || candidate.mediaSession -> MirrorSkip.MEDIA
        !candidate.clearable -> MirrorSkip.NOT_CLEARABLE
        lowImportance -> MirrorSkip.LOW_IMPORTANCE
        else -> null
    }
}

// What the phone's lock screen would show of the notification, which is what the Mac gets: SystemUI
// hides it when the app or the user's choice for its channel says secret, and redacts it when the
// user's choice for the channel says private, or the app does while the lock screen hides sensitive
// content. The app's own default is private, so that alone redacts nothing on a phone that shows it.
fun mirrorLockscreen(own: Int, channelOverride: Int, sensitiveShown: Boolean): Int = when {
    own == Notification.VISIBILITY_SECRET || channelOverride == Notification.VISIBILITY_SECRET ->
        Notification.VISIBILITY_SECRET
    channelOverride == Notification.VISIBILITY_PRIVATE -> Notification.VISIBILITY_PRIVATE
    own == Notification.VISIBILITY_PRIVATE && !sensitiveShown -> Notification.VISIBILITY_PRIVATE
    else -> Notification.VISIBILITY_PUBLIC
}

const val BRIDGE_FIXTURE_CHANNEL_PREFIX = "cue.bridge.fixture."
