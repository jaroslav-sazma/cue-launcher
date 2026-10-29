package app.cue.launcher.system.bridge

import app.cue.launcher.BuildConfig
import app.cue.launcher.data.bridge.BridgeAssociation
import app.cue.launcher.data.bridge.BridgeConfigRepository
import app.cue.launcher.data.bridge.CommitOutcome
import app.cue.launcher.data.bridge.PairingCommit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

internal sealed interface PairingProgress {

    data object Connecting : PairingProgress

    data object Requested : PairingProgress

    data class Confirming(val sas: String) : PairingProgress

    data class Committed(val macName: String) : PairingProgress

    data class Aborted(val reason: PairingAbort) : PairingProgress
}

internal sealed interface PairingEvent {
    class Inbound(val message: BridgeMessage) : PairingEvent
    class Unknown(val type: String) : PairingEvent
    data object Malformed : PairingEvent
    data object Lost : PairingEvent
    data object Cancel : PairingEvent
    data object Timeout : PairingEvent
}

internal class PairingHandle {

    internal val events = Channel<PairingEvent>(Channel.UNLIMITED)

    private val state = MutableStateFlow<PairingProgress>(PairingProgress.Connecting)

    val progress: StateFlow<PairingProgress> = state.asStateFlow()

    fun cancel() {
        events.trySend(PairingEvent.Cancel)
    }

    internal fun publish(progress: PairingProgress) {
        state.value = progress
    }
}

