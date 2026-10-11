package app.cue.launcher.system.ha

import app.cue.launcher.data.ha.HA_LIVE_POLL_MILLIS
import app.cue.launcher.data.ha.HA_LIVE_RECONNECT_MILLIS
import app.cue.launcher.data.ha.HA_LIVE_SETTLED_MILLIS
import app.cue.launcher.data.ha.HaProbe
import app.cue.launcher.data.ha.HaStateRead
import app.cue.launcher.data.ha.haLiveWait
import app.cue.launcher.engine.HaStateSource
import app.cue.launcher.engine.WallClock
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

@Suppress("TooManyFunctions")
@SingleIn(AppScope::class)
class HaStateReader internal constructor(
    private val states: suspend (Set<String>) -> HaStateRead,
    private val live: suspend (Set<String>, suspend (Map<String, String>) -> Unit) -> Boolean,
    private val clock: WallClock,
) : HaStateSource {

    @Inject
    constructor(gateway: HaGateway, socket: HaSocket, clock: WallClock) :
        this(gateway::states, socket::consume, clock)

    private val refreshes = MutableStateFlow(0)

    private val nudges = MutableStateFlow(0)

    private val cached = MutableStateFlow<Map<String, CachedState>>(emptyMap())

    private val reading = Mutex()

    // A REST read first, so a tile paints as soon as it appears, then the live channel for as long as
    // the collector lives. Without a socket the same flow falls back to a slow poll, which is what
    // keeps a value from freezing on a home screen nobody leaves.
    override fun observeStates(entities: Set<String>): Flow<Map<String, String>> = following(entities, ::fresh)

    override fun observeGate(entities: Set<String>): Flow<Map<String, String>> = following(entities, ::remembered)

    // An event keeps its reading only while events keep coming, so it re-reads once the values age
    // out rather than opening a socket for the few seconds it has left. The read is cut short well
    // inside the receiver's budget: a token refresh or an endpoint fallback can take longer than the
    // whole step may, and running out there would drop every listener of the event, gated by Home
    // Assistant or not. Cut short, the read is cancelled and stores nothing, and an entity without a
    // fresh state reads as unknown.
    override fun observeGateForEvent(entities: Set<String>): Flow<Map<String, String>> {
        if (entities.isEmpty()) return flowOf(emptyMap())
        return flow {
            while (true) {
                emit(withTimeoutOrNull(EVENT_READ_MILLIS) { read(entities).values } ?: fresh(entities))
                delay(FRESH_MILLIS)
            }
        }.distinctUntilChanged()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun following(
        entities: Set<String>,
        first: (Set<String>) -> Map<String, String>,
    ): Flow<Map<String, String>> {
        if (entities.isEmpty()) return flowOf(emptyMap())
        return refreshes
            .transformLatest {

                emit(first(entities))
                emit(read(entities).values)
                emitAll(follow(entities))
            }
            .distinctUntilChanged()
    }

    override fun refresh() {
        refreshes.update { it + 1 }
    }

    // A service call just went out. With a live socket the instance pushes the change itself; without
    // one this pulls the next poll forward, so a gate icon does not sit on its old colour for a
    // minute. Deliberately not a refresh(): restarting the flow would tear the socket down.
    override fun nudge() {
        nudges.update { it + 1 }
    }

    suspend fun read(entities: Set<String>, force: Boolean = false): HaStateRead = reading.withLock {
        val known = if (force) emptyMap() else fresh(entities)
        val stale = entities - known.keys
        if (stale.isEmpty()) return@withLock HaStateRead(
            probe = HaProbe.Ok(entities = known.size, millis = 0L),
            values = known,
            atMillis = newestRead(entities),
        )
        val result = states(stale)
        val now = clock.nowMillis()
        store(result.values, now, drop = stale)
        result.copy(values = known + result.values, atMillis = now)
    }

    // Without a live channel the same flow falls back to a slow poll; with one that keeps dropping it
    // reconnects ever more slowly rather than every five seconds for as long as the screen is on.
    private fun follow(entities: Set<String>): Flow<Map<String, String>> =
        channelFlow {
            // The home screen is a corridor: most visits are a pass between two apps. The REST read
            // has already painted the tile, so the socket waits out the grace — a pass-through never
            // opens one, and a change in those seconds still arrives with the subscription's initial
            // state set. The nudge counter is read before the grace so a tap during it is not lost.
            var seen = nudges.value
            delay(GRACE_MILLIS)
            var wait = HA_LIVE_RECONNECT_MILLIS
            while (isActive) {
                val openedAt = clock.nowMillis()
                val streamed = live(entities) { values ->
                    store(values, clock.nowMillis(), drop = entities)
                    send(values)
                }
                wait = haLiveWait(wait, settled = clock.nowMillis() - openedAt >= HA_LIVE_SETTLED_MILLIS)
                if (streamed) {
                    delay(wait)
                } else {
                    awaitNudge(seen, HA_LIVE_POLL_MILLIS)
                    send(read(entities, force = true).values)
                }
                seen = nudges.value
            }
        }

    // A poll delay a nudge cuts short: an action was sent, so the next read should not be a minute
    // away. On the live path nudges are ignored — the socket carries the change on its own.
    private suspend fun awaitNudge(seen: Int, millis: Long) {
        withTimeoutOrNull(millis) { nudges.first { it != seen } }
    }

    private fun store(values: Map<String, String>, now: Long, drop: Set<String>) {
        cached.update { current ->

            current - drop + values.mapValues { (_, state) -> CachedState(state, now) }
        }
    }

    private fun fresh(entities: Set<String>): Map<String, String> {
        val now = clock.nowMillis()
        val states = cached.value
        return entities.mapNotNull { entity ->
            states[entity]?.takeIf { now - it.readAt in 0..FRESH_MILLIS }?.let { entity to it.state }
        }.toMap()
    }

    private fun remembered(entities: Set<String>): Map<String, String> {
        val states = cached.value
        return entities.mapNotNull { entity -> states[entity]?.let { entity to it.state } }.toMap()
    }

    private fun newestRead(entities: Set<String>): Long {
        val states = cached.value
        return entities.mapNotNull { states[it]?.readAt }.maxOrNull() ?: 0L
    }

    private data class CachedState(val state: String, val readAt: Long)

    private companion object {

        const val FRESH_MILLIS = 30_000L

        const val GRACE_MILLIS = 1_000L

        // A healthy read is well under a second even through a cloud relay; this still covers a
        // connection timeout, and leaves five of the receiver's eight seconds for the rest.
        const val EVENT_READ_MILLIS = 3_000L
    }
}
