package app.cue.launcher.system.safe

import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import app.cue.launcher.engine.safe.VaultArchive
import app.cue.launcher.engine.safe.VaultEntry
import app.cue.launcher.engine.safe.VaultReader
import java.security.SecureRandom

// Document ids are the session's token, a "/" and the number the session's index gives a path, never
// the path itself: the system's picker keeps ids with its recents, and a file's path would outlive the
// lock there. The token is random and new at every unlock, so an id, and with it a grant or a stored
// URI, from an earlier unlock names nothing in a later one. It never contains "/".
internal object SafeDocumentIds {

    private const val TOKEN_BYTES = 16

    private val random = SecureRandom()

    fun newToken(): String = ByteArray(TOKEN_BYTES).also(random::nextBytes).toHexString()

    fun of(token: String, archive: VaultArchive, path: String): String =
        "$token/${checkNotNull(archive.numberOf(path)) { "no path of this archive" }}"

    fun rootOf(token: String, archive: VaultArchive): String = of(token, archive, VaultArchive.ROOT)

    // Only the canonical decimal form of a number names a document, so each has exactly one id.
    fun pathOf(token: String, archive: VaultArchive, documentId: String): String? {
        if (documentId.length <= token.length || !documentId.startsWith(token) || documentId[token.length] != '/') {
            return null
        }
        val number = documentId.substring(token.length + 1)
        return number.toIntOrNull()?.takeIf { it.toString() == number }?.let(archive::pathOf)
    }
}

internal object SafeDocuments {

    const val ROOT_ID = "vault"

    private const val FALLBACK_MIME = "application/octet-stream"

    private const val SECOND_MS = 1000L

    val ROOT_PROJECTION = arrayOf(
        Root.COLUMN_ROOT_ID,
        Root.COLUMN_DOCUMENT_ID,
        Root.COLUMN_TITLE,
        Root.COLUMN_SUMMARY,
        Root.COLUMN_ICON,
        Root.COLUMN_FLAGS,
    )

    val DOCUMENT_PROJECTION = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE,
        Document.COLUMN_LAST_MODIFIED,
        Document.COLUMN_FLAGS,
    )

    fun extensionOf(name: String): String? =
        name.lastIndexOf('.').takeIf { it > 0 && it < name.length - 1 }?.let { name.substring(it + 1).lowercase() }

    fun mimeTypeOf(entry: VaultEntry, byExtension: (String) -> String?): String = when {
        entry.isDirectory -> Document.MIME_TYPE_DIR
        else -> extensionOf(entry.name)?.let(byExtension) ?: FALLBACK_MIME
    }

    fun isDirectory(archive: VaultArchive, path: String): Boolean =
        path == VaultArchive.ROOT || archive[path]?.isDirectory == true

    // The root and every directory down to path, then path itself.
    fun lineageOf(path: String): List<String> {
        if (path == VaultArchive.ROOT) return listOf(VaultArchive.ROOT)
        val lineage = mutableListOf(VaultArchive.ROOT)
        var slash = path.indexOf('/')
        while (slash in 0 until path.length - 1) {
            lineage += path.substring(0, slash + 1)
            slash = path.indexOf('/', slash + 1)
        }
        return lineage.apply { add(path) }
    }

    fun isChild(archive: VaultArchive, parentPath: String, childPath: String): Boolean =
        isDirectory(archive, parentPath) &&
            childPath != parentPath &&
            childPath.startsWith(parentPath) &&
            archive[childPath] != null

    // No create, write, delete, rename, thumbnail, recents or search: the vault is read-only on the
    // phone. Tree access is not offered either, so no app can hold the whole vault on one grant.
    fun rootRow(token: String, archive: VaultArchive, title: String, summary: String, icon: Int): Map<String, Any?> =
        mapOf(
            Root.COLUMN_ROOT_ID to ROOT_ID,
            Root.COLUMN_DOCUMENT_ID to SafeDocumentIds.rootOf(token, archive),
            Root.COLUMN_TITLE to title,
            Root.COLUMN_SUMMARY to summary,
            Root.COLUMN_ICON to icon,
            Root.COLUMN_FLAGS to Root.FLAG_LOCAL_ONLY,
        )

    fun rootDocumentRow(token: String, archive: VaultArchive, title: String): Map<String, Any?> = mapOf(
        Document.COLUMN_DOCUMENT_ID to SafeDocumentIds.rootOf(token, archive),
        Document.COLUMN_DISPLAY_NAME to title,
        Document.COLUMN_MIME_TYPE to Document.MIME_TYPE_DIR,
        Document.COLUMN_SIZE to null,
        Document.COLUMN_LAST_MODIFIED to null,
        Document.COLUMN_FLAGS to 0,
    )

    fun documentRow(token: String, archive: VaultArchive, entry: VaultEntry, mimeType: String): Map<String, Any?> =
        mapOf(
            Document.COLUMN_DOCUMENT_ID to SafeDocumentIds.of(token, archive, entry.path),
            Document.COLUMN_DISPLAY_NAME to entry.name,
            Document.COLUMN_MIME_TYPE to mimeType,
            Document.COLUMN_SIZE to entry.size.takeUnless { entry.isDirectory },
            Document.COLUMN_LAST_MODIFIED to entry.mtime?.let { it * SECOND_MS },
            Document.COLUMN_FLAGS to 0,
        )

    // How many bytes a read of a file of fileSize bytes returns, or -1 when the request is invalid.
    fun readableBytes(fileSize: Long, offset: Long, requested: Int): Int = when {
        offset < 0 || requested < 0 -> -1
        offset >= fileSize -> 0
        else -> minOf(requested.toLong(), fileSize - offset).toInt()
    }
}

// One read of a file into data, checked before the content is touched: the bytes read, 0 at or past
// the end of the file, -1 when the request is invalid.
internal fun VaultReader.readEntry(entry: VaultEntry, offset: Long, size: Int, data: ByteArray): Int {
    val count = SafeDocuments.readableBytes(entry.size, offset, minOf(size, data.size))
    if (count > 0) read(entry.offset + offset, data, 0, count)
    return count
}
