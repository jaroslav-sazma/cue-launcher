package app.cue.launcher.system.bridge

import android.app.Application
import app.cue.launcher.data.bridge.BridgeConfigRepository
import app.cue.launcher.data.bridge.OpenedPairing
import app.cue.launcher.system.localNetworkGranted
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicReference

enum class LockMacOutcome { SENT, NOT_PAIRED, NO_LOCAL_NETWORK, UNREACHABLE }

internal fun interface GattSignalRoute {

    suspend fun send(signal: BleSignal): Boolean
}

// The Lock Mac action. The lock goes over the window's GATT link while it is subscribed, otherwise on
// the session, and without one on a connection of its own, like Send to Mac. SENT says only that the
// frame left: the Mac answers with a lock-state, which updates BridgeMacLock the way it came.
@Suppress("LongParameterList")
@SingleIn(AppScope::class)
@Inject
class BridgeLockMac(
    private val application: Application,
    private val config: BridgeConfigRepository,
    private val presence: BridgePresence,
    private val tls: BridgeTlsClient,
    private val link: BridgeSessionLink,
    private val carriers: BridgeCarriers,
    private val guard: BridgeReplayGuard,
    private val macLock: BridgeMacLock,
) {

    private val gatt = AtomicReference<GattSignalRoute?>(null)

    internal fun attach(route: GattSignalRoute) {
        gatt.set(route)
    }

    internal fun detach(route: GattSignalRoute) {
        gatt.updateAndGet { current -> current?.takeUnless { it === route } }
    }

    suspend fun lock(): LockMacOutcome {
        val settings = config.settings()
        val pin = settings.pairing?.macFingerprint?.takeIf { settings.enabled } ?: return LockMacOutcome.NOT_PAIRED
        return lockOver(
            gatt = { gatt.get()?.send(BleSignal.Lock) == true },
            session = { link.send(BleSignal.Lock) },
            oneShot = { push(pin) },
        )
    }

    // One frame for both carriers: should the first have delivered it after all, the Mac's replay
    // window drops the second copy.
    private suspend fun push(pin: String): LockMacOutcome {
        val pairing = config.openPairing()?.takeIf { it.macFingerprint == pin } ?: return LockMacOutcome.UNREACHABLE
        return try {
            config.reserveOutgoingSeq(pairing)?.let { seq -> push(pin, pairing, seq) } ?: LockMacOutcome.UNREACHABLE
        } finally {
            synchronized(pairing) { pairing.bleKey.fill(0) }
        }
    }

    private suspend fun push(pin: String, pairing: OpenedPairing, seq: Long): LockMacOutcome {
        val plaintext = encodeBleSignal(BleSignal.Lock)
        val frame = BleFrameCodec.seal(
            pairing.bleKey, BleDirection.PHONE_TO_MAC, BleFrameType.LOCK.code, seq, plaintext,
        )
        BridgeLog.event("send signal ${BleFrameType.LOCK.wireName} seq=$seq via=$CONNECTION")
        val message = BridgeMessage.Signal(encodeBase64(frame))
        val hello = sessionHello(config.settings())
        val oneShot = OneShotPush(CONNECTION) { inbound -> drained(pairing, inbound) }
        val outcome = sendOverCarriers(
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
                oneShot.push(Carrier.L2CAP, hello, message, l2capTransferMillis(frame.size)) {
                    connectL2cap(route, pairing.bleKey)
                }
            },
        )
        return when (outcome) {
            SendToMacOutcome.SENT -> LockMacOutcome.SENT
            SendToMacOutcome.NO_LOCAL_NETWORK -> LockMacOutcome.NO_LOCAL_NETWORK
            else -> LockMacOutcome.UNREACHABLE
        }
    }

    // Only a lock-state is opened here: any other frame keeps its seq unspent for the session that
    // will act on it. The type byte is the AEAD's additional data, so reading it first is safe.
    private suspend fun drained(pairing: OpenedPairing, inbound: BridgeInbound) {
        val frame = lockStateFrame(inbound) ?: return
        when (val opened = guard.open(pairing, frame)) {
            is BleOpened.Accepted -> (inboundSignal(opened.frame, CONNECTION) as? BleSignal.LockState)
                ?.let { macLock.received(pairing, opened.frame.seq, it.locked) }
            else -> logDropped(opened, CONNECTION)
        }
    }

    private companion object {
        const val CONNECTION = "lock-mac"
    }
}

internal suspend fun lockOver(
    gatt: suspend () -> Boolean,
    session: suspend () -> Boolean,
    oneShot: suspend () -> LockMacOutcome,
): LockMacOutcome = when {
    gatt() -> LockMacOutcome.SENT
    session() -> LockMacOutcome.SENT
    else -> oneShot()
}

internal fun lockStateFrame(inbound: BridgeInbound): ByteArray? {
    val encoded = ((inbound as? BridgeInbound.Message)?.message as? BridgeMessage.Signal)?.frame ?: return null
    return runCatching { decodeBase64(encoded) }.getOrNull()
        ?.takeIf { it.firstOrNull()?.toInt() == BleFrameType.LOCK_STATE.code }
}
