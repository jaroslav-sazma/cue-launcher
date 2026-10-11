package app.cue.launcher.system.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import app.cue.launcher.data.scenario.NotificationReply
import app.cue.launcher.engine.ReplyCandidate
import app.cue.launcher.engine.replyCandidateIndex

fun notificationReplyOf(key: String, notification: Notification): NotificationReply? {
    val candidates = notification.replyCandidates()
    val index = replyCandidateIndex(candidates) ?: return null
    return NotificationReply(key = key, hint = candidates[index].hint)
}

internal fun replyTargetOf(notification: Notification): ReplyTarget? {
    val index = replyCandidateIndex(notification.replyCandidates()) ?: return null
    val action = notification.actions.orEmpty()[index]
    val input = action.freeFormInput()
    val intent = action.actionIntent
    return if (input == null || intent == null) null else ReplyTarget(intent = intent, input = input)
}

internal data class ReplyTarget(
    val intent: PendingIntent,
    val input: RemoteInput,
)

private fun Notification.replyCandidates(): List<ReplyCandidate> =
    actions.orEmpty().map { action -> action.toReplyCandidate() }

private fun Notification.Action.toReplyCandidate(): ReplyCandidate {
    val input = freeFormInput()
    return ReplyCandidate(
        hasFreeFormInput = actionIntent != null && input != null,
        isReplySemantic = semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY,
        hint = input?.label?.toString()?.takeIf(String::isNotBlank),
    )
}

private fun Notification.Action.freeFormInput(): RemoteInput? =
    remoteInputs?.firstOrNull { it.allowFreeFormInput }
