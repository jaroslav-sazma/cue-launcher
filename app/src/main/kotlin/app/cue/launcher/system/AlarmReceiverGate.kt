package app.cue.launcher.system

import android.app.Application
import android.util.Log
import app.cue.launcher.engine.AlarmMonitor
import app.cue.launcher.engine.ScenarioSource
import app.cue.launcher.engine.wantsAlarmReceiver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// While switched off nobody follows the alarm, so switching on first remembers the current one —
// an alarm deleted in the meantime would otherwise look like one that rang. A ring that slips in
// between is caught by the home screen's catch-up.
@SingleIn(AppScope::class)
@Inject
class AlarmReceiverGate(
    private val application: Application,
    private val scenarios: ScenarioSource,
    private val alarms: AlarmMonitor,
) {

    fun start(scope: CoroutineScope) {
        scope.launch {
            scenarios.observeScenarios()
                .map(::wantsAlarmReceiver)
                .distinctUntilChanged()
                .collect { enabled -> setReceiverEnabled(enabled) }
        }
    }

    private suspend fun setReceiverEnabled(enabled: Boolean) {
        val receiver = NextAlarmReceiver::class.java
        if (application.isReceiverEnabled(receiver) == enabled) return
        if (enabled) alarms.rememberNextAlarm { nextAlarmMillis(application) }
        application.setReceiverEnabled(receiver, enabled)
        Log.i(TAG, "Next alarm receiver ${if (enabled) "enabled" else "disabled"}")
    }

    private companion object {
        const val TAG = "AlarmGate"
    }
}
