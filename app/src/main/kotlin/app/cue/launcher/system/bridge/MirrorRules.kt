package app.cue.launcher.system.bridge

import android.app.Application
import android.app.Notification
import android.provider.Settings
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import app.cue.launcher.BuildConfig
import app.cue.launcher.R
import app.cue.launcher.data.bridge.BridgeConfigRepository
import app.cue.launcher.data.bridge.MirrorCandidate
import app.cue.launcher.data.bridge.MirrorSkip
import app.cue.launcher.data.bridge.mirrorSkip
import app.cue.launcher.system.bridge.BridgeMessage.NotifDetail
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// What both mirrors decide a notification by: the filter, the apps the user keeps off the Mac, and
// the lock screen's view of it.
@SingleIn(AppScope::class)
@Inject
class MirrorRules(private val application: Application, config: BridgeConfigRepository) {

    internal val exclusions: Flow<Set<String>> = config.settings.map { it.mirrorExcluded }.distinctUntilChanged()

    @Volatile
    private var unreadableLogged = false

    internal fun candidate(sbn: StatusBarNotification, rankings: RankingMap?): MirrorCandidate =
        mirrorCandidateOf(sbn, rankings, sensitiveShown())

    internal fun skip(candidate: MirrorCandidate, excluded: Set<String>): MirrorSkip? =
        mirrorSkip(candidate, application.packageName, fixturesExempt = BuildConfig.DEBUG, excluded = excluded)

    internal fun passes(sbn: StatusBarNotification, rankings: RankingMap?, excluded: Set<String>): Boolean =
        skip(candidate(sbn, rankings), excluded) == null

    internal fun detail(sbn: StatusBarNotification, candidate: MirrorCandidate, face: AppFace): NotifDetail {
        val redacted = candidate.lockscreen == Notification.VISIBILITY_PRIVATE
        return mirrorDetailOf(sbn, face, application.getString(R.string.bridge_mirror_hidden).takeIf { redacted })
    }

    // The lock screen's "sensitive content" switch is a hidden setting that apps may read. Should a
    // release stop allowing it, notifications are judged by their own and their channel's visibility.
    private fun sensitiveShown(): Boolean = try {
        Settings.Secure.getInt(application.contentResolver, LOCK_SCREEN_ALLOW_PRIVATE, 1) != 0
    } catch (_: SecurityException) {
        if (!unreadableLogged) {
            unreadableLogged = true
            BridgeLog.info("the lock screen's sensitive content setting cannot be read")
        }
        true
    }

    private companion object {
        const val LOCK_SCREEN_ALLOW_PRIVATE = "lock_screen_allow_private_notifications"
    }
}
