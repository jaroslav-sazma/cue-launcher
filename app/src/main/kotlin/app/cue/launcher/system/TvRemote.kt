package app.cue.launcher.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import app.cue.launcher.data.scenario.SystemActionType

// Mirrors the Remote tile the Google TV app adds to Quick Settings: the same action, package and
// flags, no extras. The activity is left to the resolver, so an update that renames it keeps working.
internal fun tvRemoteIntent(): Intent = Intent(TV_REMOTE_ACTION)
    .setPackage(GOOGLE_TV_PACKAGE)
    .addFlags(TV_REMOTE_FLAGS)

// Read anew at every call: Google TV can be installed, disabled or removed while the launcher runs.
fun unavailableSystemActions(context: Context): Set<SystemActionType> =
    if (tvRemoteResolves(context)) emptySet() else setOf(SystemActionType.TV_REMOTE)

private fun tvRemoteResolves(context: Context): Boolean =
    context.packageManager.queryIntentActivities(tvRemoteIntent(), PackageManager.ResolveInfoFlags.of(0))
        .isNotEmpty()

internal const val TV_REMOTE_ACTION = "com.google.android.apps.googletv.ACTION_VIRTUAL_REMOTE"

internal const val GOOGLE_TV_PACKAGE = "com.google.android.videos"

internal const val TV_REMOTE_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or
    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
    Intent.FLAG_ACTIVITY_NEW_DOCUMENT
