package app.cue.launcher.system

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import app.cue.launcher.engine.StateAspect
import app.cue.launcher.engine.StateProvider
import app.cue.launcher.engine.SystemState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@SingleIn(AppScope::class)
@Inject
class PowerConditions(private val application: Application) : StateProvider {

    override val aspect: StateAspect = StateAspect.POWER

    override fun observe(): Flow<SystemState> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(measure())
            }
        }
        application.registerReceiver(receiver, POWER_CHANGES, Context.RECEIVER_NOT_EXPORTED)
        send(measure())
        awaitClose { application.unregisterReceiver(receiver) }
    }

    private fun measure(): SystemState {
        val battery = application.registerReceiver(null, BATTERY_STATE)
        val charging = battery?.let {
            chargingFrom(
                status = it.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN),
                plugged = it.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
            )
        }
        return SystemState(charging = charging)
    }

    private companion object {
        val POWER_CHANGES = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }

        val BATTERY_STATE = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    }
}

internal fun chargingFrom(status: Int, plugged: Int): Boolean =
    plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING
