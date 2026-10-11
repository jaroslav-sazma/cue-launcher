package app.cue.launcher.system

import app.cue.launcher.engine.StateAspect
import app.cue.launcher.engine.StateNeeds
import app.cue.launcher.engine.StateProvider
import app.cue.launcher.engine.SystemState
import app.cue.launcher.engine.SystemStateSource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import java.util.concurrent.ConcurrentHashMap

@SingleIn(AppScope::class)
@Inject
class SystemConditions(
    private val providers: Set<StateProvider>,
    private val scope: CoroutineScope,
) : SystemStateSource {

    private val readings = ConcurrentHashMap<StateNeeds, Flow<SystemState>>()

    override fun observeState(): Flow<SystemState> = observe(providers, StateProvider::observe)

    override fun observeState(aspects: Set<StateAspect>): Flow<SystemState> =
        observe(providers.filter { it.aspect in aspects }, StateProvider::observe)

    // An event's reading keeps its providers subscribed for a few seconds, the one exception to
    // following nothing in the background: a notification that updates every second and fires a
    // gated scenario then reads the live value instead of registering a network callback or
    // querying the calendar on each post. A quiet moment later they are let go and the value
    // forgotten, so nothing stale is read. The providers are asked anew on every subscription, as
    // what they can read changes with the permissions granted meanwhile, and one that fails reads as
    // unknown rather than leaving the event waiting.
    override suspend fun measure(needs: StateNeeds): SystemState {
        if (needs.aspects.isEmpty()) return SystemState()
        val reading = readings.getOrPut(needs) {
            flow { emitAll(observe(providers.filter { it.aspect in needs.aspects }) { it.observeForEvent(needs) }) }
                .catch { emit(SystemState()) }
                .shareIn(scope, SharingStarted.WhileSubscribed(KEEP_MILLIS, replayExpirationMillis = 0), replay = 1)
        }
        return reading.first()
    }

    private fun observe(
        chosen: Collection<StateProvider>,
        reading: (StateProvider) -> Flow<SystemState>,
    ): Flow<SystemState> = when {
        chosen.isEmpty() -> flowOf(SystemState())
        else -> combine(chosen.map(reading)) { patches ->
            patches.fold(SystemState(), SystemState::mergedWith)
        }.distinctUntilChanged()
    }
}

private const val KEEP_MILLIS = 5_000L
