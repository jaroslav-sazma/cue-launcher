package app.cue.launcher.system.bridge

import android.bluetooth.BluetoothGatt

// The held link idles at the slowest connection interval the stack offers once it is subscribed;
// an L2CAP channel on it carries a session, so it gets the balanced interval while one is open.
// Never called under the link's own lock: a request takes that lock to reach the connection.
internal class GattPacing(
    private val request: (Int) -> Boolean,
    private val log: (String) -> Unit = {},
) {

    private var subscribed = false

    private val channels = HashSet<Any>()

    private var applied: Int? = null

    @Synchronized
    fun subscribed() {
        subscribed = true
        apply()
    }

    @Synchronized
    fun disconnected() {
        subscribed = false
        channels.clear()
        applied = null
    }

    @Synchronized
    fun channelOpened(): Any = Any().also {
        channels += it
        apply()
    }

    @Synchronized
    fun channelClosed(channel: Any) {
        if (channels.remove(channel)) apply()
    }

    private fun apply() {
        if (!subscribed) return
        val wanted = if (channels.isEmpty()) LOW_POWER else BALANCED
        if (wanted == applied) return
        if (request(wanted)) {
            applied = wanted
            log("gatt connection priority ${if (wanted == LOW_POWER) "low-power" else "balanced"}")
        } else {
            log("gatt connection priority request refused")
        }
    }

    companion object {
        const val LOW_POWER = BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER
        const val BALANCED = BluetoothGatt.CONNECTION_PRIORITY_BALANCED
    }
}
