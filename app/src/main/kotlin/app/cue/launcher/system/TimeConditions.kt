package app.cue.launcher.system

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.engine.LocalClock
import app.cue.launcher.engine.ScenarioSource
import app.cue.launcher.engine.StateAspect
import app.cue.launcher.engine.StateProvider
import app.cue.launcher.engine.SystemState
import app.cue.launcher.engine.timeWindowWaitMillis
import app.cue.launcher.engine.timeWindowsOf
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

@SingleIn(AppScope::class)
@Inject
class TimeConditions(
    private val application: Application,
    private val scenarios: ScenarioSource,
    private val clock: LocalClock,
) : StateProvider {

    override val aspect: StateAspect = StateAspect.CLOCK

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<SystemState> =
        combine(scenarios.observeScenarios(), clockChanges()) { scenarios, revision ->

            Windows(timeWindowsOf(scenarios), revision)
        }
            .distinctUntilChanged()
            .flatMapLatest { windows -> measurements(windows.windows) }

    private fun measurements(windows: List<Condition.TimeWindow>): Flow<SystemState> = flow {
        while (true) {
            val reading = clock.reading()
            emit(SystemState(clock = reading.clock))
            val wait = timeWindowWaitMillis(windows, reading) ?: break
            delay(wait)
        }
    }

    private fun clockChanges(): Flow<Int> = callbackFlow {
        var revision = 0
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(++revision)
            }
        }
        application.registerReceiver(receiver, CLOCK_CHANGES, Context.RECEIVER_NOT_EXPORTED)
        send(revision)
        awaitClose { application.unregisterReceiver(receiver) }
    }

    private data class Windows(val windows: List<Condition.TimeWindow>, val revision: Int)

    private companion object {
        val CLOCK_CHANGES = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
    }
}
