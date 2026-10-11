package app.cue.launcher.system

import android.app.Application
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log
import app.cue.launcher.data.scenario.RingerModeState
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
class RingerConditions(private val application: Application) : StateProvider {

    override val aspect: StateAspect = StateAspect.RINGER

    override fun observe(): Flow<SystemState> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(measure())
            }
        }
        application.registerReceiver(receiver, SOUND_CHANGES, Context.RECEIVER_NOT_EXPORTED)
        send(measure())
        awaitClose { application.unregisterReceiver(receiver) }
    }

    private fun measure(): SystemState = SystemState(
        ringerMode = application.getSystemService(AudioManager::class.java)
            ?.let { ringerModeFrom(it.ringerMode) },
        doNotDisturb = application.getSystemService(NotificationManager::class.java)
            ?.let { dndFrom(currentInterruptionFilter(it)) },
    )

    private fun currentInterruptionFilter(notifications: NotificationManager): Int = try {
        notifications.currentInterruptionFilter
    } catch (denied: SecurityException) {
        Log.w(TAG, "The Do Not Disturb state cannot be read", denied)
        NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    private companion object {
        const val TAG = "RingerConditions"

        val SOUND_CHANGES = IntentFilter().apply {
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
    }
}

internal fun ringerModeFrom(mode: Int): RingerModeState? = when (mode) {
    AudioManager.RINGER_MODE_SILENT -> RingerModeState.SILENT
    AudioManager.RINGER_MODE_VIBRATE -> RingerModeState.VIBRATE
    AudioManager.RINGER_MODE_NORMAL -> RingerModeState.NORMAL
    else -> null
}

internal fun dndFrom(filter: Int): Boolean? = when (filter) {
    NotificationManager.INTERRUPTION_FILTER_ALL -> false
    NotificationManager.INTERRUPTION_FILTER_PRIORITY,
    NotificationManager.INTERRUPTION_FILTER_NONE,
    NotificationManager.INTERRUPTION_FILTER_ALARMS,
    -> true

    else -> null
}
