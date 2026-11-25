package app.cue.launcher.system.safe

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.util.Log

// The system's Files app opens a root handed to it this way, as the Storage settings open theirs.
// NEW_TASK because the launcher's task is the home task: the next press of Home would clear it.
internal fun Context.openSafeRoot(): Boolean {
    val root = DocumentsContract.buildRootUri(safeAuthority(this), SafeDocuments.ROOT_ID)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(root, DocumentsContract.Root.MIME_TYPE_ITEM)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        startActivity(intent)
        true
    } catch (missing: ActivityNotFoundException) {
        Log.w(TAG, "No file manager opens the vault (${missing.javaClass.simpleName})")
        false
    }
}

private const val TAG = "CueSafe"
