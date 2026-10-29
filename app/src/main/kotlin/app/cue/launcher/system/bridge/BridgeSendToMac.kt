package app.cue.launcher.system.bridge

import android.app.Application
import app.cue.launcher.data.bridge.BridgeConfigRepository
import app.cue.launcher.system.localNetworkGranted
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.io.IOException

enum class SendToMacOutcome { SENT, NOT_PAIRED, TOO_LONG, NO_LOCAL_NETWORK, UNREACHABLE }

// The text selection's Send to Mac. The home screen is usually stopped behind it, so there is no
// session: a connection of its own carries hello, the one clipboard-set and bye, over TCP or, where
// only Bluetooth reaches the Mac, over the window's L2CAP channel. A live session is used when there
// is one, because a second connection would replace it on the Mac.
@SingleIn(AppScope::class)
@Inject
class BridgeSendToMac(
    private val application: Application,
    private val config: BridgeConfigRepository,
    private val presence: BridgePresence,
    private val tls: BridgeTlsClient,
    private val link: BridgeSessionLink,
    private val carriers: BridgeCarriers,
) {

    private val oneShot = OneShotPush("send-to-mac")

    suspend fun send(text: String): SendToMacOutcome {
        val message = BridgeMessage.ClipboardSet(text, sensitive = false, announce = true)
        val settings = config.settings()
        val hello = sessionHello(settings)
        val pin = settings.pairing?.macFingerprint?.takeIf { settings.enabled }
        return when {
            pin == null -> SendToMacOutcome.NOT_PAIRED
            !fitsClipboard(text) -> SendToMacOutcome.TOO_LONG
            link.sendOnSession(message) -> SendToMacOutcome.SENT
            else -> sendOverCarriers(
                carriers = carriers,
                tcpAllowed = localNetworkGranted(application),
                tcp = {
                    presence.endpoint().first()?.let { endpoint ->
                        oneShot.push(Carrier.TCP, hello, message, transferMillis = null) {
                            tls.connect(endpoint.host, endpoint.port, pin.hexToByteArray())
                        }
                    }
                },
                l2cap = { route ->
                    oneShot.push(Carrier.L2CAP, hello, message, l2capTransferMillis(text.encodeToByteArray().size)) {
                        openL2cap(route, pin)
                    }
                },
            )
        }
    }

    private suspend fun openL2cap(route: L2capRoute, pin: String): BridgeConnection {
        val pairing = config.openPairing()?.takeIf { it.macFingerprint == pin }
            ?: throw IOException("the pairing keys cannot be read")
        return try {
            connectL2cap(route, pairing.bleKey)
        } finally {
            pairing.bleKey.fill(0)
        }
    }
}

internal const val ONE_SHOT_TIMEOUT_MS = 5_000L
private const val TRANSFER_BASE_MS = 5_000L
private const val TRANSFER_MS_PER_STEP = 1_000L
private const val TRANSFER_STEP_BYTES = 4_096
private const val TRANSFER_MAX_MS = 35_000L

// Bluetooth moves tens of kB/s, so a long clip outlasts any flat cap.
internal fun l2capTransferMillis(textBytes: Int): Long {
    val steps = (textBytes + TRANSFER_STEP_BYTES - 1) / TRANSFER_STEP_BYTES
    return (TRANSFER_BASE_MS + steps * TRANSFER_MS_PER_STEP).coerceAtMost(TRANSFER_MAX_MS)
}

// The timeout may strike right after connect returned, which then drops the value: the connection is
// kept aside so that it is closed all the same. Null when either budget ran out.
internal suspend fun <C : Closeable, T : Any> connectThenTransfer(
    transferMillis: Long,
    connect: suspend () -> C,
    transfer: suspend (C) -> T,
): T? {
    var opened: C? = null
    val connection = withTimeoutOrNull(ONE_SHOT_TIMEOUT_MS) { connect().also { opened = it } }
    if (connection == null) {
        opened?.close()
        return null
    }
    return try {
        withTimeoutOrNull(transferMillis) { transfer(connection) }
    } finally {
        connection.close()
    }
}

// Null from a carrier: it has no route now. TCP needs the local network permission and L2CAP does not,
// so without the permission Bluetooth still carries the clip, and the permission is named only when
// it did not. UNREACHABLE means neither carrier could.
internal suspend fun sendOverCarriers(
    carriers: BridgeCarriers,
    tcpAllowed: Boolean,
    tcp: suspend () -> Unit?,
    l2cap: suspend (L2capRoute) -> Unit,
): SendToMacOutcome {
    val carried = try {
        carriers.dial(tcp = { if (tcpAllowed) tcp() else null }, l2cap = l2cap)
    } catch (_: IOException) {
        null
    }
    return when {
        carried != null -> SendToMacOutcome.SENT
        !tcpAllowed -> SendToMacOutcome.NO_LOCAL_NETWORK
        else -> SendToMacOutcome.UNREACHABLE
    }
}
