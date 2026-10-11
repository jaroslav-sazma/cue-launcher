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

@SingleIn(AppScope::class)
@Inject
class NotificationReplySender(
    private val application: Application,
    private val link: NotificationListenerLink,
) {

    suspend fun send(reply: NotificationReply, text: String): ActionOutcome = withContext(Dispatchers.IO) {
        val draft = replyDraft(text) ?: return@withContext ActionOutcome.FAILED
        val notification = link.activeNotification(reply.key)?.notification
            ?: return@withContext ActionOutcome.NOTIFICATION_GONE

        val target = replyTargetOf(notification) ?: return@withContext ActionOutcome.NOTIFICATION_GONE
        send(target, draft)
    }

    private fun send(target: ReplyTarget, draft: String): ActionOutcome {
        val intent = Intent()
        val results = Bundle().apply { putCharSequence(target.input.resultKey, draft) }
        RemoteInput.addResultsToIntent(arrayOf(target.input), intent, results)

        RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)
        return try {
            target.intent.send(application, 0, intent)
            ActionOutcome.DONE
        } catch (canceled: PendingIntent.CanceledException) {
            Log.w(NOTIFICATION_LISTENER_TAG, "The reply cannot be sent", canceled)
            ActionOutcome.FAILED
        }
    }
}
