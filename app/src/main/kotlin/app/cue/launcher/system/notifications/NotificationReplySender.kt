package app.cue.launcher.system.notifications

import android.app.Application
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.util.Log
import app.cue.launcher.data.scenario.NotificationReply
import app.cue.launcher.engine.replyDraft
import app.cue.launcher.system.ActionOutcome
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class ReplyDelivery { SENT, GONE, NO_REPLY_ACTION, FAILED }

@SingleIn(AppScope::class)
@Inject
class NotificationReplySender(
    private val application: Application,
    private val link: NotificationListenerLink,
) {

    suspend fun send(reply: NotificationReply, text: String): ActionOutcome = when (deliver(reply.key, text)) {
        ReplyDelivery.SENT -> ActionOutcome.DONE
        ReplyDelivery.GONE, ReplyDelivery.NO_REPLY_ACTION -> ActionOutcome.NOTIFICATION_GONE
        ReplyDelivery.FAILED -> ActionOutcome.FAILED
    }

    internal suspend fun deliver(key: String, text: String): ReplyDelivery = withContext(Dispatchers.IO) {
        val draft = replyDraft(text) ?: return@withContext ReplyDelivery.FAILED
        val notification = link.activeNotification(key)?.notification
            ?: return@withContext ReplyDelivery.GONE

        val target = replyTargetOf(notification) ?: return@withContext ReplyDelivery.NO_REPLY_ACTION
        send(target, draft)
    }

    private fun send(target: ReplyTarget, draft: String): ReplyDelivery {
        val intent = Intent()
        val results = Bundle().apply { putCharSequence(target.input.resultKey, draft) }
        RemoteInput.addResultsToIntent(arrayOf(target.input), intent, results)

        RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)
        return try {
            target.intent.send(application, 0, intent)
            ReplyDelivery.SENT
        } catch (canceled: PendingIntent.CanceledException) {
            Log.w(NOTIFICATION_LISTENER_TAG, "The reply cannot be sent", canceled)
            ReplyDelivery.FAILED
        }
    }
}
