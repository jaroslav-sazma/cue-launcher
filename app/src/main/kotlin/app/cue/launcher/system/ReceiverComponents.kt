package app.cue.launcher.system

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import app.cue.launcher.CueApplication
import app.cue.launcher.di.AppGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// goAsync() keeps the process alive only until finish(), and the work in between holds up the next
// broadcasts. The background broadcasts these receivers get would tolerate more, but the budget stays
// under the 10 s goAsync()'s documentation sets as the general rule, and finish() runs even when the
// work hangs.
@Suppress("TooGenericExceptionCaught")
internal fun BroadcastReceiver.deliverAsync(context: Context, block: suspend (AppGraph) -> Unit) {
    val graph = (context.applicationContext as? CueApplication)?.graph ?: return
    val pending = goAsync()
    val receiver = javaClass.simpleName
    graph.processScope.launch {
        try {
            if (withTimeoutOrNull(BROADCAST_BUDGET_MS) { block(graph) } == null) {
                Log.w(TAG, "$receiver gave up after $BROADCAST_BUDGET_MS ms")
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            logUnexpected(TAG, "$receiver failed", cause)
        } finally {
            pending.finish()
        }
    }
}

private const val BROADCAST_BUDGET_MS = 8_000L

private const val TAG = "Receivers"

// The component setting persists across process death and reboots, so a disabled receiver keeps
// the process asleep until a scenario turns it back on.
internal fun Application.isReceiverEnabled(receiver: Class<out BroadcastReceiver>): Boolean =
    // DEFAULT means the manifest value, which is enabled.
    packageManager.getComponentEnabledSetting(ComponentName(this, receiver)) !=
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED

internal fun Application.setReceiverEnabled(receiver: Class<out BroadcastReceiver>, enabled: Boolean) {
    val state = when {
        enabled -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }
    packageManager.setComponentEnabledSetting(ComponentName(this, receiver), state, PackageManager.DONT_KILL_APP)
}

// For a component the manifest declares disabled: there DEFAULT means off, and going back to it
// leaves nothing behind.
internal fun Application.isGatedComponentEnabled(component: Class<*>): Boolean =
    packageManager.getComponentEnabledSetting(ComponentName(this, component)) ==
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED

internal fun Application.setGatedComponentEnabled(component: Class<*>, enabled: Boolean) {
    val state = when {
        enabled -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else -> PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
    }
    packageManager.setComponentEnabledSetting(ComponentName(this, component), state, PackageManager.DONT_KILL_APP)
}
