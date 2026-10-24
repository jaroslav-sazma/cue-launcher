package app.cue.launcher.system

import android.app.Application
import android.util.Log
import app.cue.launcher.engine.ActivationStore
import app.cue.launcher.engine.ScenarioSource
import app.cue.launcher.engine.wantsDisconnectReceiver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// The manifest receiver wakes the process on every ACL disconnect of a paired device, so it is
// switched off while no scenario starts or ends on one and no running tile waits for one.
@SingleIn(AppScope::class)
@Inject
class BluetoothReceiverGate(
    private val application: Application,
    private val scenarios: ScenarioSource,
    private val activations: ActivationStore,
) {

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(scenarios.observeScenarios(), activations.observeActivations(), ::wantsDisconnectReceiver)
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
