package app.cue.launcher.system.safe

import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.io.InputStream
import java.nio.channels.ClosedChannelException

class SafeCopiesTest {

    @TempDir
    lateinit var root: File

    private val directory by lazy { File(root, "safe") }
    private val descriptors = mutableListOf<FileDescriptor>()
    private var reserved = 0L
    private var reserve: (FileDescriptor, Long) -> Unit = { descriptor, bytes ->
        descriptors += descriptor
        reserved = bytes
    }
    private val copies by lazy { SafeCopies(directory) { descriptor, bytes -> reserve(descriptor, bytes) } }

    private val head = ByteArray(136) { it.toByte() }
    private val rest = ByteArray(700_000) { (it * 7).toByte() }

    private fun nothingLeft() {
        assertTrue(directory.listFiles().isNullOrEmpty()) { "left behind: ${directory.list()?.toList()}" }
        assertTrue(descriptors.none { it.valid() }) { "the copy is still open" }
    }

    @Test
    fun `the copy holds the header and the rest of the stream, under no name, at any position`() {
        val copy = copies.copy(head, ByteArrayInputStream(rest), head.size + rest.size.toLong()) {}

        assertEquals(head.size + rest.size.toLong(), copy.length)
        assertEquals(copy.length, reserved)
        assertTrue(directory.listFiles().isNullOrEmpty()) { "the copy keeps no name" }
        val buffer = ByteArray(1000)
        copy.read(130, buffer, 1000)
        assertArrayEquals(head.copyOfRange(130, 136) + rest.copyOf(994), buffer)
        copy.read(copy.length - 10, buffer, 10)
        assertArrayEquals(rest.copyOfRange(rest.size - 10, rest.size), buffer.copyOf(10))
        assertThrows<EOFException> { copy.read(copy.length - 10, buffer, 11) }

        copy.close()
        nothingLeft()
        assertThrows<ClosedChannelException> { copy.read(0, buffer, 1) }
    }

    @Test
    fun `a length that is unknown or wrong only decides what is reserved`() {
        val unknown = copies.copy(head, ByteArrayInputStream(rest), -1) {}
        assertEquals(0L, reserved)
        assertEquals(head.size + rest.size.toLong(), unknown.length)
        unknown.close()

        val overstated = copies.copy(head, ByteArrayInputStream(rest), 10_000_000) {}
        assertEquals(head.size + rest.size.toLong(), overstated.length)
        assertThrows<EOFException> { overstated.read(overstated.length, ByteArray(1), 1) }
        overstated.close()
    }

    @Test
    fun `a vault the storage cannot reserve room for is not enough space, and nothing is left`() {
        reserve = { descriptor, _ ->
            descriptors += descriptor
            throw IOException("ENOSPC")
        }
        var chunks = 0

        assertThrows<SafeNoSpace> { copies.copy(head, ByteArrayInputStream(rest), 1L shl 40) { chunks++ } }
        assertEquals(0, chunks, "nothing is read once the room is known to be missing")
        nothingLeft()
    }

    @Test
    fun `storage that cannot take the file is not enough space either`() {
        root.setWritable(false)
        try {
            assertThrows<SafeNoSpace> { copies.copy(head, ByteArrayInputStream(rest), -1) {} }
        } finally {
            root.setWritable(true)
        }
        assertFalse(directory.exists())
    }

    @Test
    fun `a provider that fails while the file is copied leaves nothing behind`() {
        val failing = object : InputStream() {
            var served = 0
            override fun read(): Int = throw IOException("offline")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (served > 0) throw IOException("offline")
                served = len
                return len
            }
        }

        val thrown = assertThrows<IOException> { copies.copy(head, failing, rest.size.toLong()) {} }
        assertEquals("offline", thrown.message)
        nothingLeft()
    }

    @Test
    fun `a cancelled unlock stops the copy and leaves nothing behind`() {
        var chunks = 0
        assertThrows<CancellationException> {
            copies.copy(head, ByteArrayInputStream(rest), rest.size.toLong()) {
                if (++chunks == 2) throw CancellationException("locked")
            }
        }
        nothingLeft()
    }
}