// The ceremony belongs to the screen that started it: its coroutine runs in that screen's scope, and
// leaving the screen sends pair-abort "cancelled" and closes the connection.
@SingleIn(AppScope::class)
@Inject
class BridgePairing(
    private val identity: BridgeIdentity,
    private val tls: BridgeTlsClient,
    private val config: BridgeConfigRepository,
    private val companion: BridgeCompanion,
    private val teardown: BridgeTeardown,
) {

    // A pairing without an association is the debug build's pairing over the dev endpoint.
    internal fun start(scope: CoroutineScope, target: PairingTarget, association: BridgeAssociation?): PairingHandle {
        val handle = PairingHandle()
        scope.launch(Dispatchers.IO) {
            teardown.settled()
            run(target, association, handle)
        }
        return handle
    }

    private suspend fun run(target: PairingTarget, association: BridgeAssociation?, handle: PairingHandle) {
        val ceremony = ceremonyOf(target) ?: return handle.publish(PairingProgress.Aborted(PairingAbort.KeystoreFailed))
        val connection = connect(target, handle) ?: return
        connection.setReadTimeout(0)
        coroutineScope {
            launch {
                try {
                    awaitCancellation()
                } finally {
                    val effects = synchronized(ceremony) { ceremony.cancel() }
                    effects.filterIsInstance<PairingEffect.Send>().forEach { send ->
                        runCatching { connection.send(send.message) }
                    }
                    connection.close()
                }
            }
            launch { readLoop(connection, handle) }
            Converse(ceremony, connection, handle, association).run()
            connection.close()
            coroutineContext.cancelChildren()
        }
    }

    private suspend fun connect(target: PairingTarget, handle: PairingHandle): TlsConnection? = try {
        tls.connectForPairing(target.host, target.port)
    } catch (failure: IOException) {
        BridgeLog.info("pairing could not connect: ${failure.javaClass.simpleName}")
        handle.publish(PairingProgress.Aborted(PairingAbort.Unreachable))
        null
    } catch (failure: GeneralSecurityException) {
        BridgeLog.info("pairing could not set up TLS: ${failure.javaClass.simpleName}")
        handle.publish(PairingProgress.Aborted(PairingAbort.KeystoreFailed))
        null
    }

    private fun ceremonyOf(target: PairingTarget): PhonePairingCeremony? = try {
        PhonePairingCeremony(target, identity.ensure().fingerprint, X25519.generate())
    } catch (failure: GeneralSecurityException) {
        BridgeLog.info("pairing has no identity: ${failure.javaClass.simpleName}")
        null
    } catch (failure: ProviderException) {
        BridgeLog.info("pairing has no identity: ${failure.javaClass.simpleName}")
        null
    } catch (failure: IOException) {
        BridgeLog.info("pairing has no identity: ${failure.javaClass.simpleName}")
        null
    }

    private suspend fun readLoop(connection: BridgeConnection, handle: PairingHandle) {
        val end = try {
            while (true) {
                when (val inbound = connection.read() ?: break) {
                    is BridgeInbound.Message -> handle.events.send(PairingEvent.Inbound(inbound.message))
                    is BridgeInbound.Unknown -> handle.events.send(PairingEvent.Unknown(inbound.type))
                }
            }
            PairingEvent.Lost
        } catch (_: BridgeProtocolException) {
            PairingEvent.Malformed
        } catch (_: IOException) {
            PairingEvent.Lost
        }
        handle.events.trySend(end)
    }

    private inner class Converse(
        private val ceremony: PhonePairingCeremony,
        private val connection: TlsConnection,
        private val handle: PairingHandle,
        private val association: BridgeAssociation?,
    ) {

        private var deadline: TimeSource.Monotonic.ValueTimeMark? = null
        private var failure: PairingAbort? = null
        private var refused = false

        suspend fun run() {
            apply(synchronized(ceremony) { ceremony.connected(connection.peerFingerprint) })
            publish()
            while (ceremony.isActive) {
                val event = next()
                val effects = synchronized(ceremony) { step(event) }
                apply(effects)
                publish()
            }
        }

        private suspend fun next(): PairingEvent {
            val remaining = deadline?.let { -it.elapsedNow() } ?: return handle.events.receive()
            return withTimeoutOrNull(remaining.coerceAtLeast(Duration.ZERO)) {
                handle.events.receive()
            } ?: PairingEvent.Timeout
        }

        private fun step(event: PairingEvent): List<PairingEffect> = when (event) {
            is PairingEvent.Inbound -> {
                BridgeLog.received(CONNECTION, event.message)
                ceremony.receive(event.message)
            }
            is PairingEvent.Unknown -> ceremony.receiveUnknown().also { effects ->
                val outcome = if (effects.isEmpty()) "ignored" else "before hello, malformed"
                BridgeLog.event("recv ${event.type} conn=$CONNECTION unknown type, $outcome")
            }
            PairingEvent.Malformed -> ceremony.malformed()
            PairingEvent.Lost -> ceremony.connectionLost()
            PairingEvent.Cancel -> ceremony.cancel()
            PairingEvent.Timeout -> ceremony.timeout()
        }

        private suspend fun apply(effects: List<PairingEffect>) {
            for (effect in effects) {
                when (effect) {
                    is PairingEffect.Send -> if (!refused) send(effect.message)
                    is PairingEffect.Arm -> deadline = TimeSource.Monotonic.markNow() + effect.timer.millis.milliseconds
                    is PairingEffect.Commit -> commit(effect.result)
                    PairingEffect.Close -> connection.close()
                }
            }
        }

        private fun send(message: BridgeMessage) {
            try {
                connection.send(message)
                BridgeLog.sent(CONNECTION, message)
            } catch (_: IOException) {
                BridgeLog.event("send ${wireType(message)} conn=$CONNECTION failed")
            }
        }

        private suspend fun commit(result: PairingResult) {
            val commit = PairingCommit(
                macName = result.target.name,
                host = result.target.host,
                port = result.target.port,
                macFingerprint = result.macFingerprint.toHexString(),
                bleKey = result.bleKey,
                association = association,
            )
            val outcome = try {
                config.commitPairing(commit, devEndpoint = BuildConfig.DEBUG && association == null)
            } catch (_: IOException) {
                CommitOutcome.NOT_SEALED
            } finally {
                result.bleKey.fill(0)
            }
            when (outcome) {
                CommitOutcome.SAVED -> {
                    BridgeLog.info("pairing committed, Mac ${BridgeLog.prefix(result.macFingerprint)}")
                    association?.let { companion.observe(it.id) }
                }
                CommitOutcome.NOT_SEALED -> {
                    BridgeLog.info("pairing could not be saved")
                    failure = PairingAbort.KeystoreFailed
                }
                // The bridge was turned off during the ceremony: nothing is stored, and the Mac hears
                // the same as if the user had left.
                CommitOutcome.BRIDGE_OFF -> {
                    BridgeLog.info("pairing not saved: the bridge is off")
                    send(BridgeMessage.PairAbort(ABORT_CANCELLED))
                    refused = true
                    failure = PairingAbort.Sent(PairAbortReason.CANCELLED)
                }
            }
        }

        private fun publish() {
            val progress = failure?.let(PairingProgress::Aborted) ?: when (val phase = ceremony.phase) {
                is PhonePairingCeremony.Phase.Connecting -> PairingProgress.Connecting
                PhonePairingCeremony.Phase.Requested -> PairingProgress.Requested
                is PhonePairingCeremony.Phase.Confirming -> PairingProgress.Confirming(phase.sas)
                PhonePairingCeremony.Phase.Committed -> PairingProgress.Committed(ceremony.macName)
                is PhonePairingCeremony.Phase.Aborted -> PairingProgress.Aborted(phase.reason)
            }
            if (progress is PairingProgress.Aborted) BridgeLog.info("pairing aborted: ${progress.reason}")
            handle.publish(progress)
        }
    }

    private companion object {
        const val CONNECTION = "pair"
    }
}
