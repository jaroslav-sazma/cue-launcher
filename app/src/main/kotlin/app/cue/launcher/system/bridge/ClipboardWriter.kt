package app.cue.launcher.system.bridge

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.PersistableBundle
import app.cue.launcher.R
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

// Android lets any app write the clipboard in the background, so this needs no gate of its own;
// IncomingClipboard decides when a clip from the Mac may land.
@SingleIn(AppScope::class)
@Inject
class ClipboardWriter(private val application: Application) {

    // The size cap is the one the Mac sends under; a bigger clip could overrun the binder transaction.
    internal fun write(text: String, sensitive: Boolean) {
        if (!fitsClipboard(text)) {
            BridgeLog.event("clipboard not applied: too-large")
            return
        }
        val clip = ClipData.newPlainText(application.getString(R.string.app_name), text)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_REMOTE_DEVICE, true)
            if (sensitive) putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
        application.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        BridgeLog.event("clipboard applied sensitive=$sensitive")
    }
}

// A UTF-8 character is at least one byte, so a text longer in characters is over the limit already.
internal fun fitsClipboard(text: String): Boolean =
    text.length <= CLIPBOARD_MAX_BYTES && text.encodeToByteArray().size <= CLIPBOARD_MAX_BYTES

internal const val CLIPBOARD_MAX_BYTES = 100 * 1024
