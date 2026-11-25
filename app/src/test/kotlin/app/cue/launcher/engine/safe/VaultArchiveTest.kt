package app.cue.launcher.engine.safe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class VaultArchiveTest {

    private fun header(
        path: String,
        type: Char = '0',
        size: Int = 0,
        prefix: String = "",
        edit: (ByteArray) -> Unit = {},
    ): ByteArray {
        val block = ByteArray(512)
        fun put(at: Int, text: String) = text.encodeToByteArray().copyInto(block, at)
        put(0, path)
        put(100, if (type == '5') "0000755\u0000" else "0000644\u0000")
        put(108, "0000000\u0000")
        put(116, "0000000\u0000")
        put(124, "%011o\u0000".format(size))
        put(136, "%011o\u0000".format(1_767_225_600))
        block[156] = type.code.toByte()
        put(257, "ustar\u000000")
        put(329, "0000000\u0000")
        put(337, "0000000\u0000")
        put(345, prefix)
        edit(block)
        put(148, " ".repeat(8))
        put(148, "%06o\u0000 ".format(block.sumOf { it.toInt() and 0xFF }))
        return block
    }

    private fun content(text: String): ByteArray = text.encodeToByteArray().copyOf((text.length + 511) / 512 * 512)

    private fun archive(vararg parts: ByteArray): ByteArray =
        parts.fold(ByteArray(0), ByteArray::plus) + ByteArray(1024)

    // Fed in pieces of the given number of blocks, as segments arrive.
    private fun index(bytes: ByteArray, blocks: Int = 128, entryLimit: Int = Int.MAX_VALUE): VaultArchive {
        val indexer = ArchiveIndexer(bytes.size.toLong(), entryLimit)
        if (bytes.size % 512 == 0) {
            for (at in bytes.indices step blocks * 512) {
                val piece = bytes.copyOfRange(at, minOf(bytes.size, at + blocks * 512))
                indexer.feed(piece, piece.size)
            }
        }
        return indexer.finish()
    }

    @Test
    fun `an empty archive lists nothing`() {
        val archive = index(ByteArray(1024))

        assertEquals(emptyList<VaultEntry>(), archive.entries)
        assertEquals(emptyList<VaultEntry>(), archive.children(VaultArchive.ROOT))
    }

    @Test
    fun `parent directories without an entry of their own are implied`() {
        val archive = index(archive(header("a/b/c.txt", size = 3), content("abc"), header("a/d/", '5')))
        val file = VaultEntry("a/b/c.txt", isDirectory = false, mtime = 1_767_225_600, offset = 512, size = 3)
        val implied = VaultEntry("a/", isDirectory = true, mtime = null, offset = 0, size = 0)

        assertEquals(file, archive["a/b/c.txt"])
        assertEquals("c.txt", file.name)
        assertEquals(implied, archive["a/"])
        assertEquals("a", implied.name)
        assertEquals(listOf(implied), archive.children(VaultArchive.ROOT))
        assertEquals(listOf("a/b/", "a/d/"), archive.children("a/").map { it.path }.sorted())
        assertEquals(listOf(file), archive.children("a/b/"))
        assertEquals(emptyList<VaultEntry>(), archive.children("a/d/"))
        assertNull(archive["a/b"])
        assertNull(archive["a"])
    }

    @Test
    fun `prefix and name are joined with a slash`() {
        val archive = index(archive(header("c.txt", prefix = "a/b", size = 1), content("x")))

        assertEquals(listOf("a/b/c.txt"), archive.entries.map { it.path })
    }

    @Test
    fun `fields the profile does not check are ignored`() {
        val tolerated = header("x.txt", size = 2) {
            "garbage!".encodeToByteArray().copyInto(it, 100)
            "linkname".encodeToByteArray().copyInto(it, 157)
            "user".encodeToByteArray().copyInto(it, 265)
            it[7] = 'y'.code.toByte()
            it[505] = 1
        }
        val padding = content("hi").also { it[300] = 9 }
        val archive = index(archive(tolerated, padding))

        assertEquals(listOf("x.txt"), archive.entries.map { it.path })
        assertEquals(2L, archive["x.txt"]?.size)
    }

    @Test
    fun `paths keep their normalization and compare by their bytes`() {
        val caron = Char(0x30C)
        val nfc = Char(0x17E).toString()
        val nfd = "z$caron"
        val nfdFile = "c${caron}te${caron}te.txt"
        val archive = index(archive(header(nfc), header("$nfd/", '5'), header("$nfd/a.txt"), header(nfdFile)))

        assertEquals(listOf(nfc, "$nfd/", "$nfd/a.txt", nfdFile), archive.entries.map { it.path })
        assertEquals(false, archive[nfc]?.isDirectory)
        assertEquals(listOf("$nfd/a.txt"), archive.children("$nfd/").map { it.path })
    }

    @TestFactory
    fun `an archive that breaks the profile is refused`(): List<DynamicTest> = mapOf(
        "a symbolic link" to archive(header("x", '2')),
        "a pax header" to archive(header("x", 'x')),
        "the old NUL typeflag" to archive(header("x", '\u0000')),
        "a directory with a size" to archive(header("d/", '5', size = 1), content("x")),
        "a directory path without its slash" to archive(header("d", '5')),
        "a file path with a slash" to archive(header("d/")),
        "an absolute path" to archive(header("/x")),
        "a dot segment" to archive(header("a/./b")),
        "a dot-dot segment" to archive(header("a/../b")),
        "an empty segment" to archive(header("a//b")),
        "an empty name with a prefix" to archive(header("", prefix = "a")),
        "a path that is not UTF-8" to archive(header("xx") { it[0] = 0xC3.toByte(); it[1] = 0x28 }),
        "a file and a directory of the same path" to archive(header("a"), header("a/", '5')),
        "the same file twice" to archive(header("a"), header("a")),
        "an entry inside a file" to archive(header("a"), header("a/b")),
        "a wrong checksum" to archive(header("x").also { it[0] = 'y'.code.toByte() }),
        "a checksum without its NUL and space" to archive(header("x").also { it[155] = 0 }),
        "a size that is not octal" to archive(header("x") { it[134] = '8'.code.toByte() }),
        "a size without its NUL" to archive(header("x") { it[135] = ' '.code.toByte() }),
        "an mtime of 12 digits" to archive(header("x") { it[147] = '0'.code.toByte() }),
        "a GNU magic" to archive(header("x") { "ustar  \u0000".encodeToByteArray().copyInto(it, 257) }),
        "content past the end" to archive(header("x", size = 2000)),
        "a single zero block" to header("x") + ByteArray(512),
        "three zero blocks" to archive(header("x")) + ByteArray(512),
        "an entry after the end" to archive(header("x")) + header("y") + ByteArray(1024),
        "a zero block followed by data" to header("x") + ByteArray(512) + header("y"),
        "no end marker" to header("x") + header("y"),
        "a length that is no whole number of blocks" to archive(header("x")) + ByteArray(1),
        "less than two blocks" to ByteArray(512),
        "nothing" to ByteArray(0),
    ).flatMap { (name, bytes) ->
        listOf(1, 128).map { blocks ->
            DynamicTest.dynamicTest("$name, fed $blocks blocks at a time") {
                assertFailure(VaultFailure.BAD_ARCHIVE) { index(bytes, blocks) }
            }
        }
    }

    @Test
    fun `an entry points at its content in the archive, however the archive was cut`() {
        val bytes = archive(header("x", size = 1), content("z"), header("y", size = 1000), content("w".repeat(1000)))
        for (blocks in listOf(1, 2, 128)) {
            val archive = index(bytes, blocks)
            val x = archive["x"]!!
            val y = archive["y"]!!

            assertEquals(512L, x.offset)
            assertEquals('z'.code.toByte(), bytes[x.offset.toInt()])
            assertEquals(1536L, y.offset)
            assertEquals(1000L, y.size)
        }
    }

    @Test
    fun `more entries than the caller's limit end the pass at once, implied directories included`() {
        val three = archive(header("a"), header("b"), header("c"))
        assertEquals(3, index(three, entryLimit = 3).entries.size)
        val indexer = ArchiveIndexer(three.size.toLong(), entryLimit = 2)
        assertEquals(2, assertThrows(VaultLimitReached::class.java) { indexer.feed(three, three.size) }.entryLimit)

        val deep = archive(header("x/y/z.txt"))
        assertEquals(1, index(deep, entryLimit = 3).entries.size)
        assertThrows(VaultLimitReached::class.java) { index(deep, entryLimit = 2) }
    }

    @Test
    fun `a breach is held back until the end, while the pieces keep coming`() {
        val bytes = archive(header("x", '2'), header("y"))
        val indexer = ArchiveIndexer(bytes.size.toLong())

        indexer.feed(bytes, bytes.size)
        assertFailure(VaultFailure.BAD_ARCHIVE) { indexer.finish() }
    }
}
