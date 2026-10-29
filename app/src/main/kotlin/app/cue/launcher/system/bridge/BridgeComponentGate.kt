package app.cue.launcher.system.bridge

import android.app.Application
import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.system.isGatedComponentEnabled
import app.cue.launcher.system.setGatedComponentEnabled
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// Send to Mac sits in every app's text selection menu, and the companion service is what the system
// binds whenever the Mac comes near, so both exist only while the bridge is on and paired. Following
// the stored switch, rather than setting the components where the pairing is made or wiped, also
// repairs a state a process death left behind in between.
@SingleIn(AppScope::class)
@Inject
class BridgeComponentGate(
    private val application: Application,
    private val settings: SettingsRepository,
) {

    fun start(scope: CoroutineScope) {
        scope.launch {
            settings.settings
                .map { it.bridge.enabled && it.bridge.pairing != null }
                .distinctUntilChanged()
                .collect(::setEnabled)
        }
    }

    internal companion object {
        val BRIDGE_GATED_COMPONENTS: List<Class<*>> =
            listOf(ProcessTextActivity::class.java, BridgeCompanionService::class.java)
    }

    private fun setEnabled(enabled: Boolean) {
        BRIDGE_GATED_COMPONENTS.forEach { component ->
            if (application.isGatedComponentEnabled(component) != enabled) {
                application.setGatedComponentEnabled(component, enabled)
                BridgeLog.info("${component.simpleName} ${if (enabled) "enabled" else "disabled"}")
            }
        }
    }
}
