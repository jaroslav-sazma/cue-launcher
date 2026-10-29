package app.cue.launcher.system.bridge

import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.PrivateKey

internal enum class PairAbortReason(val wire: String) {
    SAS_MISMATCH(ABORT_SAS_MISMATCH),
    CANCELLED(ABORT_CANCELLED),
    TIMEOUT(ABORT_TIMEOUT),
    PROTOCOL_ERROR(ABORT_PROTOCOL_ERROR),
    BUSY(ABORT_BUSY),
    ;

    companion object {
        fun of(wire: String): PairAbortReason = entries.firstOrNull { it.wire == wire } ?: CANCELLED
    }
}

internal sealed interface PairingAbort {

    data class Sent(val reason: PairAbortReason) : PairingAbort

    data class Received(val reason: PairAbortReason) : PairingAbort

    data object NoAnswer : PairingAbort

    data class VersionMismatch(val peerVersion: Int) : PairingAbort

    data object Malformed : PairingAbort

    data object ConnectionLost : PairingAbort

    data object Unreachable : PairingAbort

    data object KeystoreFailed : PairingAbort
}

internal enum class PairingTimer(val millis: Long) {
    STEP(PAIRING_STEP_TIMEOUT_MS),
    CONFIRMATION(PAIRING_CONFIRM_TIMEOUT_MS),
}

internal data class PairingTarget(val name: String, val host: String, val port: Int)

internal class PairingResult(val target: PairingTarget, val macFingerprint: ByteArray, val bleKey: ByteArray)

internal sealed interface PairingEffect {

    data class Send(val message: BridgeMessage) : PairingEffect

    data class Arm(val timer: PairingTimer) : PairingEffect

    class Commit(val result: PairingResult) : PairingEffect

    data object Close : PairingEffect
}

