package app.cue.launcher.system.safe

import androidx.datastore.core.DataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val WALL = 1_800_000_000_000L

internal class FailingStream(private val failure: RuntimeException) : InputStream() {
    override fun read(): Int = throw failure
    override fun read(b: ByteArray, off: Int, len: Int): Int = throw failure
}

// Serves the first bytes of the file, then fails as a provider does.
internal class FailAfter(
    private val source: InputStream,
    private var left: Int,
    private val failure: RuntimeException,
) : InputStream() {
    override fun read(): Int = throw failure
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (left <= 0) throw failure
        return source.read(b, off, minOf(len, left)).also { left -= it }
    }
}

// Serves the file in small pieces and holds the first read that starts at `at` or later until
// released; reached says one is held.
internal class Hold(private val at: Long) {
    val reached = CompletableDeferred<Unit>()
    private val released = CountDownLatch(1)

    fun release() = released.countDown()

    fun stream(source: InputStream): InputStream = object : InputStream() {
        private var position = 0L

        override fun read(): Int = ByteArray(1).let { if (read(it, 0, 1) < 0) -1 else it[0].toInt() and 0xFF }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (position >= at && reached.complete(Unit)) released.await(10, TimeUnit.SECONDS)
            return source.read(b, off, minOf(len, 64)).also { if (it > 0) position += it }
        }
    }
}

internal class MemoryStore<T>(initial: T) : DataStore<T> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<T> = state
    override suspend fun updateData(transform: suspend (t: T) -> T): T =
        transform(state.value).also { state.value = it }
}

// hold() keeps every prompt open until the returned gate completes; prompted says one is open.
internal class FakeKeys : SafeKeyGuard {
    override var canKeep = true
    var sealing: (ByteArray) -> KeySealing = { KeySealing.Sealed(ByteArray(12), it.copyOf()) }
    val sealed = mutableListOf<ByteArray>()
    var opening: (ByteArray) -> KeyOpening = { KeyOpening.Opened(it.copyOf()) }
    var prompts = 0
    var forgotten = false
    val prompted = CompletableDeferred<Unit>()
    private var gate: CompletableDeferred<Unit>? = null

    fun hold(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { gate = it }

    override suspend fun seal(cek: ByteArray): KeySealing {
        sealed += cek
        prompt()
        return sealing(cek)
    }

    override suspend fun open(iv: ByteArray, sealedCek: ByteArray): KeyOpening {
        prompt()
        return opening(sealedCek)
    }

    private suspend fun prompt() {
        prompts++
        prompted.complete(Unit)
        gate?.await()
    }

    override fun forget() {
        forgotten = true
    }
}

internal class FakeContainers(var file: ByteArray) : SafeContainers {
    var failure: Exception? = null
    var wrap: (InputStream) -> InputStream = { it }
    var unknownLength = false
    var sizeHint: Long? = null
    var opens = 0
    var consumed = 0L
    val released = mutableListOf<String>()

    override fun open(uri: String): SafeContainerStream {
        opens++
        failure?.let { throw it }
        val source = wrap(ByteArrayInputStream(file))
        val counted = object : InputStream() {
            override fun read(): Int = source.read().also { if (it >= 0) consumed++ }
            override fun read(b: ByteArray, off: Int, len: Int): Int =
                source.read(b, off, len).also { if (it > 0) consumed += it }
        }
        val length = if (unknownLength) -1 else file.size.toLong()
        return SafeContainerStream(counted, length, sizeHint ?: length)
    }

    override fun release(uri: String) {
        released += uri
    }
}

internal class FakeHost : SafeHost {
    override val session = MutableStateFlow<SafeSession?>(null)
    var opens = 0

    override fun open(session: SafeSession) {
        opens++
        this.session.value = session
    }

    override fun close() {
        session.value?.content?.close()
        session.value = null
    }

    override fun update(transform: (SafeSession) -> SafeSession) = session.update { it?.let(transform) }
}

internal class FakeClock : SafeClock {
    var elapsed = 0L
    var source: () -> Long = { elapsed }
    override fun wallMillis() = WALL + source()
    override fun elapsedMillis() = source()
}
