package app.cue.launcher.system

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

fun nextAlarmMillis(context: Context): Long? =
    context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime

fun nextAlarmChanges(context: Context): Flow<Long?> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(received: Context?, intent: Intent?) {
            trySend(nextAlarmMillis(context))
        }
    }
    context.registerReceiver(receiver, NEXT_ALARM_CHANGES, Context.RECEIVER_NOT_EXPORTED)
    send(nextAlarmMillis(context))
    awaitClose { context.unregisterReceiver(receiver) }
}

@Composable
fun rememberNextAlarmMillis(): Long? {
    val context = LocalContext.current
    val initial = remember(context) { nextAlarmMillis(context) }
    val millis by remember(context) { nextAlarmChanges(context) }
        .collectAsStateWithLifecycle(initialValue = initial)
    return millis
}

private val NEXT_ALARM_CHANGES = IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
