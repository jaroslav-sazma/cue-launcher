package app.cue.launcher.system.safe

import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import app.cue.launcher.engine.safe.Credential
import app.cue.launcher.engine.safe.VaultArchive
import app.cue.launcher.engine.safe.VaultEntry
import app.cue.launcher.engine.safe.VaultFixtures
import app.cue.launcher.engine.safe.openVault
import app.cue.launcher.engine.safe.str
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SafeDocumentsTest {

    private val opened by lazy {
        val password = VaultFixtures.positive("few-files").getValue("inputs").jsonObject.str("password")
        openVault(VaultFixtures.bytes("few-files.cuevault"), Credential.Password(password))
    }

    private val archive: VaultArchive get() = opened.index

    private val mimeTypes = mapOf("pdf" to "application/pdf", "txt" to "text/plain")

    private val token = SafeDocumentIds.newToken()

    @Test
    fun `a document id and its archive path map onto each other within one unlock, the root included`() {
        val paths = listOf(VaultArchive.ROOT) + archive.entries.map { it.path }
        for (path in paths) {
            val id = SafeDocumentIds.of(token, archive, path)
            assertEquals(path, SafeDocumentIds.pathOf(token, archive, id))
        }
        assertEquals(paths.size, paths.map { SafeDocumentIds.of(token, archive, it) }.toSet().size)
        assertEquals("$token/0", SafeDocumentIds.rootOf(token, archive))
    }

    @Test
    fun `a directory that only shows in the paths below it gets an id too`() {
        val file = VaultEntry("A/B/c.txt", isDirectory = false, mtime = 0, offset = 512, size = 1)
        val implied = VaultArchive.of(listOf(file))

        for (path in listOf(VaultArchive.ROOT, "A/", "A/B/", "A/B/c.txt")) {
            assertEquals(path, SafeDocumentIds.pathOf(token, implied, SafeDocumentIds.of(token, implied, path)))
        }
    }

    @Test
    fun `a document id names no part of the path, only an opaque number`() {
        for (entry in archive.entries) {
            val id = SafeDocumentIds.of(token, archive, entry.path)
            val number = id.substringAfter("$token/")
            assertTrue(number.isNotEmpty() && number.all(Char::isDigit)) { "$id is not the token and a number" }
            assertFalse(entry.name in id) { "$id names ${entry.name}" }
        }
    }

    @Test
    fun `an id that is no number of this unlock's index is no document`() {
        val beyond = archive.entries.size + 1_000
        for (suffix in listOf("Smlouvy/", "", "-1", "+1", "01", "00", " 1", "1 ", "$beyond", "99999999999")) {
            assertNull(SafeDocumentIds.pathOf(token, archive, "$token/$suffix")) { "suffix \"$suffix\"" }
        }
        assertNull(SafeDocumentIds.pathOf(token, archive, "1"))
        assertNull(SafeDocumentIds.pathOf(token, archive, token))
        assertNull(SafeDocumentIds.pathOf(token, archive, "/1"))
        assertNull(SafeDocumentIds.pathOf(token, archive, "${token.dropLast(1)}/1"))
        assertNull(SafeDocumentIds.pathOf(token, archive, "${token}0/1"))
    }

    @Test
    fun `an id or a grant from an earlier unlock names nothing in a later one`() {
        val earlier = SafeDocumentIds.newToken()
        val later = SafeDocumentIds.newToken()

        assertTrue(earlier != later)
        for (path in listOf(VaultArchive.ROOT, "Smlouvy/nájemní smlouva.pdf")) {
            assertNull(SafeDocumentIds.pathOf(later, archive, SafeDocumentIds.of(earlier, archive, path)))
        }
    }

    @Test
    fun `a token is random, 128 bits in hex, and owes nothing to the vault`() {
        val tokens = List(64) { SafeDocumentIds.newToken() }

        assertEquals(tokens.size, tokens.toSet().size)
        assertTrue(tokens.all { it.length == 32 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } })
    }

    @Test
    fun `a document's path runs from the root through every directory above it`() {
        assertEquals(listOf(VaultArchive.ROOT), SafeDocuments.lineageOf(VaultArchive.ROOT))
        assertEquals(listOf(VaultArchive.ROOT, "čtěte mě.txt"), SafeDocuments.lineageOf("čtěte mě.txt"))
        assertEquals(
            listOf(VaultArchive.ROOT, "Smlouvy/", "Smlouvy/nájemní smlouva.pdf"),
            SafeDocuments.lineageOf("Smlouvy/nájemní smlouva.pdf"),
        )
        assertEquals(listOf(VaultArchive.ROOT, "A/", "A/B/"), SafeDocuments.lineageOf("A/B/"))
    }

    @Test
    fun `the extension decides the MIME type, a directory is a directory and the rest is octet-stream`() {
        val entries = archive.entries.associateBy { it.path }

        assertEquals(
            "application/pdf",
            SafeDocuments.mimeTypeOf(entries.getValue("Smlouvy/nájemní smlouva.pdf"), mimeTypes::get),
        )
        assertEquals(Document.MIME_TYPE_DIR, SafeDocuments.mimeTypeOf(entries.getValue("Smlouvy/"), mimeTypes::get))
        assertEquals(
            "application/octet-stream",
            SafeDocuments.mimeTypeOf(entries.getValue("Certifikáty/osobní.p12"), mimeTypes::get),
        )
    }

    @Test
    fun `an extension is the lower-cased part after the last dot of a name that does not start with it`() {
        assertEquals("pdf", SafeDocuments.extensionOf("Smlouva.PDF"))
        assertEquals("gz", SafeDocuments.extensionOf("archive.tar.gz"))
        assertNull(SafeDocuments.extensionOf(".profile"))
        assertNull(SafeDocuments.extensionOf("README"))
        assertNull(SafeDocuments.extensionOf("trailing."))
    }

    @Test
    fun `a child lies below its parent directory and exists`() {
        assertTrue(SafeDocuments.isChild(archive, VaultArchive.ROOT, "Smlouvy/nájemní smlouva.pdf"))
        assertTrue(SafeDocuments.isChild(archive, "Smlouvy/", "Smlouvy/prázdný.txt"))
        assertFalse(SafeDocuments.isChild(archive, "Smlouvy/", "Smlouvy/"))
        assertFalse(SafeDocuments.isChild(archive, "Smlouvy/", "Archiv/"))
        assertFalse(SafeDocuments.isChild(archive, "Smlouvy/", "Smlouvy/missing.txt"))
        assertFalse(SafeDocuments.isChild(archive, "čtěte mě.txt", "čtěte mě.txt/x"))
    }

    @Test
    fun `a file row carries its size and mtime in milliseconds, a directory row no size`() {
        val file = archive["čtěte mě.txt"]!!
        val row = SafeDocuments.documentRow(token, archive, file, "text/plain")

        assertEquals(SafeDocumentIds.of(token, archive, "čtěte mě.txt"), row[Document.COLUMN_DOCUMENT_ID])
        assertEquals("čtěte mě.txt", row[Document.COLUMN_DISPLAY_NAME])
        assertEquals(file.size, row[Document.COLUMN_SIZE])
        assertEquals(1_772_323_200_000L, row[Document.COLUMN_LAST_MODIFIED])
        assertEquals(0, row[Document.COLUMN_FLAGS])

        val directory = SafeDocuments.documentRow(token, archive, archive["Archiv/"]!!, Document.MIME_TYPE_DIR)
        assertEquals("Archiv", directory[Document.COLUMN_DISPLAY_NAME])
        assertNull(directory[Document.COLUMN_SIZE])
    }

    @Test
    fun `the root offers nothing but local, read-only browsing`() {
        val row = SafeDocuments.rootRow(token, archive, "Vault", "vault.cuevault", icon = 1)
        val document = SafeDocuments.rootDocumentRow(token, archive, "Vault")

        assertEquals(Root.FLAG_LOCAL_ONLY, row[Root.COLUMN_FLAGS])
        assertEquals(SafeDocumentIds.rootOf(token, archive), row[Root.COLUMN_DOCUMENT_ID])
        assertEquals(SafeDocumentIds.rootOf(token, archive), document[Document.COLUMN_DOCUMENT_ID])
        assertEquals(SafeDocuments.ROOT_PROJECTION.toSet(), row.keys)
        assertEquals(SafeDocuments.DOCUMENT_PROJECTION.toSet(), document.keys)
    }

    @Test
    fun `the children of a directory are what the archive lists under it`() {
        assertEquals(
            setOf("Archiv/", "Certifikáty/", "Smlouvy/", "čtěte mě.txt"),
            archive.children(VaultArchive.ROOT).map { it.path }.toSet(),
        )
        assertTrue(SafeDocuments.isDirectory(archive, VaultArchive.ROOT))
        assertFalse(SafeDocuments.isDirectory(archive, "čtěte mě.txt"))
    }

    @Test
    fun `a read never runs past the end of the file and refuses a negative offset`() {
        assertEquals(10, SafeDocuments.readableBytes(fileSize = 100, offset = 0, requested = 10))
        assertEquals(5, SafeDocuments.readableBytes(fileSize = 100, offset = 95, requested = 10))
        assertEquals(0, SafeDocuments.readableBytes(fileSize = 100, offset = 100, requested = 10))
        assertEquals(0, SafeDocuments.readableBytes(fileSize = 0, offset = 0, requested = 10))
        assertEquals(0, SafeDocuments.readableBytes(fileSize = 100, offset = Long.MAX_VALUE, requested = 10))
        assertEquals(10, SafeDocuments.readableBytes(fileSize = 1L shl 40, offset = (1L shl 40) - 10, requested = 4096))
        assertEquals(-1, SafeDocuments.readableBytes(fileSize = 100, offset = -1, requested = 10))
        assertEquals(-1, SafeDocuments.readableBytes(fileSize = 100, offset = 0, requested = -1))
    }

    // The last file in the archive, so that a position past its end lies past the archive's end too.
    @Test
    fun `a read at or past the end of a file returns nothing and leaves the buffer alone`() {
        val entry = archive.entries.filterNot { it.isDirectory }.maxBy { it.offset }
        val data = ByteArray(64) { 7 }

        for (offset in listOf(entry.size, entry.size + 1, opened.reader.plaintextSize, Long.MAX_VALUE)) {
            assertEquals(0, opened.reader.readEntry(entry, offset, data.size, data), "at $offset")
        }
        assertEquals(0, opened.reader.readEntry(entry, offset = 0, size = 0, data))
        assertEquals(-1, opened.reader.readEntry(entry, offset = -1, size = data.size, data))
        assertEquals(-1, opened.reader.readEntry(entry, offset = Long.MIN_VALUE, size = data.size, data))
        assertEquals(-1, opened.reader.readEntry(entry, offset = 0, size = -1, data))
        assertTrue(data.all { it == 7.toByte() })
    }

    @Test
    fun `a read that runs into the end of a file returns what is left of it`() {
        val entry = archive["čtěte mě.txt"]!!
        val content = opened.content(entry)
        val data = ByteArray(4096)

        assertEquals(3, opened.reader.readEntry(entry, entry.size - 3, data.size, data))
        assertArrayEquals(content.copyOfRange(content.size - 3, content.size), data.copyOf(3))
        assertEquals(content.size, opened.reader.readEntry(entry, 0, Int.MAX_VALUE, data))
        assertArrayEquals(content, data.copyOf(content.size))
    }

    @Test
    fun `an empty file reads as its end at once`() {
        val entry = archive["Smlouvy/prázdný.txt"]!!

        assertEquals(0L, entry.size)
        assertEquals(0, opened.reader.readEntry(entry, 0, 64, ByteArray(64)))
    }
}
