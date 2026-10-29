package app.cue.launcher.system.bridge

import app.cue.launcher.system.bridge.PhonePairingCeremony.Phase
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class PhonePairingCeremonyTest {

    private val fixture = BridgeFixtures.load("pairing.json")
    private val first = fixture.objects("cases").first()
    private val target = PairingTarget("Example-Mac", "192.168.1.20", PORT)

    private fun case(name: String) = fixture.objects("cases").single { it.str("name") == name }

    private fun ceremony(case: JsonObject = first) = PhonePairingCeremony(
        target = target,
        phoneFingerprint = case.hex("phoneFingerprint"),
        ephemeral = phoneKey(case),
    )

    private fun phoneKey(case: JsonObject): X25519KeyPair {
        val private = X25519.privateKey(case.hex("phonePrivateKey"))
        return X25519KeyPair(X25519.publicKeyOf(private), private)
    }

    private fun requested(case: JsonObject = first): PhonePairingCeremony = ceremony(case).apply {
        connected(case.hex("macFingerprint"))
        receive(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION))
    }

    private fun confirming(case: JsonObject = first): PhonePairingCeremony = requested(case).apply {
        receive(BridgeMessage.PairResponse(case.str("macPublicKeyBase64")))
    }

    private fun macConfirm(case: JsonObject = first) = BridgeMessage.PairConfirm(case.str("proofMac"))

    private fun sent(effects: List<PairingEffect>) = effects.filterIsInstance<PairingEffect.Send>().map { it.message }

    private fun commit(effects: List<PairingEffect>) = effects.filterIsInstance<PairingEffect.Commit>().single().result

    private fun committedIn(effects: List<PairingEffect>) =
        effects.filterIsInstance<PairingEffect.Commit>().firstOrNull()?.result

    private fun decode(body: String) = bridgeJson.decodeFromString(BridgeMessage.serializer(), body)

    // The phone's side of every vector conversation in which the phone is the honest party: the Mac's
    // messages go in, and what the phone sends has to be the vector's next phone message.
    @TestFactory
    fun `the phone holds up its side of the vector ceremonies`(): List<DynamicTest> =
        fixture.objects("ceremonies")
            .filter { ceremony -> ceremony.getValue("checks").jsonArray.any { it.jsonPrimitive.content == "phone" } }
            .map { vector ->
                DynamicTest.dynamicTest(vector.str("name")) {
                    val case = case(vector.str("case"))
                    val ceremony = ceremony(case)
                    val outgoing = ArrayDeque(sent(ceremony.connected(case.hex("macFingerprint"))))
                    var committed: PairingResult? = null
                    for (step in vector.objects("messages")) {
                        val message = decode(step.str("body"))
                        if (step.str("from") == "mac") {
                            val effects = ceremony.receive(message)
                            outgoing += sent(effects)
                            committed = committed ?: committedIn(effects)
                        } else {
                            assertEquals(message, outgoing.removeFirstOrNull()) { "the phone's next message" }
                        }
                    }
                    assertTrue(outgoing.isEmpty()) { "the phone sent more: $outgoing" }
                    when (vector.str("expect")) {
                        "committed" -> {
                            assertEquals(Phase.Committed, ceremony.phase)
                            assertArrayEquals(vector.hex("kBle"), committed?.bleKey)
                            assertArrayEquals(case.hex("macFingerprint"), committed?.macFingerprint)
                        }
                        "busy" -> assertEquals(
                            Phase.Aborted(PairingAbort.Received(PairAbortReason.BUSY)),
                            ceremony.phase,
                        )
                        else -> assertEquals(
                            Phase.Aborted(PairingAbort.Sent(PairAbortReason.PROTOCOL_ERROR)),
                            ceremony.phase,
                        )
                    }
                }
            }

    @Test
    fun `the phone commits to its key before the Mac shows its own, and reveals and confirms it after`() {
        val ceremony = ceremony()
        ceremony.connected(first.hex("macFingerprint"))

        val requested = ceremony.receive(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION))
        assertEquals(listOf(BridgeMessage.PairRequest(first.str("commit"))), sent(requested))
        assertTrue(PairingEffect.Arm(PairingTimer.STEP) in requested)
        assertEquals(Phase.Requested, ceremony.phase)

        val revealed = ceremony.receive(BridgeMessage.PairResponse(first.str("macPublicKeyBase64")))
        assertEquals(
            listOf(
                PairingEffect.Send(BridgeMessage.PairReveal(first.str("phonePublicKeyBase64"))),
                PairingEffect.Send(BridgeMessage.PairConfirm(first.str("proofPhone"))),
                PairingEffect.Arm(PairingTimer.CONFIRMATION),
            ),
            revealed,
        )
        assertEquals(Phase.Confirming(first.str("sas")), ceremony.phase)
        assertTrue(ceremony.isActive)
    }

    // A pairing connection pins nothing up front: the certificate the handshake showed is the one the
    // code covers and the one that becomes the pin.
    @Test
    fun `whatever certificate the Mac showed goes into the code and the pin`() {
        val swapped = case("the same keys with the two fingerprints swapped: roles are part of the transcript")
        val ceremony = ceremony(swapped)

        assertEquals(
            listOf(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION)),
            sent(ceremony.connected(swapped.hex("macFingerprint"))),
        )
        ceremony.receive(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION))
        ceremony.receive(BridgeMessage.PairResponse(swapped.str("macPublicKeyBase64")))
        assertEquals(Phase.Confirming(swapped.str("sas")), ceremony.phase)

        val result = commit(ceremony.receive(BridgeMessage.PairConfirm(swapped.str("proofMac"))))
        assertArrayEquals(swapped.hex("macFingerprint"), result.macFingerprint)
        assertEquals(target, result.target)
    }

    @Test
    fun `a Mac on another protocol version is told so and left`() {
        val ceremony = ceremony()
        ceremony.connected(first.hex("macFingerprint"))

        val effects = ceremony.receive(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION + 1))

        assertEquals(listOf(BridgeMessage.Bye(BYE_VERSION_MISMATCH)), sent(effects))
        assertEquals(PairingEffect.Close, effects.last())
        assertEquals(Phase.Aborted(PairingAbort.VersionMismatch(BRIDGE_PROTOCOL_VERSION + 1)), ceremony.phase)
    }

    @Test
    fun `a first frame other than hello is malformed and closes without a reply`() {
        val ceremony = ceremony()
        ceremony.connected(first.hex("macFingerprint"))

        val effects = ceremony.receive(BridgeMessage.PairResponse(first.str("macPublicKeyBase64")))

        assertEquals(listOf(PairingEffect.Close), effects)
        assertEquals(Phase.Aborted(PairingAbort.Malformed), ceremony.phase)
    }

    @Test
    fun `an unknown type before the Mac's hello is malformed, after it ignored`() {
        val early = ceremony()
        early.connected(first.hex("macFingerprint"))

        assertEquals(listOf(PairingEffect.Close), early.receiveUnknown())
        assertEquals(Phase.Aborted(PairingAbort.Malformed), early.phase)

        val requested = requested()
        assertEquals(emptyList<PairingEffect>(), requested.receiveUnknown())
        assertEquals(Phase.Requested, requested.phase)

        val confirming = confirming()
        assertEquals(emptyList<PairingEffect>(), confirming.receiveUnknown())
        assertTrue(confirming.phase is Phase.Confirming)
    }

    @Test
    fun `a message out of order is a protocol error`() {
        val requested = requested()
        val early = requested.receive(macConfirm())

        assertEquals(listOf(BridgeMessage.PairAbort(ABORT_PROTOCOL_ERROR)), sent(early))
        assertEquals(Phase.Aborted(PairingAbort.Sent(PairAbortReason.PROTOCOL_ERROR)), requested.phase)

        val confirming = confirming()
        val again = confirming.receive(BridgeMessage.PairResponse(first.str("macPublicKeyBase64")))

        assertEquals(listOf(BridgeMessage.PairAbort(ABORT_PROTOCOL_ERROR)), sent(again))
        assertEquals(Phase.Aborted(PairingAbort.Sent(PairAbortReason.PROTOCOL_ERROR)), confirming.phase)
    }

    // The phone's key stays its own until the Mac's key is valid: a broken answer is not revealed to.
    @TestFactory
    fun `a Mac key that is no X25519 key, or of low order, aborts before the reveal`(): List<DynamicTest> =
        (
            fixture.objects("lowOrderPublicKeys").map { it.str("name") to encodeBase64(it.hex("publicKey")) } +
                listOf(
                    "31 bytes" to encodeBase64(ByteArray(X25519_KEY_BYTES - 1) { 1 }),
                    "not base64" to "not base64!",
                )
            ).map { (name, key) ->
            DynamicTest.dynamicTest(name) {
                val ceremony = requested()

                val effects = ceremony.receive(BridgeMessage.PairResponse(key))

                assertEquals(listOf(BridgeMessage.PairAbort(ABORT_PROTOCOL_ERROR)), sent(effects))
                assertTrue(sent(effects).none { it is BridgeMessage.PairReveal })
                assertEquals(Phase.Aborted(PairingAbort.Sent(PairAbortReason.PROTOCOL_ERROR)), ceremony.phase)
            }
        }

    // A peer that derived another secret cannot produce the Mac's proof: a reflected or foreign one
    // aborts although the phone's own proof is already out.
    @TestFactory
    fun `a pair-confirm without the Mac's proof aborts with a protocol error`(): List<DynamicTest> = mapOf(
        "the phone's own proof reflected" to first.str("proofPhone"),
        "another pairing's proof" to fixture.objects("cases").last().str("proofMac"),
        "uppercase hex" to first.str("proofMac").uppercase(),
        "one byte short" to first.str("proofMac").dropLast(2),
        "empty" to "",
    ).map { (name, proof) ->
        DynamicTest.dynamicTest(name) {
            val ceremony = confirming()

            val effects = ceremony.receive(BridgeMessage.PairConfirm(proof))

            assertEquals(listOf(BridgeMessage.PairAbort(ABORT_PROTOCOL_ERROR)), sent(effects))
            assertTrue(effects.none { it is PairingEffect.Commit })
            assertEquals(Phase.Aborted(PairingAbort.Sent(PairAbortReason.PROTOCOL_ERROR)), ceremony.phase)
            assertEquals(emptyList<PairingEffect>(), ceremony.receive(macConfirm()))
        }
    }

    @Test
    fun `the Mac's abort ends the ceremony, busy included, an unknown reason reading as cancelled`() {
        val mismatch = confirming()
        mismatch.receive(BridgeMessage.PairAbort(ABORT_SAS_MISMATCH))
        assertEquals(Phase.Aborted(PairingAbort.Received(PairAbortReason.SAS_MISMATCH)), mismatch.phase)

        val busy = requested()
        assertEquals(listOf(PairingEffect.Close), busy.receive(BridgeMessage.PairAbort(ABORT_BUSY)))
        assertEquals(Phase.Aborted(PairingAbort.Received(PairAbortReason.BUSY)), busy.phase)

        val unknown = confirming()
        unknown.receive(BridgeMessage.PairAbort("gone-fishing"))
        assertEquals(Phase.Aborted(PairingAbort.Received(PairAbortReason.CANCELLED)), unknown.phase)
    }

    @Test
    fun `the Mac's verified pair-confirm commits at once, then says bye and closes`() {
        val ceremony = confirming()

        val effects = ceremony.receive(macConfirm())

        val result = commit(effects)
        assertArrayEquals(first.hex("kBle"), result.bleKey)
        assertArrayEquals(first.hex("macFingerprint"), result.macFingerprint)
        assertEquals(target, result.target)
        assertEquals(listOf(BridgeMessage.Bye()), sent(effects))
        assertEquals(PairingEffect.Close, effects.last())
        assertEquals(Phase.Committed, ceremony.phase)
    }

    @Test
    fun `timeouts abort every waiting phase`() {
        val beforeHello = ceremony()
        assertEquals(listOf(PairingEffect.Close), beforeHello.timeout())
        assertEquals(Phase.Aborted(PairingAbort.NoAnswer), beforeHello.phase)

        val waitingForHello = ceremony().apply { connected(first.hex("macFingerprint")) }
        assertEquals(listOf(BridgeMessage.PairAbort(ABORT_TIMEOUT)), sent(waitingForHello.timeout()))
        assertEquals(Phase.Aborted(PairingAbort.NoAnswer), waitingForHello.phase)

        val requested = requested()
        assertEquals(listOf(BridgeMessage.PairAbort(ABORT_TIMEOUT)), sent(requested.timeout()))
        assertEquals(Phase.Aborted(PairingAbort.NoAnswer), requested.phase)

        val waitingForMac = confirming()
        assertEquals(listOf(BridgeMessage.PairAbort(ABORT_TIMEOUT)), sent(waitingForMac.timeout()))
        assertEquals(Phase.Aborted(PairingAbort.Sent(PairAbortReason.TIMEOUT)), waitingForMac.phase)
    }

    @Test
    fun `cancelling sends pair-abort only once the connection is a pairing connection`() {
        val connecting = ceremony().apply { connected(first.hex("macFingerprint")) }
        assertEquals(listOf(PairingEffect.Close), connecting.cancel())

        val confirming = confirming()
        assertEquals(listOf(BridgeMessage.PairAbort(ABORT_CANCELLED)), sent(confirming.cancel()))
        assertEquals(Phase.Aborted(PairingAbort.Sent(PairAbortReason.CANCELLED)), confirming.phase)
    }

    @Test
    fun `a lost connection or a bye aborts, a malformed frame closes`() {
        val lost = confirming()
        assertEquals(emptyList<PairingEffect>(), lost.connectionLost())
        assertEquals(Phase.Aborted(PairingAbort.ConnectionLost), lost.phase)

        val bye = confirming()
        bye.receive(BridgeMessage.Bye())
        assertEquals(Phase.Aborted(PairingAbort.ConnectionLost), bye.phase)

        val malformed = confirming()
        assertEquals(listOf(PairingEffect.Close), malformed.malformed())
        assertEquals(Phase.Aborted(PairingAbort.Malformed), malformed.phase)
    }

    @Test
    fun `nothing after the commit changes it`() {
        val ceremony = confirming()
        ceremony.receive(macConfirm())

        assertEquals(emptyList<PairingEffect>(), ceremony.receive(macConfirm()))
        assertEquals(emptyList<PairingEffect>(), ceremony.receive(BridgeMessage.PairAbort(ABORT_CANCELLED)))
        assertEquals(emptyList<PairingEffect>(), ceremony.timeout())
        assertEquals(emptyList<PairingEffect>(), ceremony.cancel())
        assertEquals(emptyList<PairingEffect>(), ceremony.connectionLost())
        assertEquals(Phase.Committed, ceremony.phase)
        assertFalse(ceremony.isActive)
    }

    private companion object {
        const val PORT = 52_817
    }
}
