package app.cue.launcher.system.bridge

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.OutputStream
import java.security.InvalidKeyException

class L2capVectorsTest {

    private val fixture = BridgeFixtures.load("l2cap.json")
    private val transcript = fixture.getValue("transcript") as JsonObject

    @Test
    fun `the label and the limits match the code`() {
        assertEquals(L2CAP_KEYS_INFO, fixture.str("label"))
        assertEquals(X25519_KEY_BYTES, fixture.int("handshakeKeyBytes"))
        assertEquals(L2capRecordCodec.MAX_PLAINTEXT_BYTES, fixture.int("maxPlaintextBytes"))
        assertEquals(L2capRecordCodec.MIN_CIPHERTEXT_BYTES, fixture.int("minCiphertextBytes"))
        assertEquals(L2capRecordCodec.MAX_CIPHERTEXT_BYTES, fixture.int("maxCiphertextBytes"))
        assertEquals(L2capChannel.TIMEOUT_MS, fixture.int("timeoutMs"))
    }

    @Test
    fun `the K_ble of the vectors is the one the first pairing case derives`() {
        val kBle = BridgeFixtures.load("pairing.json").objects("cases").first().str("kBle")

        assertEquals(kBle, fixture.objects("derivation").first().str("kBle"))
        assertEquals(kBle, transcript.str("kBle"))
    }

    @TestFactory
    fun `the channel keys derive as the vectors expect`(): List<DynamicTest> =
        fixture.objects("derivation").filter { it.str("expect") == "keys" }.map { v ->
            DynamicTest.dynamicTest(v.str("name")) {
                val phone = keyPair(v.hex("phonePrivateKey"))
                val mac = keyPair(v.hex("macPrivateKey"))
                val phonePublic = v.hex("phonePublicKey")
                val macPublic = v.hex("macPublicKey")
                assertArrayEquals(phonePublic, phone.publicKey)
                assertArrayEquals(macPublic, mac.publicKey)

                val ss = X25519.agree(phone.privateKey, macPublic)
                assertEquals(v.str("sharedSecret"), ss.toHexString())
                assertEquals(v.str("sharedSecret"), X25519.agree(mac.privateKey, phonePublic).toHexString())
                val salt = L2capDerivation.salt(v.hex("kBle"), phonePublic, macPublic)
                assertEquals(v.str("salt"), salt.toHexString())
                assertEquals(v.str("okm"), L2capDerivation.okm(ss, salt).toHexString())
                val keys = L2capDerivation.keys(ss, v.hex("kBle"), phonePublic, macPublic)
                assertEquals(v.str("phoneToMacKey"), keys.phoneToMac.toHexString())
                assertEquals(v.str("macToPhoneKey"), keys.macToPhone.toHexString())
            }
        }

    @TestFactory
    fun `a low-order key aborts the handshake`(): List<DynamicTest> =
        fixture.objects("derivation").filter { it.str("expect") != "keys" }.map { v ->
            DynamicTest.dynamicTest(v.str("name")) {
                assertEquals("zero-shared-secret", v.str("expect"))
                val phone = keyPair(v.hex("phonePrivateKey"))
                assertThrows(InvalidKeyException::class.java) { X25519.agree(phone.privateKey, v.hex("macPublicKey")) }

                val socket = ClosingSocket()
                assertThrows(BridgeProtocolException::class.java) {
                    val raw = L2capStreams(ByteArrayInputStream(v.hex("macPublicKey")), ByteArrayOutputStream())
                    L2capChannel.connect(socket, raw, v.hex("kBle"), ephemeral = phone)
                }
                assertTrue(socket.closed)
            }
        }

    @TestFactory
    fun `records seal and open to the vectors`(): List<DynamicTest> =
        records().objects("seals").filter { it.containsKey("plaintext") }.map { v ->
            DynamicTest.dynamicTest(v.str("name")) {
                val key = v.hex("key")
                val counter = v.long("counter")
                assertEquals(v.str("nonce"), L2capRecordCodec.nonce(counter).toHexString())

                assertEquals(v.str("record"), L2capRecordCodec.seal(key, counter, v.hex("plaintext")).toHexString())

                val stream = ByteArrayInputStream(v.hex("record"))
                assertEquals(v.str("plaintext"), L2capRecordCodec.read(stream, key, counter)!!.toHexString())
                assertNull(L2capRecordCodec.read(stream, key, counter + 1))
            }
        }

    @Test
    fun `the largest record seals to the vector`() {
        val v = records().objects("seals").single { !it.containsKey("plaintext") }
        assertEquals("i mod 256", v.str("plaintextPattern"))
        val plaintext = ByteArray(v.int("plaintextBytes")) { it.toByte() }
        assertEquals(L2capRecordCodec.MAX_PLAINTEXT_BYTES, plaintext.size)

        val key = v.hex("key")
        val counter = v.long("counter")

        val record = L2capRecordCodec.seal(key, counter, plaintext)

        assertEquals(v.int("recordBytes"), record.size)
        assertEquals(v.str("recordHead"), record.copyOf(v.str("recordHead").length / 2).toHexString())
        assertEquals(v.str("tag"), record.copyOfRange(record.size - v.str("tag").length / 2, record.size).toHexString())
        assertEquals(v.str("recordSha256"), sha256(record).toHexString())
        assertArrayEquals(plaintext, L2capRecordCodec.read(ByteArrayInputStream(record), key, counter))
    }

