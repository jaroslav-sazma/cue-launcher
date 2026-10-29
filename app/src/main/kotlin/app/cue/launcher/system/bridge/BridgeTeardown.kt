package app.cue.launcher.system.bridge

import app.cue.launcher.data.bridge.BridgeConfigRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Turning the bridge off and unpairing outlive the screen they were asked on: a wipe cut short would
// leave the TLS identity or an association for the next pairing to pick up. A pairing, a search or
// switching the bridge back on waits for the wipe under way.
@SingleIn(AppScope::class)
@Inject
class BridgeTeardown(
    processScope: CoroutineScope,
    private val config: BridgeConfigRepository,
    private val companion: BridgeCompanion,
    private val identity: BridgeIdentity,
    private val macLock: BridgeMacLock,
) {

    private val queue = TeardownQueue(processScope)

    fun turnOff(): Job = wipe("turn-off", config::disable)

    fun unpair(): Job = wipe("unpair", config::unpair)

    suspend fun settled() = queue.settled()

    private fun wipe(name: String, settings: suspend () -> Unit): Job = queue.run(
        name,
        listOf(
            settings,
            { macLock.forget() },
            { withContext(Dispatchers.IO) { companion.forgetAll() } },
            { withContext(Dispatchers.IO) { identity.forget() } },
        ),
    )
}

// Each teardown starts once the one before it has ended, runs every step even when one fails, and
// cancelling its job or its caller's scope stops none of them.
internal class TeardownQueue(private val scope: CoroutineScope, private val log: (String) -> Unit = BridgeLog::info) {

    private var last: Job? = null

    fun run(name: String, steps: List<suspend () -> Unit>): Job = synchronized(this) {
        val previous = last
        scope.launch {
            withContext(NonCancellable) {
                previous?.join()
                steps.forEach { step -> attempt(name, step) }
                log("$name: bridge wiped")
            }
        }.also { last = it }
    }

    suspend fun settled() {
        synchronized(this) { last }?.join()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun attempt(name: String, step: suspend () -> Unit) {
        try {
            step()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            log("$name: a step failed: ${failure.javaClass.simpleName}")
        }
    }
}
