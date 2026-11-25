package app.cue.launcher.engine.safe

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException

// The caller's own limit on the entries it lists, none of the format's categories.
class VaultLimitReached(val entryLimit: Int) : Exception("more than $entryLimit entries")

// offset and size are in the plaintext archive: where the content starts and how long it is.
data class VaultEntry(
    val path: String,
    val isDirectory: Boolean,
    val mtime: Long?,
    val offset: Long,
    val size: Long,
) {
    val name: String get() = path.removeSuffix("/").substringAfterLast('/')
}

// Paths are the archive's own, a directory's ending in "/", and the root is ROOT. A directory that only
// shows in the paths below it has no entry in the archive: it is listed as implied, with no mtime.
// No path is normalized: decoded from strict UTF-8, equal strings are equal bytes, so an NFC path and
// its NFD twin are two entries, as the spec has it.
class VaultArchive private constructor(
    val entries: List<VaultEntry>,
    private val byPath: Map<String, VaultEntry>,
    private val byParent: Map<String, List<VaultEntry>>,
) {

    // A number for every path that stands for it without naming it: the root is 0, the rest count up
    // in the archive's order, implied directories included.
    private val paths: List<String> = listOf(ROOT) + byPath.keys

    private val numbers: Map<String, Int> = paths.withIndex().associate { (number, path) -> path to number }

    operator fun get(path: String): VaultEntry? = byPath[path]

    fun children(directory: String): List<VaultEntry> = byParent[directory].orEmpty()

    fun numberOf(path: String): Int? = numbers[path]

    fun pathOf(number: Int): String? = paths.getOrNull(number)

    companion object {

        const val ROOT = ""

        // The implied directories count against the limit too.
        internal fun of(entries: List<VaultEntry>, entryLimit: Int = Int.MAX_VALUE): VaultArchive {
            val byPath = LinkedHashMap<String, VaultEntry>()
            entries.forEach { byPath[it.path] = it }
            for (entry in entries) {
                var parent = parentOf(entry.path)
                while (parent != ROOT && parent !in byPath) {
                    if (byPath.size >= entryLimit) throw VaultLimitReached(entryLimit)
                    byPath[parent] = VaultEntry(parent, isDirectory = true, mtime = null, offset = 0, size = 0)
                    parent = parentOf(parent)
                }
            }
            return VaultArchive(entries, byPath, byPath.values.groupBy { parentOf(it.path) })
        }
    }
}

private const val BLOCK = 512
private const val NAME_SIZE = 100
private const val SIZE_AT = 124
private const val MTIME_AT = 136
private const val CHKSUM_AT = 148
private const val CHKSUM_SIZE = 8
private const val CHKSUM_DIGITS = 6
private const val TYPEFLAG_AT = 156
private const val MAGIC_AT = 257
private const val PREFIX_AT = 345
private const val PREFIX_SIZE = 155
private const val OCTAL_DIGITS = 11
private const val OCTAL_BASE = 8
private const val SPACE = 0x20
private const val DIGIT_ZERO: Byte = 0x30
private const val TYPE_FILE: Byte = 0x30
private const val TYPE_DIR: Byte = 0x35
private const val SLASH: Byte = 0x2F
private val MAGIC_AND_VERSION = "ustar\u000000".encodeToByteArray()
private val CHKSUM_END = byteArrayOf(0, SPACE.toByte())
private val NUL_END = byteArrayOf(0)

private fun parentOf(path: String): String = path.substring(0, path.dropLast(1).lastIndexOf('/') + 1)

private fun ByteArray.checksum(): Long = indices.sumOf {
    if (it - CHKSUM_AT in 0 until CHKSUM_SIZE) SPACE.toLong() else this[it].toUByte().toLong()
}

private fun ByteArray.fieldLength(offset: Int, length: Int): Int =
    (0 until length).firstOrNull { this[offset + it] == 0.toByte() } ?: length

private fun pathFollowsRules(path: String, isDirectory: Boolean): Boolean {
    if (path.endsWith('/') != isDirectory || path.startsWith('/')) return false
    return (if (isDirectory) path.dropLast(1) else path).split('/').none { it.isEmpty() || it == "." || it == ".." }
}

// Checks the archive of the given size against the profile as its plaintext arrives, in pieces of
// whole blocks — segments are whole blocks, and an archive of another size is refused up front — and
// keeps only the entries. The first breach is held back until finish(), so that a damaged segment the
// caller meets later still decides the category.
internal class ArchiveIndexer(private val size: Long, private val entryLimit: Int = Int.MAX_VALUE) {

    private val block = ByteArray(BLOCK)
    private val utf8 = Charsets.UTF_8.newDecoder()
    private val isDirectoryByKey = HashMap<String, Boolean>()
    private val entries = ArrayList<VaultEntry>()
    private var at = 0L
    private var nextHeader = 0L
    private var zeroBlocks = 0
    private var breach: VaultException? = null

