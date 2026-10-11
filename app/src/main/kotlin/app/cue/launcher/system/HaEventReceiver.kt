package app.cue.launcher.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import app.cue.launcher.di.AppGraph
import app.cue.launcher.engine.HA_EVENT_ACTION
import app.cue.launcher.engine.HA_EXTRA_CODE
import app.cue.launcher.engine.HA_EXTRA_SECRET
import app.cue.launcher.engine.haEventKeys
import app.cue.launcher.engine.haEventOf
import app.cue.launcher.engine.haEventSecretAccepted
import app.cue.launcher.engine.haExtraText

// Every extra is the sender's to choose, and the generic Bundle getter would instantiate whatever class
// a value names: an unknown Parcelable throws out of onReceive, a Serializable runs its readObject. So
// nothing but the secret is read before the secret is accepted, and then only through the typed
// getters, which cast what unparcelling already produced and leave a Parcelable or Serializable
// undecoded.
class HaEventReceiver : BroadcastReceiver() {

    @Suppress("TooGenericExceptionCaught")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != HA_EVENT_ACTION) return
        val received = try {
            intent.extras?.let { extras -> Received(extras, extras.getString(HA_EXTRA_SECRET)) }
        } catch (cause: RuntimeException) {
            Log.w(TAG, "HA event dropped — unreadable extras (${cause::class.java.simpleName})")
            return
        }
        deliverAsync(context) { graph -> deliver(graph, received) }
    }

    private suspend fun deliver(graph: AppGraph, received: Received?) {
        val expected = graph.haConfigRepository.eventSecret()
        if (received == null || !haEventSecretAccepted(expected, received.secret)) {
            val code = { received?.extras?.let { haExtraText(scalarOf(it, HA_EXTRA_CODE)) } }
            if (graph.haEventLogRepository.recordRejected(code)) {
                Log.w(TAG, "HA event rejected — pairing secret does not match")
            }
            return
        }
        val event = haEventOf(extrasOf(received.extras))
        if (event == null) {
            Log.w(TAG, "HA event without a code")
            return
        }
        Log.i(TAG, "HA event: ${event.code} (op=${event.op}, fields=${event.fields.size})")
        graph.conditionMonitor.onHaEvent(event)
    }

    private fun extrasOf(extras: Bundle): Map<String, Any?> {
        val keys = runCatching { extras.keySet().toList() }.getOrDefault(emptyList())
        return haEventKeys(keys).associateWith { key -> scalarOf(extras, key) }
    }

    // The companion app turns a digit-only value into an Int and true/false into a Boolean, and its
    // typed suffixes reach Long, Double and Float — so those are the only shapes worth asking for.
    // A getter of the wrong type logs a platform warning and answers with the default, which is why
    // the numeric ones are asked twice with different defaults before a zero is believed.
    private fun scalarOf(extras: Bundle, key: String): Any? = runCatching {
        extras.getString(key)
            ?: typed(0, 1) { extras.getInt(key, it) }
            ?: typed(false, true) { extras.getBoolean(key, it) }
            ?: typed(0L, 1L) { extras.getLong(key, it) }
            ?: typed(0.0, 1.0) { extras.getDouble(key, it) }
            ?: typed(0f, 1f) { extras.getFloat(key, it) }
    }.getOrNull()

    private inline fun <T : Any> typed(absent: T, other: T, read: (T) -> T): T? {
        val value = read(absent)
        return when {
            value != absent -> value
            read(other) == absent -> absent
            else -> null
        }
    }

    private class Received(val extras: Bundle, val secret: String?)

    private companion object {
        const val TAG = "HaEvent"
    }
}
