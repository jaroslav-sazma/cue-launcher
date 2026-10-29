package app.cue.launcher.system.bridge

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.cue.launcher.CueApplication
import app.cue.launcher.R
import kotlinx.coroutines.launch

// Shows nothing and finishes once the text is out; the send runs in the activity's scope, so it ends
// with it. EXTRA_PROCESS_TEXT_READONLY changes nothing, as no result is ever returned.
class ProcessTextActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = sharedText()
        val graph = (application as? CueApplication)?.graph
        // A recreated instance (a rotation mid-send) must not send the text a second time.
        if (savedInstanceState != null || text.isNullOrEmpty() || graph == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            val outcome = graph.sendToMac.send(text)
            Toast.makeText(applicationContext, messageOf(outcome), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // PROCESS_TEXT reaches us from a text-selection toolbar, ACTION_SEND from the share sheet; Chrome
    // and other web views hide most PROCESS_TEXT items, so the share sheet is the entry that always
    // shows. Both carry the selection as plain text and land here with the home screen stopped. The
    // extras are any app's to choose, and a bundle that cannot be unparcelled throws out of the
    // getter, which would take the launcher's process down with it.
    @Suppress("TooGenericExceptionCaught")
    private fun sharedText(): String? = try {
        when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            else -> null
        }?.toString()
    } catch (cause: RuntimeException) {
        BridgeLog.warn("Send to Mac dropped, unreadable extras", cause)
        null
    }

    private fun messageOf(outcome: SendToMacOutcome): Int = when (outcome) {
        SendToMacOutcome.SENT -> R.string.bridge_send_sent
        SendToMacOutcome.NOT_PAIRED -> R.string.bridge_send_not_paired
        SendToMacOutcome.TOO_LONG -> R.string.bridge_send_too_long
        SendToMacOutcome.NO_LOCAL_NETWORK -> R.string.bridge_send_no_local_network
        SendToMacOutcome.UNREACHABLE -> R.string.bridge_send_unreachable
    }
}
