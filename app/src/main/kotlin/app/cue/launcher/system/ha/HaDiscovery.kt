package app.cue.launcher.system.ha

import android.app.Application
import android.net.nsd.DiscoveryRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.PatternMatcher
import android.util.Log
import app.cue.launcher.data.ha.HA_SERVICE_TYPE
import app.cue.launcher.data.ha.HA_TXT_NAME
import app.cue.launcher.data.ha.HA_TXT_UUID
import app.cue.launcher.data.ha.HaInstance
import app.cue.launcher.data.ha.haInstanceOf
import app.cue.launcher.engine.WallClock
import app.cue.launcher.system.localNetworkGranted
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

// Android 17 hands local network access out per service: an instance the user picked in the system
// picker can be reached without the blanket ACCESS_LOCAL_NETWORK permission. The same discovery
// answers "is my instance on this network", which is what the SSID list was needed for.
@SingleIn(AppScope::class)
@Inject
class HaDiscovery(
    private val application: Application,
    private val clock: WallClock,
) {

    private val seen = ConcurrentHashMap<String, Seen>()

    fun localNetworkGranted(): Boolean = localNetworkGranted(application)

    suspend fun pick(): HaInstance? {
        val request = request(DiscoveryRequest.FLAG_SHOW_PICKER) { setDisplayNameAttribute(HA_TXT_NAME) }
        val found = request?.let { withTimeoutOrNull(PICKER_TIMEOUT_MILLIS) { resolve(it) } }
        return found?.let(::instanceOf)?.also { remember(it.uuid, visible = true) }
    }

    // Only services the user already approved are reported, so this cannot be used to sniff the
    // network — it answers one question about one instance the user picked themselves.
    suspend fun visible(uuid: String): Boolean {
        remembered(uuid)?.let { return it }
        val request = request(DiscoveryRequest.FLAG_USER_APPROVED_ONLY) {
            setAttributeFilters(mapOf(HA_TXT_UUID to PatternMatcher(uuid, PatternMatcher.PATTERN_LITERAL)))
        }
        val found = request?.let { withTimeoutOrNull(PRESENCE_TIMEOUT_MILLIS) { resolve(it) } }
        return (found != null).also { remember(uuid, it) }
    }

    private fun remembered(uuid: String): Boolean? = when {
        uuid.isBlank() -> false
        else -> seen[uuid]?.takeIf { clock.nowMillis() - it.atMillis in 0..PRESENCE_FRESH_MILLIS }?.visible
    }

    fun forget() = seen.clear()

    private fun remember(uuid: String, visible: Boolean) {
        if (uuid.isBlank()) return
        seen[uuid] = Seen(visible, clock.nowMillis())
    }

    @Suppress("TooGenericExceptionCaught")
    private inline fun request(
        flags: Long,
        build: DiscoveryRequest.Builder.() -> Unit,
    ): DiscoveryRequest? = try {
        DiscoveryRequest.Builder(HA_SERVICE_TYPE).setFlags(flags).apply(build).build()
    } catch (cause: Exception) {
        Log.w(TAG, "Discovery request was refused (${cause::class.java.simpleName})")
        null
    }

    // The answer is a snapshot: the callback is dropped as soon as the service resolves, times out or
    // the caller goes away, so nothing keeps listening on the network in the background.
    private suspend fun resolve(request: DiscoveryRequest): NsdServiceInfo? {
        val manager = application.getSystemService(NsdManager::class.java) ?: return null
        var registered: NsdManager.ServiceInfoCallback? = null
        return try {
            suspendCancellableCoroutine { continuation ->
                val callback = object : NsdManager.ServiceInfoCallback {
                    private var answered = false

                    override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                        if (answered) return
                        answered = true
                        continuation.resume(serviceInfo)
                    }

                    override fun onServiceLost() = Unit

                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                        if (answered) return
                        answered = true
                        Log.i(TAG, "Discovery did not start: $errorCode")
                        continuation.resume(null)
                    }

                    override fun onServiceInfoCallbackUnregistered() = Unit
                }
                runCatching { manager.registerServiceInfoCallback(request, DISPATCHER, callback) }
                    .onSuccess { registered = callback }
                    .onFailure { cause ->
                        Log.w(TAG, "Discovery was refused (${cause::class.java.simpleName})")
                        continuation.resume(null)
                    }
            }
        } finally {
            registered?.let { runCatching { manager.unregisterServiceInfoCallback(it) } }
        }
    }

    private fun instanceOf(info: NsdServiceInfo): HaInstance = haInstanceOf(
        serviceName = info.serviceName.orEmpty(),
        attributes = info.attributes.orEmpty(),
        host = info.hostname ?: info.hostAddresses.filterIsInstance<Inet4Address>()
            .firstOrNull()?.hostAddress.orEmpty(),
        port = info.port,
    )

    private data class Seen(val visible: Boolean, val atMillis: Long)

    private companion object {

        val DISPATCHER = Dispatchers.IO.asExecutor()

        const val PICKER_TIMEOUT_MILLIS = 120_000L

        // A responder on the same network answers in a fraction of this; the rest of the wait only
        // ever confirms that nothing is going to answer, and it sits in front of the first call of
        // every scenario that reads a state. The answer is not remembered for longer than the half
        // minute below on purpose: without the location permission every Wi-Fi looks the same to the
        // launcher, so a stale "not at home" would keep the home address unused after coming back.
        const val PRESENCE_TIMEOUT_MILLIS = 1_200L

        const val PRESENCE_FRESH_MILLIS = 30_000L

        const val TAG = "HaDiscovery"
    }
}
