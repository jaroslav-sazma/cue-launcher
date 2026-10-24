package app.cue.launcher.engine

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@SingleIn(AppScope::class)
@Inject
class AlarmMonitor(
    private val scenarios: ScenarioSource,
    private val activations: ActivationStore,
    private val systemState: SystemStateSource,
    private val engine: ScenarioEngine,
    private val memory: AlarmMemory,
    private val clock: WallClock,
) {

    private val lock = Mutex()

    // The alarm is read under the lock, or a stale read could overwrite a newer one. It is remembered
    // only after the activation: dying in between replays the ring instead of losing it, and
    // activating twice with the same ring time changes nothing.
    suspend fun onNextAlarm(readUpcoming: () -> Long?) = lock.withLock {
        val upcoming = readUpcoming()
        val now = clock.nowMillis()
        val remembered = memory.rememberedAlarm()
        if (remembered == upcoming) return@withLock
        rungAlarm(remembered, upcoming, now)?.let { rangAt -> activateRung(rangAt, now) }
        memory.rememberAlarm(upcoming)
    }

    suspend fun rememberNextAlarm(readUpcoming: () -> Long?) = lock.withLock {
        memory.rememberAlarm(readUpcoming())
    }

    // Ends before fires, so an alarm that ends its own scenario starts it again.
    private suspend fun activateRung(rangAt: Long, now: Long) {
        endedByAlarm(activations.activations(), rangAt).forEach { engine.deactivateIfUnchanged(it) }
        val definitions = scenarios.observeScenarios().first()
        val listeners = alarmListeners(definitions)
        if (listeners.isEmpty()) return
        val needs = stateNeedsOf(listeners)
        val state = if (needs.aspects.isEmpty()) SystemState() else systemState.measure(needs)
        dueAlarms(definitions, state, rangAt, now, clock.zone()).forEach { scenario ->
            val trigger = activationTrigger(scenario, state, alarmTrigger(scenario))
            engine.activate(scenario, trigger = trigger, firedAt = rangAt)
        }
    }
}
