package app.cue.launcher.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import app.cue.launcher.R

// A screen another build always has can be missing here: Android Go ships without the
// notification-listener and Do Not Disturb access settings, and a managed device can disable the
// document picker. The launch then throws instead of opening anything.
internal fun <I> ActivityResultLauncher<I>.launchOrElse(input: I, onMissing: () -> Unit) {
    try {
        launch(input)
    } catch (missing: ActivityNotFoundException) {
        Log.w(TAG, "No activity handles ${contract.javaClass.simpleName} (${missing.javaClass.simpleName})")
        onMissing()
    }
}

internal fun <I> ActivityResultLauncher<I>.launchSystemScreen(input: I, context: Context) =
    launchOrElse(input) { Toast.makeText(context, R.string.system_screen_missing, Toast.LENGTH_LONG).show() }

// NEW_TASK because the launcher's task is the home task: a browser opened inside it would be cleared
// by the next press of Home. A package that turns out to have no browser activity falls back to any.
internal fun Context.openWebPage(url: String, browserPackage: String? = null) {
    val page = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val pinned = browserPackage?.let { Intent(page).setPackage(it) }
    if (pinned != null && tryStart(pinned)) return
    if (!tryStart(page)) Toast.makeText(this, R.string.browser_missing, Toast.LENGTH_LONG).show()
}

private fun Context.tryStart(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (missing: ActivityNotFoundException) {
    Log.w(TAG, "No activity opens the web page in ${intent.`package` ?: "any app"} (${missing.javaClass.simpleName})")
    false
}

private const val TAG = "ActivityLaunch"
