package app.cue.launcher.system.safe

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.system.ErrnoException
import android.system.OsConstants
import android.util.Log
import android.webkit.MimeTypeMap
import app.cue.launcher.R
import app.cue.launcher.engine.safe.VaultEntry
import app.cue.launcher.engine.safe.VaultException
import app.cue.launcher.engine.safe.VaultReader
import java.io.FileNotFoundException
import java.io.IOException

// The vault's content, read-only, decrypted from the session's private copy one read at a time. The
// manifest declares it disabled; it is enabled from the unlock to the lock, so outside that window the
// system never creates it, and every call checks the expiry first. Only the system's document UI,
// holding MANAGE_DOCUMENTS, calls it directly; other apps reach a file only through the grant a
// picker hands them.
class SafeDocumentsProvider : DocumentsProvider() {

    // Runs before Application.onCreate, so it cannot reach the graph. A process that starts with the
    // provider enabled is one whose predecessor died unlocked: there is nothing to serve.
    override fun onCreate(): Boolean {
        context?.let(OpenSafe::disableIfLocked)
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: SafeDocuments.ROOT_PROJECTION)
        val session = live()?.session ?: return cursor
        cursor.addRowOf(
            SafeDocuments.rootRow(
                session.documentToken,
                session.archive,
                title(),
                session.displayName,
                R.mipmap.ic_launcher,
            ),
        )
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val session = requireLive().session
        val path = session.existingPath(documentId)
        val row = session.archive[path]?.let { session.rowOf(it) }
            ?: SafeDocuments.rootDocumentRow(session.documentToken, session.archive, title())
        return MatrixCursor(projection ?: SafeDocuments.DOCUMENT_PROJECTION).apply { addRowOf(row) }
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val session = requireLive().session
        val path = session.existingPath(parentDocumentId)
        if (!SafeDocuments.isDirectory(session.archive, path)) throw FileNotFoundException("not a directory")
        val cursor = MatrixCursor(projection ?: SafeDocuments.DOCUMENT_PROJECTION)
        session.archive.children(path).forEach { cursor.addRowOf(session.rowOf(it)) }
        val context = requireContext()
        cursor.setNotificationUri(
            context.contentResolver,
            DocumentsContract.buildChildDocumentsUri(safeAuthority(context), parentDocumentId),
        )
        return cursor
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val session = live()?.session ?: return false
        val parent = SafeDocumentIds.pathOf(session.documentToken, session.archive, parentDocumentId)
        val child = SafeDocumentIds.pathOf(session.documentToken, session.archive, documentId)
        return parent != null && child != null && SafeDocuments.isChild(session.archive, parent, child)
    }

    override fun findDocumentPath(parentDocumentId: String?, childDocumentId: String): DocumentsContract.Path {
        val session = requireLive().session
        val lineage = SafeDocuments.lineageOf(session.existingPath(childDocumentId))
        val from = parentDocumentId?.let { lineage.indexOf(session.existingPath(it)) } ?: 0
        if (from < 0) throw FileNotFoundException("not below the parent")
        val ids = lineage.drop(from).map { SafeDocumentIds.of(session.documentToken, session.archive, it) }
        return DocumentsContract.Path(SafeDocuments.ROOT_ID.takeIf { parentDocumentId == null }, ids)
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (mode != READ_ONLY) throw SecurityException("the vault is read-only on this phone")
        val live = requireLive()
        val path = live.session.existingPath(documentId)
        val entry = live.session.archive[path]?.takeUnless { it.isDirectory }
            ?: throw FileNotFoundException("not a file")
        val context = requireContext()
        val storage = context.getSystemService(StorageManager::class.java)
        val gate = SafeReadGate(
            live.session.documentToken,
            session = { OpenSafe.session.value },
            nowElapsed = SystemClock::elapsedRealtime,
            lockExpired = { OpenSafe.lockIfExpiredLater(context) },
        )
        if (!live.reads.acquire()) throw FileNotFoundException("the vault is locked")
        var handed = false
        try {
            return storage.openProxyFileDescriptor(
                ParcelFileDescriptor.MODE_READ_ONLY,
                EntryReads(live.session.content, entry, live.reads, gate) { OpenSafe.closeLater(context, live.reads) },
                live.reads.handler,
            ).also { handed = true }
        } catch (cause: IOException) {
            throw FileNotFoundException("no proxy descriptor (${cause::class.java.simpleName})")
        } finally {
            if (!handed) live.reads.release()
        }
    }

    private fun live(): LiveSafe? = OpenSafe.live(requireContext(), SystemClock.elapsedRealtime())

    private fun requireLive(): LiveSafe = live() ?: throw FileNotFoundException("the vault is locked")

    private fun title(): String = requireContext().getString(R.string.safe_root_title)

    private companion object {
        const val READ_ONLY = "r"
    }
}

// An id of an earlier unlock carries another token, so it is no document either.
private fun SafeSession.existingPath(documentId: String): String =
    SafeDocumentIds.pathOf(documentToken, archive, documentId) ?: throw FileNotFoundException("no such document")

private fun SafeSession.rowOf(entry: VaultEntry): Map<String, Any?> =
    SafeDocuments.documentRow(
        documentToken,
        archive,
        entry,
        SafeDocuments.mimeTypeOf(entry, MimeTypeMap.getSingleton()::getMimeTypeFromExtension),
    )

private fun MatrixCursor.addRowOf(row: Map<String, Any?>) {
    val builder = newRow()
    row.forEach { (column, value) -> builder.add(column, value) }
}

// Runs on the session's reading thread only, the same thread that closes the content. A descriptor
// opened before the limit stops reading at it, as the gate asks for the lock. A segment that no longer
// verifies means the private copy changed under the session: that read fails at once, and the lock
// follows on the main thread.
private class EntryReads(
    private val content: VaultReader,
    private val entry: VaultEntry,
    private val reads: SafeReads,
    private val gate: SafeReadGate,
    private val onTampered: () -> Unit,
) : ProxyFileDescriptorCallback() {

    override fun onGetSize(): Long {
        if (content.closed || !gate.open()) throw ErrnoException("onGetSize", OsConstants.EIO)
        return entry.size
    }

    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
        if (content.closed || !gate.open()) throw ErrnoException("onRead", OsConstants.EIO)
        val count = try {
            content.readEntry(entry, offset, size, data)
        } catch (_: VaultException) {
            Log.w(TAG, "A segment of the vault's copy failed to verify during a read; locking the vault")
            onTampered()
            throw ErrnoException("onRead", OsConstants.EIO)
        } catch (_: IOException) {
            throw ErrnoException("onRead", OsConstants.EIO)
        }
        if (count < 0) throw ErrnoException("onRead", OsConstants.EINVAL)
        return count
    }

    override fun onRelease() = reads.release()

    private companion object {
        const val TAG = "CueSafe"
    }
}
