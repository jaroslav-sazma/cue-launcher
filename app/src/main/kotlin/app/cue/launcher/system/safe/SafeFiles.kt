package app.cue.launcher.system.safe

import android.Manifest.permission.MANAGE_DOCUMENTS
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.DocumentsContract
import android.util.Log

// The system's Files app opens a root handed to it this way, as the Storage settings open theirs.
// NEW_TASK because the launcher's task is the home task: the next press of Home would clear it.
internal fun Context.openSafeRoot(): Boolean {
    val root = DocumentsContract.buildRootUri(safeAuthority(this), SafeDocuments.ROOT_ID)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(root, DocumentsContract.Root.MIME_TYPE_ITEM)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    documentUiFor(intent)?.let(intent::setPackage)
    return try {
        startActivity(intent)
        true
    } catch (missing: ActivityNotFoundException) {
        Log.w(TAG, "No file manager opens the vault (${missing.javaClass.simpleName})")
        false
    }
}

// Only the system's document UI holds MANAGE_DOCUMENTS and can browse the vault. Files by Google takes
// the same intent too, only to hand it on there, so without a package the user first gets a chooser.
private fun Context.documentUiFor(view: Intent): String? {
    val flags = PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
    return packageManager.queryIntentActivities(view, flags)
        .map { it.activityInfo.packageName }
        .firstOrNull { packageManager.checkPermission(MANAGE_DOCUMENTS, it) == PackageManager.PERMISSION_GRANTED }
}

private const val TAG = "CueSafe"
