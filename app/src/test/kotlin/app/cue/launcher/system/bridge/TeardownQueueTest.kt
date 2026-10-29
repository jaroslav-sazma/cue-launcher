package app.cue.launcher.system.bridge

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
class TeardownQueueTest {

    private val done = CopyOnWriteArrayList<String>()
    private val logged = CopyOnWriteArrayList<String>()

    private fun TestScope.appScope() = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

    private fun step(name: String, gate: CompletableDeferred<Unit>? = null): suspend () -> Unit = {
        gate?.await()
        done += name
    }

    @Test
    fun `leaving the screen mid-wipe leaves nothing half wiped`() = runTest {
        val queue = TeardownQueue(appScope()) { logged += it }
        val settingsWritten = CompletableDeferred<Unit>()
        val screen = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
        var job: Job? = null
        screen.launch {
            job = queue.run("unpair", listOf(step("settings", settingsWritten), step("associations"), step("identity")))
        }
        advanceUntilIdle()

        screen.cancel()
        job?.cancel()
        settingsWritten.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("settings", "associations", "identity"), done)
        assertTrue(job?.isCompleted == true)
    }

    @Test
    fun `a failing step does not stop the ones after it`() = runTest {
        val queue = TeardownQueue(appScope()) { logged += it }

        queue.run("turn-off", listOf(step("settings"), { error("the keystore is busy") }, step("identity")))
        advanceUntilIdle()

        assertEquals(listOf("settings", "identity"), done)
        assertTrue(logged.any { it.contains("a step failed") })
    }

    @Test
    fun `what waits for the wipe starts only once it is over, and the next wipe waits for the last`() = runTest {
        val queue = TeardownQueue(appScope()) { logged += it }
        val gate = CompletableDeferred<Unit>()
        queue.run("unpair", listOf(step("first", gate)))
        queue.run("turn-off", listOf(step("second")))
        var settled = false
        launch {
            queue.settled()
            settled = true
        }
        advanceUntilIdle()
        assertFalse(settled)
        assertTrue(done.isEmpty())

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("first", "second"), done)
        assertTrue(settled)
    }
}
