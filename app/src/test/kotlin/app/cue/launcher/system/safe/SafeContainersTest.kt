package app.cue.launcher.system.safe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

class SafeContainersTest {

    @ParameterizedTest
    @MethodSource("providerFailures")
    fun `whatever another app's provider throws becomes a file that cannot be read`(failure: RuntimeException) {
        val thrown = assertThrows<IOException> { fromProvider { throw failure } }
        assertSame(failure, thrown.cause)
    }

    @Test
    fun `a lost grant and a missing file keep their meaning`() {
        val lost = SecurityException("no grant")
        assertSame(lost, assertThrows<SecurityException> { fromProvider { throw lost } })
        assertThrows<FileNotFoundException> { fromProvider { throw FileNotFoundException("gone") } }
    }

    @Test
    fun `a stream that breaks inside the provider fails as a read error`() {
        val stream = ProviderInputStream(BrokenStream(UnsupportedOperationException("not now")))

        assertThrows<IOException> { stream.read() }
        assertThrows<IOException> { stream.read(ByteArray(8), 0, 8) }
        assertThrows<IOException> { stream.readAllBytes() }
        assertThrows<IOException> { stream.skip(4) }
        assertDoesNotThrow { stream.close() }
    }

    @Test
    fun `a healthy stream reads through unchanged`() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        assertEquals(bytes.toList(), ProviderInputStream(ByteArrayInputStream(bytes)).readAllBytes().toList())
    }

    private class BrokenStream(private val failure: RuntimeException) : InputStream() {
        override fun read(): Int = throw failure
        override fun read(b: ByteArray, off: Int, len: Int): Int = throw failure
        override fun skip(n: Long): Long = throw failure
        override fun close() = throw failure
    }

    companion object {
        @JvmStatic
        fun providerFailures() = listOf(
            IllegalArgumentException("unknown uri"),
            IllegalStateException("cursor closed"),
            NullPointerException("null column"),
            UnsupportedOperationException("no streams"),
            ArrayIndexOutOfBoundsException("parcel"),
        )
    }
}
