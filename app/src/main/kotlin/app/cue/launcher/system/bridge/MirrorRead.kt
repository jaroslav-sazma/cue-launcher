package app.cue.launcher.system.bridge

import android.app.Notification
import android.app.NotificationManager
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import app.cue.launcher.data.bridge.MirrorCandidate
import app.cue.launcher.data.bridge.mirrorLockscreen
import app.cue.launcher.system.notifications.notificationReplyOf
import app.cue.launcher.system.notifications.notificationTextOf
import app.cue.launcher.system.notifications.notificationTitleOf

internal fun mirrorCandidateOf(
    sbn: StatusBarNotification,
    rankings: RankingMap?,
    sensitiveShown: Boolean,
): MirrorCandidate {
    val notification = sbn.notification
    val flags = notification.flags
    val ranking = Ranking()
    val ranked = rankings?.getRanking(sbn.key, ranking) == true
    val channelOverride = if (ranked) ranking.lockscreenVisibilityOverride else Ranking.VISIBILITY_NO_OVERRIDE
    return MirrorCandidate(
        packageName = sbn.packageName,
        channelId = notification.channelId.orEmpty(),
        ongoing = flags and Notification.FLAG_ONGOING_EVENT != 0,
        foregroundService = flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
        groupSummary = flags and Notification.FLAG_GROUP_SUMMARY != 0,
        promotedOngoing = flags and Notification.FLAG_PROMOTED_ONGOING != 0,
        category = notification.category,
        mediaSession = notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION),
        importance = if (ranked) ranking.importance else NotificationManager.IMPORTANCE_UNSPECIFIED,
        clearable = sbn.isClearable,
        lockscreen = mirrorLockscreen(notification.visibility, channelOverride, sensitiveShown),
    )
}

// A redacted notification goes as its public version, as the lock screen shows it, or as the app's
// name over the hidden text when the app has none. Its key stays the real one, so answering and
// closing it on the Mac still work.
internal fun mirrorDetailOf(
    sbn: StatusBarNotification,
    face: AppFace,
    hidden: String?,
): BridgeMessage.NotifDetail {
    val notification = sbn.notification
    val shown = if (hidden == null) {
        MirrorText(notificationTitleOf(notification), notificationTextOf(notification))
    } else {
        val public = notification.publicVersion
        redactedText(public?.let(::notificationTitleOf), public?.let(::notificationTextOf), face.label, hidden)
    }
    return BridgeMessage.NotifDetail(
        key = sbn.key,
        pkg = sbn.packageName,
        appLabel = face.label,
        title = clipText(shown.title.orEmpty(), MAX_TITLE_CHARS),
        text = clipText(shown.text.orEmpty(), MAX_TEXT_CHARS),
        postedAt = sbn.postTime,
        threadId = mirrorThreadId(sbn.packageName, notification.shortcutId, notification.group),
        canReply = notificationReplyOf(sbn.key, notification) != null,
        isClearable = sbn.isClearable,
        iconHash = face.icon?.hash,
        revision = mirrorRevisionOf(sbn),
    )
}

internal data class MirrorText(val title: String?, val text: String?)

internal fun redactedText(publicTitle: String?, publicText: String?, appLabel: String, hidden: String): MirrorText =
    if (publicTitle == null && publicText == null) {
        MirrorText(appLabel, hidden)
    } else {
        MirrorText(publicTitle ?: appLabel, publicText)
    }

// Every post of a key gets a new post time, which is all a revision has to tell apart.
internal fun mirrorRevisionOf(sbn: StatusBarNotification): Long = sbn.postTime

internal fun mirrorThreadId(packageName: String, shortcutId: String?, group: String?): String? {
    val conversation = shortcutId?.takeIf(String::isNotEmpty) ?: group?.takeIf(String::isNotEmpty)
    return conversation?.let { "$packageName/$it" }
}

// The whole active-set has to fit one frame, and the Mac shows a few lines at most; a cut never
// leaves half a surrogate pair behind.
internal fun clipText(text: String, max: Int): String {
    if (text.length <= max) return text
    val end = if (text[max - 1].isHighSurrogate()) max - 1 else max
    return text.substring(0, end)
}

internal const val MAX_TITLE_CHARS = 1_000
internal const val MAX_TEXT_CHARS = 4_000