    init {
        guarded {
            if (size % BLOCK != 0L || size < 2 * BLOCK) bad("an archive of $size bytes is no whole number of blocks")
        }
    }

    fun feed(plain: ByteArray, length: Int) = guarded {
        var offset = 0
        while (offset < length) {
            val content = minOf(nextHeader - at, (length - offset).toLong()).toInt()
            if (content > 0) {
                at += content
                offset += content
            } else {
                plain.copyInto(block, 0, offset, offset + BLOCK)
                header()
                at += BLOCK
                offset += BLOCK
            }
        }
    }

    fun finish(): VaultArchive {
        breach?.let { throw it }
        if (zeroBlocks < 2 || at != size) bad("the archive ends without its two zero blocks")
        isDirectoryByKey.keys.forEach(::checkNotInsideFile)
        return VaultArchive.of(entries, entryLimit)
    }

    private inline fun guarded(step: () -> Unit) {
        if (breach != null) return
        try {
            step()
        } catch (failure: VaultException) {
            breach = failure
        }
    }

    // A block where a header is due: an entry, or the first or second of the two zero blocks that end
    // the archive.
    private fun header() {
        val zero = block.all { it == 0.toByte() }
        when {
            zeroBlocks == 1 -> {
                if (!zero) bad("the archive does not end in exactly two zero blocks")
                zeroBlocks = 2
            }
            zero -> {
                if (at + 2 * BLOCK != size) bad("the archive does not end in exactly two zero blocks")
                zeroBlocks = 1
                nextHeader = at + BLOCK
            }
            else -> {
                if (entries.size >= entryLimit) throw VaultLimitReached(entryLimit)
                entries += entry()
            }
        }
    }

    private fun entry(): VaultEntry {
        if (MAGIC_AND_VERSION.indices.any { block[MAGIC_AT + it] != MAGIC_AND_VERSION[it] }) bad("not a ustar header")
        if (octal(CHKSUM_AT, CHKSUM_DIGITS, CHKSUM_END) != block.checksum()) bad("the checksum does not match")
        val type = block[TYPEFLAG_AT]
        if (type != TYPE_FILE && type != TYPE_DIR) bad("entry type $type is not a file or a directory")
        val isDirectory = type == TYPE_DIR
        val contentSize = octal(SIZE_AT, OCTAL_DIGITS, NUL_END)
        val mtime = octal(MTIME_AT, OCTAL_DIGITS, NUL_END)
        if (isDirectory && contentSize != 0L) bad("a directory has a size")
        val path = path(isDirectory)
        val start = at + BLOCK
        val end = start + (contentSize + BLOCK - 1) / BLOCK * BLOCK
        if (end > size) bad("$path runs past the end of the archive")
        nextHeader = end
        return VaultEntry(path, isDirectory, mtime, start, contentSize)
    }

    private fun octal(offset: Int, digits: Int, terminator: ByteArray): Long {
        var value = 0L
        for (i in offset until offset + digits) {
            val digit = block[i] - DIGIT_ZERO
            if (digit !in 0 until OCTAL_BASE) bad("a numeric field is not $digits octal digits")
            value = value * OCTAL_BASE + digit
        }
        if (terminator.indices.any { block[offset + digits + it] != terminator[it] }) {
            bad("a numeric field is not terminated as the profile requires")
        }
        return value
    }

    private fun path(isDirectory: Boolean): String {
        val name = block.fieldLength(0, NAME_SIZE)
        if (name == 0) bad("the name is empty")
        val prefix = block.fieldLength(PREFIX_AT, PREFIX_SIZE)
        val raw = if (prefix == 0) {
            block.copyOf(name)
        } else {
            block.copyOfRange(PREFIX_AT, PREFIX_AT + prefix) + SLASH + block.copyOf(name)
        }
        val path = try {
            utf8.decode(ByteBuffer.wrap(raw)).toString()
        } catch (_: CharacterCodingException) {
            bad("the path is not UTF-8")
        }
        if (!pathFollowsRules(path, isDirectory)) bad("$path breaks the path rules")
        if (isDirectoryByKey.put(path.removeSuffix("/"), isDirectory) != null) bad("$path occurs twice")
        return path
    }

    private fun checkNotInsideFile(key: String) {
        var slash = key.indexOf('/')
        while (slash >= 0) {
            if (isDirectoryByKey[key.substring(0, slash)] == false) bad("$key lies inside a file")
            slash = key.indexOf('/', slash + 1)
        }
    }

    private fun bad(message: String): Nothing =
        vaultFailure(VaultFailure.BAD_ARCHIVE, "archive header at byte $at: $message")
}