// The phone's side of the ceremony as a pure state machine: CONNECTING → REQUESTED → CONFIRMING →
// COMMITTED or ABORTED. The caller owns the connection and the timers and carries out the returned
// effects in order. Every exit drops the ephemeral key and the derived secrets.
@Suppress("TooManyFunctions") // one entry point per input the spec's state machine reacts to
internal class PhonePairingCeremony(
    private val target: PairingTarget,
    private val phoneFingerprint: ByteArray,
    ephemeral: X25519KeyPair,
) {

    sealed interface Phase {
        data class Connecting(val helloSent: Boolean) : Phase
        data object Requested : Phase
        data class Confirming(val sas: String) : Phase
        data object Committed : Phase
        data class Aborted(val reason: PairingAbort) : Phase
    }

    var phase: Phase = Phase.Connecting(helloSent = false)
        private set

    val isActive: Boolean get() = phase !is Phase.Committed && phase !is Phase.Aborted

    val macName: String get() = target.name

    private var ephemeral: X25519KeyPair? = ephemeral
    private var macFingerprint: ByteArray? = null
    private var bleKey: ByteArray? = null
    private var macProof: ByteArray? = null

    // A pairing connection accepts any certificate: the one the handshake showed goes into the
    // transcript, so a different one shows a different code, and it becomes the pin at the commit.
    fun connected(observedMacFingerprint: ByteArray): List<PairingEffect> {
        if (phase != Phase.Connecting(helloSent = false)) return emptyList()
        macFingerprint = observedMacFingerprint.copyOf()
        phase = Phase.Connecting(helloSent = true)
        return listOf(
            PairingEffect.Send(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION)),
            PairingEffect.Arm(PairingTimer.STEP),
        )
    }

    // The code is checked on the Mac only: the phone commits once the peer of this connection sends a
    // pair-confirm whose proof verifies.
    fun receive(message: BridgeMessage): List<PairingEffect> = when (phase) {
        Phase.Connecting(helloSent = true) -> receiveFirst(message)
        Phase.Requested -> when (message) {
            is BridgeMessage.PairResponse -> reveal(message.pk)
            else -> receiveOther(message)
        }
        is Phase.Confirming -> when {
            message !is BridgeMessage.PairConfirm -> receiveOther(message)
            !isMacProof(message.proof) -> abort(PairAbortReason.PROTOCOL_ERROR)
            else -> commit()
        }
        else -> emptyList()
    }

    // Ignored once the Mac's hello is in, so a newer Mac may add types; before it, malformed like any
    // first frame that is not hello.
    fun receiveUnknown(): List<PairingEffect> =
        if (phase is Phase.Connecting) end(PairingAbort.Malformed, PairingEffect.Close) else emptyList()

    fun cancel(): List<PairingEffect> = when (phase) {
        is Phase.Connecting -> end(PairingAbort.Sent(PairAbortReason.CANCELLED), PairingEffect.Close)
        Phase.Requested, is Phase.Confirming -> abort(PairAbortReason.CANCELLED)
        else -> emptyList()
    }

    fun timeout(): List<PairingEffect> = when (phase) {
        Phase.Connecting(helloSent = false) -> end(PairingAbort.NoAnswer, PairingEffect.Close)
        is Phase.Connecting, Phase.Requested -> end(
            PairingAbort.NoAnswer,
            PairingEffect.Send(BridgeMessage.PairAbort(ABORT_TIMEOUT)),
            PairingEffect.Close,
        )
        is Phase.Confirming -> abort(PairAbortReason.TIMEOUT)
        else -> emptyList()
    }

    fun malformed(): List<PairingEffect> =
        if (isActive) end(PairingAbort.Malformed, PairingEffect.Close) else emptyList()

    fun connectionLost(): List<PairingEffect> = if (isActive) end(PairingAbort.ConnectionLost) else emptyList()

    // The commitment leaves before the Mac's key is known, and the key itself only after the Mac's is
    // on record: neither side can pick its key to steer the code.
    private fun receiveFirst(message: BridgeMessage): List<PairingEffect> = when {
        message !is BridgeMessage.Hello -> end(PairingAbort.Malformed, PairingEffect.Close)
        message.v != BRIDGE_PROTOCOL_VERSION -> end(
            PairingAbort.VersionMismatch(message.v),
            PairingEffect.Send(BridgeMessage.Bye(BYE_VERSION_MISMATCH)),
            PairingEffect.Close,
        )
        else -> {
            val commit = PairingDerivation.commitment(checkNotNull(ephemeral).publicKey).toHexString()
            phase = Phase.Requested
            listOf(PairingEffect.Send(BridgeMessage.PairRequest(commit)), PairingEffect.Arm(PairingTimer.STEP))
        }
    }

    private fun receiveOther(message: BridgeMessage): List<PairingEffect> = when (message) {
        is BridgeMessage.PairAbort ->
            end(PairingAbort.Received(PairAbortReason.of(message.reason)), PairingEffect.Close)
        is BridgeMessage.Bye -> end(PairingAbort.ConnectionLost, PairingEffect.Close)
        else -> abort(PairAbortReason.PROTOCOL_ERROR)
    }

    private fun reveal(macKey: String): List<PairingEffect> {
        val own = checkNotNull(ephemeral)
        val macPublicKey = runCatching { decodeBase64(macKey) }.getOrNull()?.takeIf { it.size == X25519_KEY_BYTES }
        val shared = macPublicKey?.let { agreeOrNull(own.privateKey, it) }
        if (macPublicKey == null || shared == null) return abort(PairAbortReason.PROTOCOL_ERROR)
        val transcriptHash = PairingDerivation.transcriptHash(
            checkNotNull(macFingerprint), phoneFingerprint, macPublicKey, own.publicKey,
        )
        val secrets = PairingDerivation.derive(shared, transcriptHash)
        shared.fill(0)
        ephemeral = null
        bleKey = secrets.bleKey
        val ownProof = PairingDerivation.proof(secrets.confirmKey, PairingRole.PHONE).toHexString()
        macProof = PairingDerivation.proof(secrets.confirmKey, PairingRole.MAC)
        secrets.confirmKey.fill(0)
        phase = Phase.Confirming(secrets.sas)
        return listOf(
            PairingEffect.Send(BridgeMessage.PairReveal(encodeBase64(own.publicKey))),
            PairingEffect.Send(BridgeMessage.PairConfirm(ownProof)),
            PairingEffect.Arm(PairingTimer.CONFIRMATION),
        )
    }

    private fun agreeOrNull(privateKey: PrivateKey, peerPublicKey: ByteArray): ByteArray? = try {
        X25519.agree(privateKey, peerPublicKey)
    } catch (_: GeneralSecurityException) {
        null
    }

    private fun isMacProof(proof: String): Boolean {
        val expected = macProof ?: return false
        return isLowerHex(proof, PROOF_BYTES) && MessageDigest.isEqual(proof.hexToByteArray(), expected)
    }

    private fun commit(): List<PairingEffect> {
        val result = PairingResult(target, checkNotNull(macFingerprint), checkNotNull(bleKey).copyOf())
        wipe()
        phase = Phase.Committed
        return listOf(PairingEffect.Commit(result), PairingEffect.Send(BridgeMessage.Bye()), PairingEffect.Close)
    }

    private fun abort(reason: PairAbortReason): List<PairingEffect> =
        end(PairingAbort.Sent(reason), PairingEffect.Send(BridgeMessage.PairAbort(reason.wire)), PairingEffect.Close)

    private fun end(reason: PairingAbort, vararg effects: PairingEffect): List<PairingEffect> {
        wipe()
        phase = Phase.Aborted(reason)
        return effects.toList()
    }

    private fun wipe() {
        ephemeral = null
        bleKey?.fill(0)
        bleKey = null
        macProof?.fill(0)
        macProof = null
    }
}
