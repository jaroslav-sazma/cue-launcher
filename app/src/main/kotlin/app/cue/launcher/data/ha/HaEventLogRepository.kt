package app.cue.launcher.data.ha

import app.cue.launcher.engine.HA_EVENT_VERSION
import app.cue.launcher.engine.HA_EXTRA_ICON
import app.cue.launcher.engine.HaEvent
import app.cue.launcher.engine.HaEventLog
import app.cue.launcher.engine.HaEventOp
import app.cue.launcher.engine.WallClock
import app.cue.launcher.engine.haCodeKey
import app.cue.launcher.engine.haEventCode
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@SingleIn(AppScope::class)
@Inject
class HaEventLogRepository(
    private val dao: HaEventLogDao,
    private val clock: WallClock,
) : HaEventLog {

    private val rejectedRate = HaRejectedRate()

    fun observeLog(limit: Int = HA_LOG_LIMIT): Flow<List<HaLogEntry>> =
        dao.observeLog(limit).map { rows -> rows.map(HaEventLogEntity::toLogEntry) }

    fun observeLastSeen(): Flow<Map<String, Long>> =
        dao.observeSeen().map { rows -> rows.associate { it.code to it.lastSeenAt } }

    override suspend fun record(event: HaEvent, matched: Int) {
        val now = clock.nowMillis()
        dao.insert(
            HaEventLogEntity(
                receivedAt = now,
                code = event.code,
                op = if (event.op == HaEventOp.END) OP_END else OP_START,
                payload = haLogPayloadText(event),
                matched = matched,
                version = event.version,
            ),
        )
        dao.prune(HA_LOG_LIMIT)

        // Only codes a scenario waits for: ha_code_seen is read for those alone, and an unbounded
        // key would let a stream of made-up codes grow the table without end.
        if (matched > 0) {
            dao.upsertSeen(HaCodeSeenEntity(code = haCodeKey(event.code), lastSeenAt = now))
        }
    }

    // Nothing about a rejected broadcast is trusted, so only the code lands in the log — the payload
    // of a foreign sender is not worth showing back to the user. The budget is shared by every rejected
    // broadcast whatever code it claims: a sender inventing a fresh code each time must not buy itself
    // a row, a log line or a map entry per code. False means the broadcast was not recorded at all.
    // The code is asked for only once the budget has admitted the row: reading it out of a foreign
    // Bundle can make the platform log a stack trace, and that must not scale with the flood either.
    suspend fun recordRejected(code: () -> String?): Boolean {
        val now = clock.nowMillis()
        if (!rejectedRate.admit(now)) return false
        dao.insert(
            HaEventLogEntity(
                receivedAt = now,
                code = haEventCode(code()).orEmpty(),
                op = OP_START,
                payload = "",
                matched = HA_LOG_REJECTED,
            ),
        )
        dao.pruneRejected(HA_REJECTED_LOG_LIMIT)
        dao.prune(HA_LOG_LIMIT)
        return true
    }

    suspend fun clearLog() = dao.clearLog()

    private companion object {
        const val OP_START = "start"
        const val OP_END = "end"
    }
}

// A fixed window over the wall clock. A clock that jumps backwards opens a new window rather than
// leaving the budget spent until it has caught up again.
class HaRejectedRate(
    private val perWindow: Int = HA_REJECTED_PER_MINUTE,
    private val windowMillis: Long = HA_REJECTED_WINDOW_MILLIS,
) {

    private var windowStart: Long? = null

    private var used = 0

    @Synchronized
    fun admit(now: Long): Boolean {
        val start = windowStart
        if (start == null || now - start !in 0 until windowMillis) {
            windowStart = now
            used = 0
        }
        if (used >= perWindow) return false
        used++
        return true
    }
}

const val HA_REJECTED_PER_MINUTE = 6

const val HA_REJECTED_WINDOW_MILLIS = 60_000L

data class HaLogEntry(
    val id: Long,
    val receivedAt: Long,
    val code: String,
    val end: Boolean,
    val payload: String,
    val matched: Int,
    val version: Int = 0,
) {
    val rejected: Boolean get() = matched < 0

    val unknown: Boolean get() = matched == 0

    // An event from a blueprint older than the current contract still works, but the user should know
    // where to look when something in it behaves unexpectedly.
    val legacy: Boolean get() = !rejected && version < HA_EVENT_VERSION
}

fun HaEventLogEntity.toLogEntry(): HaLogEntry = HaLogEntry(
    id = id,
    receivedAt = receivedAt,
    code = code,
    end = op.equals("end", ignoreCase = true),
    payload = payload,
    matched = matched,
    version = version,
)

fun haLogPayloadText(event: HaEvent): String {
    val parts = buildList {
        event.icon?.let { add("$HA_EXTRA_ICON=$it") }
        event.fields.entries.sortedBy { it.key }.forEach { (key, value) -> add("$key=$value") }
    }
    return parts.joinToString(" · ")
}
