package app.cue.launcher.system.ha

import app.cash.turbine.test
import app.cue.launcher.data.ha.HaIconAttributes
import app.cue.launcher.data.ha.HaProbe
import app.cue.launcher.data.ha.HaStateRead
import app.cue.launcher.engine.WallClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HaStateReaderTest {

    private var answer: suspend () -> HaStateRead = { read() }

    private fun TestScope.reader(): HaStateReader = HaStateReader(
        states = { answer() },
        live = { _, _ -> false },
        clock = WallClock { testScheduler.currentTime },
    )

    // Read once, then left alone well past the freshness a tile's text gets: the state a gate held up
    // on the last visit to the home screen.
    private suspend fun TestScope.remembering(state: String): HaStateReader {
        val reader = reader()
        answer = { read(TV to state) }
        reader.read(setOf(TV))
        advanceTimeBy(LONG_AGO_MILLIS)
        return reader
    }

    @Test
    fun `a gate starts from the last state read, however old, before the read lands`() = runTest {
        val reader = remembering("playing")
        val pending = CompletableDeferred<HaStateRead>()
        answer = { pending.await() }

        reader.observeGate(setOf(TV)).test {
            assertEquals(mapOf(TV to "playing"), awaitItem())

            pending.complete(read(TV to "off"))
            assertEquals(mapOf(TV to "off"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a tile's text starts only from what is fresh`() = runTest {
        val reader = remembering("playing")
        val pending = CompletableDeferred<HaStateRead>()
        answer = { pending.await() }

        reader.observeStates(setOf(TV)).test {
            assertEquals(emptyMap<String, String>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an entity the read does not return is forgotten`() = runTest {
        val reader = remembering("playing")
        answer = { HaStateRead(HaProbe.Network("unreachable")) }

        reader.observeGate(setOf(TV)).test {
            assertEquals(mapOf(TV to "playing"), awaitItem())
            assertEquals(emptyMap<String, String>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        reader.observeGate(setOf(TV)).test {
            assertEquals(emptyMap<String, String>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an event waits for the read instead of an old state`() = runTest {
        val reader = remembering("playing")
        answer = { read(TV to "off") }

        reader.observeGateForEvent(setOf(TV)).test {
            assertEquals(mapOf(TV to "off"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an event read that hangs gives up in time, reads as unknown and stores nothing later`() = runTest {
        val reader = remembering("playing")
        val pending = CompletableDeferred<HaStateRead>()
        var cancelled = false
        answer = {
            try {
                pending.await()
            } catch (cause: CancellationException) {
                cancelled = true
                throw cause
            }
        }
        val startedAt = testScheduler.currentTime

        assertEquals(emptyMap<String, String>(), reader.observeGateForEvent(setOf(TV)).first())

        assertEquals(EVENT_READ_MILLIS, testScheduler.currentTime - startedAt)
        assertTrue(cancelled)
        pending.complete(read(TV to "off"))
        runCurrent()
        assertEquals(emptyMap<String, String>(), reader.observeStates(setOf(TV)).first())
    }

    @Test
    fun `an event read that hangs still yields the states that are fresh`() = runTest {
        val reader = reader()
        answer = { read(TV to "playing") }
        reader.read(setOf(TV))
        answer = { awaitCancellation() }

        assertEquals(mapOf(TV to "playing"), reader.observeGateForEvent(setOf(TV, DOOR)).first())
    }

    @Test
    fun `nothing to follow reads nothing`() = runTest {
        var reads = 0
        answer = {
            reads++
            read()
        }

        reader().observeGate(emptySet()).test {
            assertEquals(emptyMap<String, String>(), awaitItem())
            awaitComplete()
        }
        assertEquals(0, reads)
    }

    // A device class stays put while its entity is unavailable, or a gate would turn into a window.
    @Test
    fun `the attributes of a read outlive its state`() = runTest {
        val reader = reader()
        val gate = HaIconAttributes(deviceClass = "gate")
        answer = { read(DOOR to "open").copy(attributes = mapOf(DOOR to gate)) }
        reader.read(setOf(DOOR))
        answer = { read().copy(attributes = emptyMap()) }
        reader.read(setOf(DOOR), force = true)

        assertEquals(mapOf(DOOR to gate), reader.observeAttributes(setOf(DOOR, TV)).first())
        assertEquals(emptyMap<String, HaIconAttributes>(), reader.observeAttributes(setOf(TV)).first())
    }

    private fun read(vararg states: Pair<String, String>): HaStateRead =
        HaStateRead(HaProbe.Ok(entities = states.size, millis = 0L), values = states.toMap())

    private companion object {

        const val TV = "media_player.living_room_tv"

        const val DOOR = "binary_sensor.door"

        const val EVENT_READ_MILLIS = 3_000L

        const val LONG_AGO_MILLIS = 60 * 60 * 1_000L
    }
}
