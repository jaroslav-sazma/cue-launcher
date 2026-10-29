package app.cue.launcher.system.bridge

import android.app.Application
import app.cue.launcher.data.bridge.BridgeConfigRepository
import app.cue.launcher.data.bridge.OpenedPairing
import app.cue.launcher.system.localNetworkGranted
import app.cue.launcher.system.notifications.NotificationListenerLink
import app.cue.launcher.system.notifications.NotificationReplySender
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLException

enum class BridgeLinkState {
    IDLE,
    CONNECTING,
    CONNECTED,
    UNREACHABLE,
    REJECTED,
    VERSION_MISMATCH,
    REPLACED,
    NO_LOCAL_NETWORK,
    KEY_UNAVAILABLE,
}

private data class SessionTarget(val endpoint: BridgeEndpoint, val pin: String, val clipboardFromMac: Boolean)

private class ActiveSession(
    val connection: BridgeConnection,
    val pairing: OpenedPairing,
    val dispatch: suspend (BleSignal) -> Unit,
    val touch: () -> Unit,
)

// One session connection to the paired Mac while it is present. run() lives in the foreground
// session, so that link exists only while the home screen is started, and a reconnect is the
// catch-up: hello, then the phone's active-set, which the Mac diffs its mirror against. Without the
// home screen the companion window summons a short visit instead, which closes once it goes quiet.
@Suppress("LongParameterList", "TooManyFunctions")
@SingleIn(AppScope::class)
@Inject
class BridgeSessionLink(
    private val application: Application,
    private val config: BridgeConfigRepository,
    private val presence: BridgePresence,
    private val tls: BridgeTlsClient,
    private val listener: NotificationListenerLink,
    private val icons: BridgeAppIcons,
    private val replies: NotificationReplySender,
    private val clipboard: IncomingClipboard,
    private val guard: BridgeReplayGuard,
    private val locator: BridgeLocator,
    private val carriers: BridgeCarriers,
    private val rules: MirrorRules,
    private val macLock: BridgeMacLock,
) {

    private val mutableState = MutableStateFlow(BridgeLinkState.IDLE)

    val state: StateFlow<BridgeLinkState> = mutableState.asStateFlow()

    private val started = MutableStateFlow(0L)

    internal val sessionsStarted: StateFlow<Long> = started.asStateFlow()

    private val active = AtomicReference<ActiveSession?>(null)

    private val visitLock = Any()

    private val sendLock = Mutex()

    @Volatile
    private var attended = false

    private var visiting: Job? = null

    // What wakes the home screen link that a newer connection replaced: the next summon.
    private val retries = Channel<Unit>(Channel.CONFLATED)

    internal val hasSession: Boolean get() = active.get() != null

    // The listener is held for the mirror while there is a Mac to reach, and released with the run.
    // The Mac learns the clipboard switch from a session's hello, so a change opens a new session.
    suspend fun run() {
        try {
            combine(presence.endpoint(), config.settings) { endpoint, settings ->
                val pin = settings.pairing?.macFingerprint
                if (endpoint != null && pin != null) SessionTarget(endpoint, pin, settings.clipboardFromMac) else null
            }.distinctUntilChanged().collectLatest { target ->
                if (target == null) {
                    mutableState.value = BridgeLinkState.IDLE
                } else {
                    attend(target)
                }
            }
        } finally {
            mutableState.value = BridgeLinkState.IDLE
        }
    }

    // A visit still open would be replaced on the Mac by this link's connection, so it goes first;
    // while the home screen keeps the link, a summon only wakes a link that was replaced.
    private suspend fun attend(target: SessionTarget) {
        val visit = synchronized(visitLock) {
            attended = true
            visiting.also { visiting = null }
        }
        try {
            visit?.cancelAndJoin()
            listener.holdForBridge { maintain(target, idleClose = false) }
        } finally {
            attended = false
        }
    }

    // The block the seq comes from is on disk before the frame is written, so no seq is ever used
    // twice under one key. A session that ended meanwhile has its key wiped under the same lock, so it
    // is never sealed with. One mutex spans reserve, seal and write, so this carrier never puts its
    // own frames on the wire out of seq order; only the Mac's window absorbs the other carrier's.
    internal suspend fun send(signal: BleSignal): Boolean = sendLock.withLock {
        val session = active.get()
        val seq = session?.let { config.reserveOutgoingSeq(it.pairing) }
        val frame = if (session != null && seq != null) seal(session, signal, seq) else null
        if (session == null || frame == null) return false
        return withContext(Dispatchers.IO) {
            try {
                session.connection.send(BridgeMessage.Signal(encodeBase64(frame)))
                session.touch()
                BridgeLog.event("send signal ${signal.type.wireName} seq=$seq k=${signalKeyHash(signal) ?: "-"}")
                true
            } catch (_: IOException) {
                false
            }
        }
    }

    // False when no session is up, so the caller can reach the Mac another way.
    internal suspend fun sendOnSession(message: BridgeMessage): Boolean {
        val session = active.get() ?: return false
        return withContext(Dispatchers.IO) {
            try {
                session.connection.send(message)
                session.touch()
                BridgeLog.sent(SessionConversation.CONNECTION, message)
                true
            } catch (_: IOException) {
                false
            }
        }
    }

    // A signal that came over GATT while a session is up belongs to that session's mirror, whose
    // ledger knows the key hashes and tombstones the dismissals.
    internal suspend fun dispatchToSession(signal: BleSignal): Boolean {
        val session = active.get() ?: return false
        session.touch()
        session.dispatch(signal)
        return true
    }

    // A sync-request or a long reply over GATT asks for a connection. The visit lives in the caller's
    // scope — the companion window's — and dies with it. It makes one attempt and nothing retries it on
    // a timer: the next summon does, and the Mac sends a sync-request over GATT with every doorbell and
    // on every subscription it confirms, so a Mac whose Wi-Fi was off is reached once it is back.
    internal fun summon(scope: CoroutineScope, reason: String) {
        synchronized(visitLock) {
            when {
                active.get() != null -> BridgeLog.event("summon for $reason: the session is up")
                attended -> {
                    retries.trySend(Unit)
                    BridgeLog.event("summon for $reason: the home screen link is asked to connect")
                }
                visiting?.isActive == true -> BridgeLog.event("summon for $reason: a visit is already under way")
                else -> {
                    BridgeLog.event("summon for $reason: opening a visit")
                    visiting = scope.launch { visit() }
                }
            }
        }
    }

    private suspend fun visit() {
        val settings = config.settings()
        val pairing = settings.pairing?.takeIf { settings.enabled } ?: return
        val target = SessionTarget(
            BridgeEndpoint(pairing.host, pairing.port),
            pairing.macFingerprint,
            settings.clipboardFromMac,
        )
        try {
            val end = attempt(target, idleClose = true)
            mutableState.value = end.state
            if (!end.reached) {
                BridgeLog.info("visit failed: ${end.state.name.lowercase()}; waiting for the next summon")
            }
        } finally {
            if (!attended) mutableState.value = BridgeLinkState.IDLE
        }
    }

    private fun seal(session: ActiveSession, signal: BleSignal, seq: Long): ByteArray? = synchronized(session.pairing) {
        if (active.get() !== session) return null
        BleFrameCodec.seal(
            session.pairing.bleKey, BleDirection.PHONE_TO_MAC, signal.type.code, seq, encodeBleSignal(signal),
        )
    }

    // Backoff runs only inside this coroutine, which dies with onStop: a Mac that is away costs at most
    // one connection attempt a minute while the home screen is in front, and nothing once it is not.
    // A summon that came before the link was replaced is stale, so it is drained before the wait.
    private suspend fun maintain(target: SessionTarget, idleClose: Boolean) {
        val backoff = SessionBackoff()
        while (true) {
            val end = attempt(target, idleClose)
            mutableState.value = end.state
            when (val retry = backoff.after(end)) {
                is SessionRetry.After -> delay(retry.millis)
                SessionRetry.OnSummon -> {
                    retries.tryReceive()
                    BridgeLog.info("session replaced, the link waits for the next summon")
                    retries.receive()
                }
                SessionRetry.Never -> awaitCancellation()
            }
        }
    }

    // TCP needs the local network permission and the L2CAP channel does not, so without it the link
    // still goes over Bluetooth, and says the permission is missing only when that fails too.
    private suspend fun attempt(target: SessionTarget, idleClose: Boolean): SessionEnd {
        val tcp = localNetworkGranted(application)
        val pairing = config.openPairing()?.takeIf { it.macFingerprint == target.pin }
        return when {
            !tcp && carriers.l2capRoute() == null -> SessionEnd(BridgeLinkState.NO_LOCAL_NETWORK)
            pairing == null -> SessionEnd(BridgeLinkState.KEY_UNAVAILABLE)
            else -> try {
                connectAndConverse(target, pairing, idleClose, tcp)
            } finally {
                synchronized(pairing) { pairing.bleKey.fill(0) }
            }
        }
    }

    // A Mac found at a new address is saved, which changes the home screen link's target: that link
    // then reconnects once to the same address, a cost paid once per move.
    private suspend fun connectAndConverse(
        target: SessionTarget,
        pairing: OpenedPairing,
        idleClose: Boolean,
        tcp: Boolean,
    ): SessionEnd {
        val unreachable = SessionEnd(if (tcp) BridgeLinkState.UNREACHABLE else BridgeLinkState.NO_LOCAL_NETWORK)
        return try {
            mutableState.value = BridgeLinkState.CONNECTING
            val carried = carriers.dial(
                tcp = {
                    if (tcp) {
                        logged(Carrier.TCP) {
                            locator.dial(target.endpoint, target.pin, pairing.macName) { endpoint ->
                                tls.connect(endpoint.host, endpoint.port, target.pin.hexToByteArray())
                            }
                        }
                    } else {
                        null
                    }
                },
                l2cap = { route -> logged(Carrier.L2CAP) { connectL2cap(route, pairing.bleKey) } },
            )
            if (carried == null) unreachable else converse(carried, pairing, idleClose)
        } catch (_: BridgePinMismatchException) {
            BridgeLog.info("session refused: the Mac's certificate is not the pinned one")
            SessionEnd(BridgeLinkState.REJECTED)
        } catch (_: IOException) {
            unreachable
        } catch (_: GeneralSecurityException) {
            SessionEnd(BridgeLinkState.KEY_UNAVAILABLE)
        } catch (_: ProviderException) {
            SessionEnd(BridgeLinkState.KEY_UNAVAILABLE)
        }
    }

    private suspend fun <T> logged(carrier: Carrier, connect: suspend () -> T): T = try {
        connect()
    } catch (failure: IOException) {
        BridgeLog.event("session could not connect over ${carrier.tag}: ${failure.javaClass.simpleName}")
        throw failure
    }

    // Blocking reads do not notice cancellation, so a watcher closes the socket when the link stops,
    // which ends the read with an exception and lets the coroutine finish. A watcher cancelled before
    // it ever ran closes nothing, hence the close in finally as well.
    private suspend fun converse(
        carried: Carried<BridgeConnection>,
        pairing: OpenedPairing,
        idleClose: Boolean,
    ): SessionEnd = coroutineScope {
        val connection = carried.value
        val watcher = launch(Dispatchers.IO) {
            try {
                awaitCancellation()
            } finally {
                connection.close()
            }
        }
        try {
            withContext(Dispatchers.IO) { Conversation(connection, pairing, idleClose, carried.carrier).run() }
        } finally {
            watcher.cancel()
            connection.close()
        }
    }

    private inner class Conversation(
        private val connection: BridgeConnection,
        private val pairing: OpenedPairing,
        idleClose: Boolean,
        private val carrier: Carrier,
    ) : SessionInbox {

        private val talk = SessionConversation(
            connection = connection,
            open = { frame -> guard.open(pairing, frame) },
            inbox = this,
            idleCloseMs = SESSION_IDLE_CLOSE_MS.takeIf { idleClose },
        )
        private val session = ActiveSession(connection, pairing, ::signal, talk::touch)
        private val mirror = BridgeMirror(
            listener = listener,
            icons = icons,
            rules = rules,
            sendMessage = { message, binary -> talk.send(message, binary) },
            sendSignal = { signal -> this@BridgeSessionLink.send(signal) },
            deliverReply = replies::deliver,
        )

        suspend fun run(): SessionEnd = try {
            talk.send(sessionHello(config.settings()))
            opened(connection.read())
        } catch (failure: SSLException) {
            // TLS 1.3 finishes the client's handshake before the Mac has judged the phone's certificate,
            // so a Mac that no longer knows this phone answers with an alert on the first read. That alert
            // cannot be told apart from other TLS failures, so only the phone's own pin check says REJECTED.
            BridgeLog.info("session TLS failed before hello: ${failure.javaClass.simpleName}")
            SessionEnd(BridgeLinkState.UNREACHABLE)
        } catch (_: IOException) {
            SessionEnd(BridgeLinkState.UNREACHABLE)
        }

        private suspend fun opened(first: BridgeInbound?): SessionEnd {
            if (first is BridgeInbound.Message) BridgeLog.received(SessionConversation.CONNECTION, first.message)
            return when (val opening = sessionOpening(first)) {
                SessionOpening.Established -> established()
                is SessionOpening.Refused -> {
                    opening.bye?.let { talk.send(it) }
                    opening.end
                }
            }
        }

        // The mirror sends the opening active-set once the shade can be read, and every doorbell
        // after it; a mirror that fails closes the connection, which ends the read below.
        private suspend fun established(): SessionEnd {
            val since = System.nanoTime()
            connection.setReadTimeout(0)
            active.set(session)
            mutableState.value = BridgeLinkState.CONNECTED
            started.update { it + 1 }
            BridgeLog.info("session established over ${carrier.tag}, Mac ${BridgeLog.prefix(pairing.macFingerprint)}")
            val end = try {
                coroutineScope {
                    val worker = launch {
                        try {
                            mirror.run()
                        } catch (_: IOException) {
                            BridgeLog.event("mirror stopped: the connection failed")
                        } finally {
                            connection.close()
                        }
                    }
                    try {
                        talk.run()
                    } finally {
                        worker.cancel()
                    }
                }
            } catch (_: IOException) {
                SessionEnd(BridgeLinkState.UNREACHABLE)
            } finally {
                active.compareAndSet(session, null)
                BridgeLog.info("session ended")
            }
            val ended = if (talk.idled) SessionEnd(BridgeLinkState.IDLE) else end
            return ended.copy(stable = sessionStable(System.nanoTime() - since), reached = true)
        }

        override suspend fun sync() = mirror.requestSync()

        override suspend fun icon(hash: String) = mirror.requestIcon(hash)

        override suspend fun replyText(key: String, text: String) = mirror.requestReplyText(key, text)

        override suspend fun clip(text: String, sensitive: Boolean, ageMs: Long?) =
            clipboard.clip(text, sensitive, ageMs)

        override suspend fun lockState(seq: Long, locked: Boolean) = macLock.received(pairing, seq, locked)

        // A clipboard-pending needs nothing here: the Mac sends its clip on this session.
        override suspend fun signal(signal: BleSignal) {
            when (signal) {
                is BleSignal.SyncRequest -> mirror.requestSync()
                is BleSignal.Dismiss -> mirror.requestDismiss(signal)
                is BleSignal.Reply -> mirror.requestReply(signal)
                is BleSignal.ClipboardSmall -> clipboard.clip(signal.text, signal.sensitive, signal.ageMs)
                else -> Unit
            }
        }
    }
}
