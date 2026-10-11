package app.cue.launcher.system

import android.app.Application
import android.util.Log
import app.cue.launcher.engine.ScenarioSource
import app.cue.launcher.engine.wantsDisconnectReceiver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// The manifest receiver wakes the process on every ACL disconnect of a paired device, so with no
// listening scenario it is switched off.
@SingleIn(AppScope::class)
@Inject
class BluetoothReceiverGate(
    private val application: Application,
    private val scenarios: ScenarioSource,
) {

    fun start(scope: CoroutineScope) {
        scope.launch {
            scenarios.observeScenarios()
                .map(::wantsDisconnectReceiver)
                .distinctUntilChanged()
                .collect(::setReceiverEnabled)
        }
    }

    private fun setReceiverEnabled(enabled: Boolean) {
        val receiver = BluetoothDisconnectReceiver::class.java
        if (application.isReceiverEnabled(receiver) == enabled) return
        application.setReceiverEnabled(receiver, enabled)
        Log.i(TAG, "Bluetooth disconnect receiver ${if (enabled) "enabled" else "disabled"}")
    }

    private companion object {
        const val TAG = "BluetoothGate"
    }
}
