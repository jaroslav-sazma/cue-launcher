package app.cue.launcher.system.ha

import app.cash.turbine.test
import app.cue.launcher.data.ha.HaIconSet
import app.cue.launcher.data.ha.HaIconTable
import app.cue.launcher.engine.WallClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HaIconReaderTest {

    private val instance = MutableStateFlow<String?>(HOME)

    private val handshakes = MutableStateFlow<HaHandshake?>(null)

    private var stored: HaIconTable? = null

    private var version = "2026.10.1"

    private var reachable = true

    private val asked = mutableListOf<Set<String>>()

    private val saved = mutableListOf<HaIconTable>()

    private var now = 0L

    private fun TestScope.reader() = HaIconReader(
        instance = instance,
        handshakes = handshakes,
        fetch = { domains ->
            asked += domains
            if (reachable) table(version, domains) else null
        },
        load = { stored },
        save = { saved += it },
        clock = WallClock { now },
    )

    private fun handshake(version: String, of: String = HOME) {
        handshakes.value = HaHandshake(of, version, (handshakes.value?.count ?: 0) + 1)
    }

    @Test
    fun `nothing is read while no tile needs an icon from Home Assistant`() = runTest {
        assertNull(reader().observe(emptySet()).first())
        assertEquals(emptyList<Set<String>>(), asked)
    }

    @Test
    fun `a missing table is read for the domains on screen, once, and stored`() = runTest {
        val reader = reader()

        reader.observe(setOf("lock", "cover")).test {
            assertNull(awaitItem())
            assertEquals(table("2026.10.1", setOf("lock", "cover")), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        reader.observe(setOf("lock")).test {
            assertEquals(table("2026.10.1", setOf("lock", "cover")), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf(setOf("lock", "cover")), asked)
        assertEquals(listOf(table("2026.10.1", setOf("lock", "cover"))), saved)
    }

    // Home Assistant answers for the integrations it is asked about, so a lock added later is not
    // left on the generic glyph until the next release.
    @Test
    fun `a domain the table was not read for is asked for alone and merged in`() = runTest {
        stored = table("2026.10.1", setOf("lock"))

        reader().observe(setOf("lock", "light")).test {
            assertEquals(table("2026.10.1", setOf("lock")), awaitItem())
            assertEquals(table("2026.10.1", setOf("lock", "light")), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf(setOf("light")), asked)
    }

    @Test
    fun `a stored table of the version the instance runs is not read again`() = runTest {
        stored = table("2026.10.1", setOf("lock"))
        handshake("2026.10.1")

        reader().observe(setOf("lock")).test {
            assertEquals(table("2026.10.1", setOf("lock")), awaitItem())
            handshake("2026.10.1")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(emptyList<Set<String>>(), asked)
    }

    @Test
    fun `a new version announced by the live channel reads the table again`() = runTest {
        stored = table("2026.9.3", setOf("lock", "cover"))

        reader().observe(setOf("lock")).test {
            assertEquals(table("2026.9.3", setOf("lock", "cover")), awaitItem())
            handshake("2026.10.1")
            assertEquals(table("2026.10.1", setOf("lock")), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf(setOf("lock")), asked)
    }

    // A handshake of the instance used before says nothing about the one set up now.
    @Test
    fun `a version announced by another instance is not held against the table`() = runTest {
        stored = table("2026.10.1", setOf("lock"))
        handshake("2026.11.0", of = "elsewhere")

        reader().observe(setOf("lock")).test {
            assertEquals(table("2026.10.1", setOf("lock")), awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(emptyList<Set<String>>(), asked)
    }

    @Test
    fun `a table of another instance is neither shown nor kept`() = runTest {
        stored = table("2026.10.1", setOf("lock"), instance = "elsewhere")

        reader().observe(setOf("lock")).test {
            assertNull(awaitItem())
            assertEquals(table("2026.10.1", setOf("lock")), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `without Home Assistant set up there is no table and no read`() = runTest {
        instance.value = null

        assertNull(reader().observe(setOf("lock")).first())
        assertEquals(emptyList<Set<String>>(), asked)
    }

    // A proxy that refuses the socket would otherwise cost a connection on every visit to the home screen.
    @Test
    fun `a failed read waits before it is tried again`() = runTest {
        reachable = false
        val reader = reader()

        repeat(2) {
            reader.observe(setOf("lock")).test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
        assertEquals(1, asked.size)

        now += RETRY_MILLIS
        reachable = true
        reader.observe(setOf("lock")).test {
            assertNull(awaitItem())
            assertEquals(table("2026.10.1", setOf("lock")), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, asked.size)
    }

    // A token put right after a refused read: a handshake that gets through is proof enough, even one
    // announcing the version the failed read already saw.
    @Test
    fun `a handshake that comes through ends the wait after a failed read`() = runTest {
        handshake("2026.10.1")
        reachable = false
        val reader = reader()

        reader.observe(setOf("lock")).test {
            assertNull(awaitItem())
            reachable = true
            handshake("2026.10.1")
            assertEquals(table("2026.10.1", setOf("lock")), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(2, asked.size)
    }

    private fun table(version: String, domains: Set<String>, instance: String = HOME) = HaIconTable(
        instance = instance,
        version = version,
        domains = domains.associateWith { mapOf("_" to HaIconSet(default = "mdi:$it")) },
        asked = domains,
    )

    private companion object {

        const val HOME = "home"

        const val RETRY_MILLIS = 15 * 60_000L
    }
}
