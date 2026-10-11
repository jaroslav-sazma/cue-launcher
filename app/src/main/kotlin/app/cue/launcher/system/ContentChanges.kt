package app.cue.launcher.system

import android.content.ContentResolver
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal fun ContentResolver.contentChanges(uri: Uri): Flow<Unit> = callbackFlow {
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            trySend(Unit)
        }
    }
    registerContentObserver(uri, true, observer)
    send(Unit)
    awaitClose { unregisterContentObserver(observer) }
}

internal fun Cursor.text(column: Int): String? =
    if (isNull(column)) null else getString(column)?.trim()?.takeIf(String::isNotEmpty)
