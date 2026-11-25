package app.cue.launcher.system.safe

import app.cue.launcher.engine.StateAspect
import app.cue.launcher.engine.StateProvider
import app.cue.launcher.engine.SystemState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

// Reads only the session in memory, so it registers nothing and builds nothing of the vault. A
// session past its limit reads as locked even where nothing has locked it yet, as on an event in the
// background. The wait for the limit lives only while something collects — the home screen, or an
// event's reading, which stays shared for a few seconds after the last event — so that reading turns
// false at the limit instead of outliving it, and nothing waits once it is let go.
@SingleIn(AppScope::class)
@Inject
class SafeConditions(private val host: SafeHost, private val clock: SafeClock) : StateProvider {

    override val aspect: StateAspect = StateAspect.VAULT

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<SystemState> =
        host.session
            .flatMapLatest(::readings)
            .distinctUntilChanged()

    // The elapsed clock counts deep sleep and delay() does not, so the wait ends at the limit or later,
    // never before it; the expiry is read from the clock again at every pass all the same.
    private fun readings(session: SafeSession?): Flow<SystemState> = flow {
        while (true) {
            val now = clock.elapsedMillis()
            val unlocked = vaultUnlocked(session, now)
            emit(SystemState(vaultUnlocked = unlocked))
            if (session == null || !unlocked) break
            delay(session.remainingMillis(now))
        }
    }
}
