package app.cue.launcher.system.ha

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// count tells two handshakes that announced the same version apart: each one is also proof that a
// socket gets through again.
data class HaHandshake(val instance: String, val version: String, val count: Int)

@SingleIn(AppScope::class)
@Inject
class HaInstanceVersion {

    private val announced = MutableStateFlow<HaHandshake?>(null)

    val seen: StateFlow<HaHandshake?> = announced.asStateFlow()

    fun saw(instance: String, version: String) {
        announced.update { HaHandshake(instance, version, (it?.count ?: 0) + 1) }
    }
}