    @TestFactory
    fun `broken records fail for the stated reason`(): List<DynamicTest> =
        records().objects("failures").map { v ->
            DynamicTest.dynamicTest(v.str("name")) {
                assertEquals("fail", v.str("expect"))
                val zeroFill = if (v.containsKey("zeroFill")) v.int("zeroFill") else 0
                val stream = ByteArrayInputStream(v.hex("stream") + ByteArray(zeroFill))

                val failure = assertThrows(L2capRecordException::class.java) {
                    L2capRecordCodec.read(stream, v.hex("key"), v.long("counter"))
                }

                assertEquals(L2capRejection.valueOf(v.str("reason").uppercase()), failure.reason)
            }
        }

    @Test
    fun `the transcript derives its keys`() {
        val t = transcript
        val phonePublic = t.hex("phonePublicKey")
        val macPublic = t.hex("macPublicKey")
        assertArrayEquals(phonePublic, keyPair(t.hex("phonePrivateKey")).publicKey)
        assertArrayEquals(macPublic, keyPair(t.hex("macPrivateKey")).publicKey)
        val ss = X25519.agree(X25519.privateKey(t.hex("phonePrivateKey")), macPublic)
        val keys = L2capDerivation.keys(ss, t.hex("kBle"), phonePublic, macPublic)

        assertEquals(t.str("sharedSecret"), ss.toHexString())
        assertEquals(t.str("salt"), L2capDerivation.salt(t.hex("kBle"), phonePublic, macPublic).toHexString())
        assertEquals(t.str("phoneToMacKey"), keys.phoneToMac.toHexString())
        assertEquals(t.str("macToPhoneKey"), keys.macToPhone.toHexString())
    }

    @Test
    fun `each side's writes concatenate to its stream`() {
        listOf("phoneWrites" to "phoneStream", "macWrites" to "macStream").forEach { (writes, stream) ->
            val joined = transcript.objects(writes).joinToString("") { it.str("bytes") }
            assertEquals(transcript.str(stream), joined)
        }
    }

    // The phone's frames are written the way the transcript cuts them, record by record, so the second
    // frame spans two records and the third shares one with it.
    @Test
    fun `the phone side reproduces its stream and reads the Mac's`() {
        val out = ByteArrayOutputStream()
        val streams = L2capChannel.handshake(
            L2capRole.PHONE, reading("macStream", out), transcript.hex("kBle"),
            keyPair(transcript.hex("phonePrivateKey")), ReadDeadline {},
        )

        transcript.objects("phoneWrites").drop(1).forEach { streams.output.writeRecord(it.hex("plaintext")) }

        assertEquals(transcript.str("phoneStream"), out.toByteArray().toHexString())
        assertEquals(bodies("macBodies"), readBodies(streams))
    }

    @Test
    fun `the Mac side reproduces its stream and reads the phone's`() {
        val out = ByteArrayOutputStream()
        val streams = L2capChannel.handshake(
            L2capRole.MAC, reading("phoneStream", out), transcript.hex("kBle"),
            keyPair(transcript.hex("macPrivateKey")), ReadDeadline {},
        )

        transcript.objects("macWrites").drop(1).forEach { streams.output.writeRecord(it.hex("plaintext")) }

        assertEquals(transcript.str("macStream"), out.toByteArray().toHexString())
        assertEquals(bodies("phoneBodies"), readBodies(streams))
    }

    @Test
    fun `a connection over the channel sends hello as the transcript's first record`() {
        val out = ByteArrayOutputStream()
        val connection = L2capChannel.connect(
            ClosingSocket(), reading("macStream", out), transcript.hex("kBle"),
            ephemeral = keyPair(transcript.hex("phonePrivateKey")),
        )

        connection.send(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION))

        val expected = transcript.objects("phoneWrites").take(2).joinToString("") { it.str("bytes") }
        assertEquals(expected, out.toByteArray().toHexString())
        assertEquals(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION), (connection.read() as BridgeInbound.Message).message)
        assertTrue((connection.read() as BridgeInbound.Message).message is BridgeMessage.SyncRequest)
        assertNull(connection.read())
    }

    private fun records(): JsonObject = fixture.getValue("records") as JsonObject

    private fun reading(stream: String, out: OutputStream): L2capStreams =
        L2capStreams(ByteArrayInputStream(transcript.hex(stream)), out)

    private fun bodies(name: String): List<String> =
        transcript.getValue(name).jsonArray.map { it.jsonPrimitive.content }

    private fun readBodies(streams: L2capStreams): List<String> = generateSequence { BridgeFraming.read(streams.input) }
        .map { inbound -> bridgeBody((inbound as BridgeInbound.Message).message).decodeToString() }
        .toList()

    private fun OutputStream.writeRecord(plaintext: ByteArray) {
        write(plaintext)
        flush()
    }

    private fun keyPair(privateKey: ByteArray): X25519KeyPair {
        val key = X25519.privateKey(privateKey)
        return X25519KeyPair(X25519.publicKeyOf(key), key)
    }

    private class ClosingSocket : Closeable {

        var closed = false

        override fun close() {
            closed = true
        }
    }
}
