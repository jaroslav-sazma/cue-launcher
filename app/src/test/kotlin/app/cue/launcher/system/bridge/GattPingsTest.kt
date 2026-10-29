package app.cue.launcher.system.bridge

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GattPingsTest {

    @Test
    fun `the phone pings after every subscription and every session start, but not for a session from before`() =
        runTest(UnconfinedTestDispatcher()) {
            val subscriptions = Channel<Unit>(Channel.CONFLATED)
            val sessions = MutableStateFlow(SESSIONS_BEFORE)
            val pings = mutableListOf<String>()
            val collecting = launch { gattPings(subscriptions.receiveAsFlow(), sessions).collect { pings += it } }

            assertEquals(emptyList<String>(), pings)

            subscriptions.send(Unit)
            sessions.value = SESSIONS_BEFORE + 1
            subscriptions.send(Unit)
            sessions.value = SESSIONS_BEFORE + 2

            assertEquals(listOf("subscription", "session start", "subscription", "session start"), pings)
            collecting.cancel()
        }

    private companion object {
        const val SESSIONS_BEFORE = 3L
    }
}
