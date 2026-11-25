package app.cue.launcher.engine.safe

import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class VaultReaderTest {

    private val multi = VaultFixtures.bytes("multi-segment.cuevault")
    private val multiInputs = VaultFixtures.positive("multi-segment").getValue("inputs").jsonObject
    private val multiKey = Credential.Stored(multiInputs.hex("key_id"), multiInputs.hex("cek"))
    private val multiHeader = VaultHeader.read(ByteArrayInputStream(multi))

    private val largestPayload = MAX_SEGMENTS * SEALED_SEGMENT_SIZE

    private fun reader(file: ByteArray, source: VaultSource = ByteArraySource(file)) =
        VaultReader(VaultLayout.of(multiHeader, file.size.toLong()), source, multiKey.cek)

    // Seals an archive under the multi-segment vault's header and CEK, as a writer does.
    private fun seal(archive: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val segments = archive.toList().chunked(VAULT_SEGMENT_SIZE).map { it.toByteArray() }
        return segments.foldIndexed(multi.copyOf(VAULT_HEADER_SIZE)) { index, file, plain ->
            val nonce = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).put(multiHeader.noncePrefix)
                .putInt(index).put(if (index == segments.lastIndex) 1 else 0).array()
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(multiKey.cek, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(file, 0, VAULT_HEADER_SIZE)
            file + cipher.doFinal(plain)
        }
    }

    @Test
    fun `the largest archive the segment index allows is sized from the length alone`() {
        val layout = VaultLayout.of(multiHeader, VAULT_HEADER_SIZE + largestPayload)

        assertEquals(1L shl 32, layout.segments)
        assertEquals(1L shl 48, layout.plaintextSize)
        assertEquals(VAULT_SEGMENT_SIZE, layout.plaintextLength(layout.segments - 1))
    }

    @Test
    fun `a length of more than 2^32 segments is no layout`() {
        for (length in listOf(
            VAULT_HEADER_SIZE + largestPayload + MIN_SEALED_SEGMENT_SIZE,
            VAULT_HEADER_SIZE + largestPayload + SEALED_SEGMENT_SIZE,
            Long.MAX_VALUE,
        )) {
            assertFailure(VaultFailure.BAD_HEADER) { VaultLayout.of(multiHeader, length) }
        }
    }

    @Test
    fun `a length with no segment layout is refused before any crypto`() {
        for (payload in listOf(0L, 1L, 16L, SEALED_SEGMENT_SIZE + 16L, 2L * SEALED_SEGMENT_SIZE + 1)) {
            assertFailure(VaultFailure.BAD_HEADER) { VaultLayout.of(multiHeader, VAULT_HEADER_SIZE + payload) }
        }
    }

    @TestFactory
    fun `every file of every vector reads the same at any offset and in any piece`(): List<DynamicTest> {
        val positive = VaultFixtures.vectors.objects("positive").map { it to it.getValue("inputs").jsonObject }
        val accepted = VaultFixtures.vectors.objects("accepted").map { it to it.getValue("mutation").jsonObject }
        return (positive + accepted).map { (case, entries) ->
            DynamicTest.dynamicTest(case.str("name")) {
                val credential = credentialOf(case["credential"]?.jsonObject ?: case.getValue("inputs").jsonObject)
                val file = VaultFixtures.bytes(case.getValue("expected").jsonObject.str("file"))
                val opened = openVault(file, credential)
                for (spec in entries.objects("entries").filter { it.str("type") == "file" }) {
                    val path = spec.str("path")
                    val content = contentOf(spec["content"]?.jsonObject)
                    val entry = opened.index[path] ?: opened.index.entries.single { it.path == path.nfc() }
                    assertEquals(content.size.toLong(), entry.size, path)
                    for ((offset, size) in ranges(entry)) {
                        val read = ByteArray(size)
                        opened.reader.read(entry.offset + offset, read, 0, size)
                        assertArrayEquals(content.copyOfRange(offset, offset + size), read, "$path: $size at $offset")
                    }
                }
            }
        }
    }

    @Test
    fun `written_at is unsigned, and a time too large for milliseconds is capped`() {
        fun writtenAt(value: Long): Long {
            val head = multi.copyOf(VAULT_HEADER_SIZE)
            ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN).putLong(61, value)
            return VaultHeader.read(ByteArrayInputStream(head)).writtenAt
        }

        assertEquals(1_772_323_200L, writtenAt(1_772_323_200L))
        assertEquals(Long.MAX_VALUE / 1_000, writtenAt(Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE / 1_000, writtenAt(Long.MIN_VALUE))
        assertEquals(Long.MAX_VALUE / 1_000, writtenAt(-1L))
        assertEquals(Long.MAX_VALUE / 1_000, writtenAt(Long.MAX_VALUE / 1_000))
        assertTrue(writtenAt(-1L) * 1_000 > 0)
    }

    @Test
    fun `a read that spans every segment returns the archive the vector names`() {
        val opened = openVault(multi, multiKey)
        val archive = ByteArray(opened.reader.plaintextSize.toInt())
        opened.reader.read(0, archive, 0, archive.size)

        assertArrayEquals(opened.archive(piece = 512), archive)
        assertArrayEquals(opened.archive(piece = VAULT_SEGMENT_SIZE + 7), archive)
    }

    @Test
    fun `a read lands at the offset it is given in the buffer`() {
        val opened = openVault(multi, multiKey)
        val buffer = ByteArray(40) { 7 }
        opened.reader.read(VAULT_SEGMENT_SIZE - 10L, buffer, 5, 20)

        val expected = opened.archive().copyOfRange(VAULT_SEGMENT_SIZE - 10, VAULT_SEGMENT_SIZE + 10)
        assertArrayEquals(expected, buffer.copyOfRange(5, 25))
        assertTrue(buffer.take(5).all { it == 7.toByte() } && buffer.drop(25).all { it == 7.toByte() })
    }

    @Test
    fun `the index pass verifies every segment once and keeps none of them`() {
        val source = ByteArraySource(multi)
        var segments = 0
        reader(multi, source).index { segments++ }

        assertEquals(3, segments)
        assertEquals(3, source.reads)
    }

    @Test
    fun `reading straight through decrypts each segment once`() {
        val source = ByteArraySource(multi)
        val reader = reader(multi, source)
        val piece = ByteArray(4093)
        var at = 0L
        while (at < reader.plaintextSize) {
            val size = minOf(piece.size.toLong(), reader.plaintextSize - at).toInt()
            reader.read(at, piece, 0, size)
            at += size
        }

        assertEquals(3, source.reads)
    }

    @Test
    fun `a segment changed after the index pass fails its read as corrupt and is not kept`() {
        val file = multi.copyOf()
        val source = ByteArraySource(file)
        val reader = reader(file, source)
        reader.index()
        val second = VAULT_HEADER_SIZE + SEALED_SEGMENT_SIZE + 100
        file[second] = (file[second].toInt() xor 1).toByte()
        val buffer = ByteArray(16)

        assertFailure(VaultFailure.CORRUPT) { reader.read(VAULT_SEGMENT_SIZE + 5L, buffer, 0, buffer.size) }
        assertTrue(buffer.all { it == 0.toByte() }, "nothing of the failed segment reaches the caller")
        reader.read(5, buffer, 0, buffer.size)

        file[second] = (file[second].toInt() xor 1).toByte()
        reader.read(VAULT_SEGMENT_SIZE + 5L, buffer, 0, buffer.size)
        assertEquals(openVault(multi, multiKey).archive()[VAULT_SEGMENT_SIZE + 5], buffer[0])
    }

    @Test
    fun `a damaged segment outranks an archive that breaks the profile, wherever it lies`() {
        val symlink = ByteArray(512).also {
            "link".encodeToByteArray().copyInto(it)
            it[156] = '2'.code.toByte()
        }
        val archive = symlink + ByteArray(3 * VAULT_SEGMENT_SIZE - 512)
        val sealed = seal(archive)

        assertFailure(VaultFailure.BAD_ARCHIVE) { reader(sealed).index() }
        val damaged = sealed.copyOf().also { it[it.size - 20] = (it[it.size - 20].toInt() xor 1).toByte() }
        assertFailure(VaultFailure.CORRUPT) { reader(damaged).index() }
    }

    @Test
    fun `more entries than the caller's limit end the pass in the segment that holds one too many`() {
        val archive = ustarFile("a.txt", byteArrayOf(1)) + ustarFile("b.txt", byteArrayOf(2)) +
            ustarFile("c.bin", ByteArray(2 * VAULT_SEGMENT_SIZE)) + ByteArray(1024)
        val sealed = seal(archive)
        var segments = 0

        assertThrows(VaultLimitReached::class.java) { reader(sealed).index(entryLimit = 2) { segments++ } }
        assertEquals(1, segments)
        assertEquals(3, reader(sealed).index(entryLimit = 3).entries.size)
    }

    @Test
    fun `segments cut at block boundaries index the archive across them`() {
        val content = ByteArray(VAULT_SEGMENT_SIZE * 2 + 1000) { (it * 31).toByte() }
        val archive = ustarFile("big.bin", content) + ustarFile("after.txt", byteArrayOf(1)) + ByteArray(1024)
        val reader = reader(seal(archive))
        val index = reader.index()

        val big = index["big.bin"]!!
        assertEquals(512L, big.offset)
        assertArrayEquals(content, ByteArray(content.size).also { reader.read(big.offset, it, 0, it.size) })
        assertEquals(listOf("big.bin", "after.txt"), index.entries.map { it.path })
    }

    @Test
    fun `a range outside the archive is a programming error, and so is a read after closing`() {
        val reader = reader(multi)
        val buffer = ByteArray(8)

        assertThrows(IllegalArgumentException::class.java) { reader.read(reader.plaintextSize - 4, buffer, 0, 8) }
        assertThrows(IllegalArgumentException::class.java) { reader.read(-1, buffer, 0, 1) }
        reader.read(reader.plaintextSize, buffer, 0, 0)
        reader.close()
        assertThrows(IllegalStateException::class.java) { reader.read(0, buffer, 0, 1) }
    }

    @Test
    fun `closing closes the source, and the caller's CEK can be wiped once the reader exists`() {
        val source = ByteArraySource(multi)
        val cek = multiKey.cek.copyOf()
        val reader = VaultReader(VaultLayout.of(multiHeader, multi.size.toLong()), source, cek)
        cek.fill(0)
        reader.index()
        reader.read(0, ByteArray(512), 0, 512)

        reader.close()
        reader.close()
        assertTrue(source.closed)
        assertTrue(reader.closed)
    }

    @Test
    fun `the segment cache keeps the most recently used and wipes every segment that leaves it`() {
        val cache = SegmentCache(capacity = 2)
        val first = cache.spare().also { it.fill(1) }
        cache[0] = first
        val second = cache.spare().also { it.fill(2) }
        cache[1] = second
        assertSame(first, cache[0])

        val reused = cache.spare()
        assertSame(second, reused, "the least recently used goes first")
        assertTrue(reused.all { it == 0.toByte() })
        assertNull(cache[1])

        cache[1] = reused.also { it.fill(3) }
        cache.clear()
        assertTrue(first.all { it == 0.toByte() } && reused.all { it == 0.toByte() })
        assertNull(cache[0])
    }

    @Test
    fun `a password the spec fixes the bytes of derives as the platform's PBKDF2 does`() {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        for (password in listOf("", "correct horse battery staple")) {
            val spec = PBEKeySpec(password.toCharArray(), multiHeader.salt, multiHeader.iterations, 256)
            assertEquals(
                factory.generateSecret(spec).encoded.toHexString(),
                VaultKeys.kek(password, multiHeader).toHexString(),
                "password \"$password\"",
            )
        }
    }

    @Test
    fun `an empty password derives the KEK the Python and Swift implementations derive`() {
        val header = VaultHeader.read(ByteArrayInputStream(VaultFixtures.bytes("empty.cuevault")))

        assertEquals(
            "fdbc14efda48861e58248eb62381d1ab385e0fa54da933473c6c46491bafd2d0",
            VaultKeys.kek("", header).toHexString(),
        )
    }

    @Test
    fun `a password with a lone surrogate cannot be right`() {
        assertFailure(VaultFailure.WRONG_PASSWORD) { VaultKeys.canonicalPassword("abc\uD800def") }
    }

    @Test
    fun `a stored key of another key_id is stale before it is used`() {
        VaultKeys.checkStoredKey(multiHeader, multiKey.keyId)
        val other = multiKey.keyId.copyOf().also { it[0] = (it[0] + 1).toByte() }

        assertFailure(VaultFailure.STALE_KEY) { VaultKeys.checkStoredKey(multiHeader, other) }
    }

    // The whole file, nothing at either end, its first, middle and last bytes, and short reads across
    // every segment boundary inside it, as (offset in the file, size).
    private fun ranges(entry: VaultEntry): List<Pair<Int, Int>> {
        val size = entry.size.toInt()
        val ends = listOf(0 to size, 0 to 0, size to 0)
        val inside = if (size > 0) listOf(0 to 1, size - 1 to 1, size / 2 to (size + 1) / 2) else emptyList()
        val boundaries = (1..(entry.offset + size) / VAULT_SEGMENT_SIZE)
            .map { (it * VAULT_SEGMENT_SIZE - entry.offset).toInt() }
            .filter { it in 1 until size }
        val crossings = boundaries.flatMap { boundary ->
            val start = maxOf(0, boundary - 3)
            listOf(start to minOf(7, size - start), boundary to 0, boundary - 1 to minOf(2, size - boundary + 1))
        }
        return ends + inside + crossings
    }

    private fun String.nfc() = Normalizer.normalize(this, Normalizer.Form.NFC)

    private fun ustarFile(path: String, content: ByteArray): ByteArray {
        val block = ByteArray(512)
        fun put(at: Int, text: String) = text.encodeToByteArray().copyInto(block, at)
        put(0, path)
        put(100, "0000644\u0000")
        put(108, "0000000\u0000")
        put(116, "0000000\u0000")
        put(124, "%011o\u0000".format(content.size))
        put(136, "%011o\u0000".format(0))
        block[156] = '0'.code.toByte()
        put(257, "ustar\u000000")
        put(329, "0000000\u0000")
        put(337, "0000000\u0000")
        put(148, " ".repeat(8))
        put(148, "%06o\u0000 ".format(block.sumOf { it.toInt() and 0xFF }))
        return block + content.copyOf((content.size + 511) / 512 * 512)
    }
}
