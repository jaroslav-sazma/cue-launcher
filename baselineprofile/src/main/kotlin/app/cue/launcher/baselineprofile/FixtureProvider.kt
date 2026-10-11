package app.cue.launcher.baselineprofile

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

const val SEED_FIXTURE = "home.json"

const val JSON_MIME = "application/json"

// The manifest declares the authority as ${applicationId}.fixtures.
fun seedFixtureUri(context: Context): Uri = Uri.Builder()
    .scheme("content")
    .authority("${context.packageName}.fixtures")
    .path(SEED_FIXTURE)
    .build()

class FixtureProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = JSON_MIME

    // A pipe rather than assets.openFd, which needs the asset stored uncompressed. The asset is opened
    // here, so a missing one reaches the import as unreadable. The copy must not throw on the helper's
    // thread, which would take the instrumentation down; the import then fails on the cut-off file.
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r" || uri.lastPathSegment != SEED_FIXTURE) throw FileNotFoundException(uri.toString())
        val asset = try {
            requireNotNull(context).assets.open(SEED_FIXTURE)
        } catch (e: IOException) {
            throw FileNotFoundException(e.message)
        }
        return openPipeHelper(uri, JSON_MIME, null, asset) { output, _, _, _, source: InputStream? ->
            runCatching {
                source?.use { input -> FileOutputStream(output.fileDescriptor).use(input::copyTo) }
            }.onFailure { Log.w(TAG, "Copying $SEED_FIXTURE into the pipe failed", it) }
        }
    }

    private companion object {

        const val TAG = "FixtureProvider"
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
