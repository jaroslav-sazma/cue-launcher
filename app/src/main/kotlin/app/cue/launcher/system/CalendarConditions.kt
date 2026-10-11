package app.cue.launcher.system

import app.cue.launcher.data.calendar.CalendarAccess
import app.cue.launcher.data.calendar.CalendarRead
import app.cue.launcher.engine.StateAspect
import app.cue.launcher.engine.StateProvider
import app.cue.launcher.engine.SystemState
import app.cue.launcher.engine.WallClock
import app.cue.launcher.engine.calendarEdgeWaitMillis
import app.cue.launcher.engine.calendarEventOngoing
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

@SingleIn(AppScope::class)
@Inject
class CalendarConditions(
    private val calendar: CalendarRepository,
    private val clock: WallClock,
) : StateProvider {

    override val aspect: StateAspect = StateAspect.CALENDAR

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<SystemState> = calendar.observeEvents().flatMapLatest(::readings)

    private fun readings(read: CalendarRead): Flow<SystemState> = flow {
        while (true) {
            val now = clock.nowMillis()
            emit(stateOf(read, now))
            val wait = calendarEdgeWaitMillis(read.events, now) ?: break
            delay(wait)
        }
    }

    private fun stateOf(read: CalendarRead, nowMillis: Long): SystemState = SystemState(
        calendarEvent = when (read.access) {
            CalendarAccess.GRANTED -> calendarEventOngoing(read.events, nowMillis)
            CalendarAccess.DENIED, CalendarAccess.UNAVAILABLE -> null
        },
    )
}
