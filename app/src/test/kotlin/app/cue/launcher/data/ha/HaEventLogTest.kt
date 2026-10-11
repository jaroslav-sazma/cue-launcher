package app.cue.launcher.data.ha

import app.cue.launcher.engine.HA_CODE_MAX_LENGTH
import app.cue.launcher.engine.HaEvent
import app.cue.launcher.engine.WallClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HaEventLogTest {

    @Test
    fun `payload is assembled alphabetically with the icon first`() {
        val event = HaEvent(
            code = "susicka",
            icon = "mdi:tumble-dryer",
            fields = linkedMapOf("zbyva" to "12", "program" to "bavlna"),
        )

        assertEquals("icon=mdi:tumble-dryer · program=bavlna · zbyva=12", haLogPayloadText(event))
    }

    @Test
    fun `an event without a payload has an empty line`() {
        assertEquals("", haLogPayloadText(HaEvent(code = "zvonek")))
    }

    @Test
    fun `an unmatched event is flagged in the log`() {
        val unknown = HaEventLogEntity(
            id = 1,
            receivedAt = 1_000,
            code = "Zvonek",
            op = "start",
            payload = "",
            matched = 0,
        ).toLogEntry()

        assertTrue(unknown.unknown)
        assertFalse(unknown.end)

        assertEquals("Zvonek", unknown.code)
    }

    @Test
    fun `an end code is recognizable in the log`() {
        val end = HaEventLogEntity(
            id = 2,
            receivedAt = 2_000,
            code = "pracka",
            op = "end",
            payload = "",
            matched = 1,
        ).toLogEntry()

        assertTrue(end.end)
        assertFalse(end.unknown)
    }

    @Test
    fun `an unreadable token reads as empty`() {
        val settings = HaSettings(
            urlInternal = "http://ha.local:8123",
            token = HaSealedToken(iv = "xx", data = "yy"),
            homeNetworks = listOf("Doma-5G"),
        )

        assertEquals("", settings.toConfig { null }.token)
        assertEquals("tajne", settings.toConfig { "tajne" }.token)
        assertEquals(listOf("Doma-5G"), settings.toConfig { null }.homeNetworks)
    }

    @Test
    fun `without a stored token, the token is empty`() {
        assertEquals("", HaSettings().toConfig { error("nemá se volat") }.token)
    }

    @Test
    fun `rejected broadcasts share one budget per window`() {
        val rate = HaRejectedRate(perWindow = 3, windowMillis = 60_000)

        assertEquals(listOf(true, true, true, false, false), (0 until 5).map { rate.admit(1_000L + it) })
        assertFalse(rate.admit(60_999))
        assertTrue(rate.admit(61_000))
    }

    @Test
    fun `a clock that jumps back opens a new window instead of keeping the budget spent`() {
        val rate = HaRejectedRate(perWindow = 1, windowMillis = 60_000)

        assertTrue(rate.admit(100_000))
        assertFalse(rate.admit(100_001))
        assertTrue(rate.admit(50_000))
    }

    // A sender inventing a fresh code for every broadcast must not buy a row per code: the budget is
    // shared, the code that does get written is cut to the cap, and the extras are not even read for
    // a broadcast over the budget.
    @Test
    fun `a flood of made-up codes writes and reads only the budget, each code capped`() = runTest {
        val dao = FakeDao()
        val repository = HaEventLogRepository(dao, WallClock { 5_000 })

        var read = 0
        val recorded = (0 until 1_000).count { index ->
            repository.recordRejected {
                read++
                "kod$index" + "x".repeat(500)
            }
        }

        assertEquals(HA_REJECTED_PER_MINUTE, recorded)
        assertEquals(HA_REJECTED_PER_MINUTE, read, "the code is read only for an admitted row")
        assertEquals(HA_REJECTED_PER_MINUTE, dao.rows.size)
        assertTrue(dao.rows.all { it.code.length == HA_CODE_MAX_LENGTH && it.matched == HA_LOG_REJECTED })
    }

    private class FakeDao : HaEventLogDao {

        val rows = mutableListOf<HaEventLogEntity>()

        override fun observeLog(limit: Int): Flow<List<HaEventLogEntity>> = emptyFlow()

        override suspend fun insert(row: HaEventLogEntity) {
            rows += row
        }

        override suspend fun prune(keep: Int) = Unit

        override suspend fun pruneRejected(keep: Int) = Unit

        override suspend fun clearLog() = rows.clear()

        override fun observeSeen(): Flow<List<HaCodeSeenEntity>> = emptyFlow()

        override suspend fun upsertSeen(row: HaCodeSeenEntity) = Unit
    }
}
