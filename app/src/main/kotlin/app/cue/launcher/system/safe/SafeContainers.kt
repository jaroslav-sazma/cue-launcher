package app.cue.launcher.system.safe

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import dev.zacsweers.metro.Inject
import java.io.Closeable
import java.io.FileNotFoundException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

// length is the descriptor's own size, -1 for a pipe; sizeHint is that or else what the provider's
// SIZE column claims, -1 when neither knows. Only length is trusted to check the layout before the
// key; sizeHint is only how much space the private copy reserves up front.
class SafeContainerStream(val input: InputStream, val length: Long, val sizeHint: Long = length) :
    Closeable by input

interface SafeContainers {

    // SecurityException when the read grant is gone, IOException when the file cannot be read.
    fun open(uri: String): SafeContainerStream

    fun release(uri: String)
}

@Inject
class ResolverSafeContainers(private val application: Application) : SafeContainers {

    // Google Drive hands out a pipe while it streams a file it has not cached, and a pipe has no
    // size; the provider's own column usually still has it.
    override fun open(uri: String): SafeContainerStream = fromProvider {
        val parsed = Uri.parse(uri)
        val descriptor = application.contentResolver.openFileDescriptor(parsed, "r")
            ?: throw FileNotFoundException("the provider opened nothing")
        val input = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
        var handed = false
        try {
            val length = descriptor.statSize.takeIf { it >= 0 } ?: -1L
            val sizeHint = if (length >= 0) length else queriedSize(parsed) ?: -1L
            SafeContainerStream(ProviderInputStream(input), length, sizeHint).also { handed = true }
        } finally {
            if (!handed) input.close()
        }
    }

    override fun release(uri: String) {
        try {
            application.contentResolver.releasePersistableUriPermission(
                Uri.parse(uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            Log.i(TAG, "The vault file's grant was already gone")
        }
    }

    // Only a hint, so whatever the provider throws here just leaves the size unknown.
    @Suppress("TooGenericExceptionCaught")
    private fun queriedSize(uri: Uri): Long? = try {
        application.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            cursor.takeIf { it.moveToFirst() && !it.isNull(0) }?.getLong(0)
        }
    } catch (_: RuntimeException) {
        null
    }

    private companion object {
        const val TAG = "CueSafe"
    }
}

// The resolver re-throws whatever another app's provider threw — IllegalArgumentException,
// IllegalStateException, NullPointerException, UnsupportedOperationException and more. None of it
// may crash the launcher: it is a file that cannot be read. SecurityException keeps its meaning.
@Suppress("TooGenericExceptionCaught")
internal inline fun <T> fromProvider(block: () -> T): T = try {
    block()
} catch (failure: SecurityException) {
    throw failure
} catch (failure: RuntimeException) {
    throw IOException("the provider failed (${failure::class.java.simpleName})", failure)
}

internal class ProviderInputStream(input: InputStream) : FilterInputStream(input) {

    override fun read(): Int = fromProvider { super.read() }

    override fun read(b: ByteArray, off: Int, len: Int): Int = fromProvider { super.read(b, off, len) }

    override fun skip(n: Long): Long = fromProvider { super.skip(n) }

    override fun available(): Int = fromProvider { super.available() }

    // A read-only stream that fails to close changes nothing about what was read, and an unlock that
    // already opened the vault must not turn into an error here.
    override fun close() {
        runCatching { super.close() }
    }
}
