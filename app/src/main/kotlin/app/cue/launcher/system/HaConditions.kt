package app.cue.launcher.system

import app.cue.launcher.engine.HaStateSource
import app.cue.launcher.engine.HaStates
import app.cue.launcher.engine.ScenarioSource
import app.cue.launcher.engine.StateAspect
import app.cue.launcher.engine.StateNeeds
import app.cue.launcher.engine.StateProvider
import app.cue.launcher.engine.SystemState
import app.cue.launcher.engine.haGateEntities
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@SingleIn(AppScope::class)
@Inject
class HaConditions(
    private val scenarios: ScenarioSource,
    private val states: HaStateSource,
) : StateProvider {

    override val aspect: StateAspect = StateAspect.HA

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<SystemState> =
        scenarios.observeScenarios()
            .map(::haGateEntities)
            .distinctUntilChanged()
            .flatMapLatest(states::observeGate)
            .map(::stateOf)

    override fun observeForEvent(needs: StateNeeds): Flow<SystemState> =
        states.observeGateForEvent(needs.haEntities).map(::stateOf)

    private fun stateOf(states: Map<String, String>): SystemState = SystemState(ha = HaStates.of(states))
}
