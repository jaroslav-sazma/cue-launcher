package app.cue.launcher.system.bridge

import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.security.GeneralSecurityException
import java.security.MessageDigest

class PairingVectorsTest {

    private val fixture = BridgeFixtures.load("pairing.json")

    @Test
    fun `the labels are the ones the code derives with`() {
        val labels = fixture.getValue("labels").jsonObject

        assertEquals(PAIRING_COMMIT_LABEL, labels.str("commit"))
        assertEquals(PAIRING_TRANSCRIPT_LABEL, labels.str("transcript"))
        assertEquals(PAIRING_KBLE_INFO, labels.str("kBle"))
        assertEquals(PAIRING_SAS_INFO, labels.str("sas"))
        assertEquals(PAIRING_CONFIRM_INFO, labels.str("confirm"))
    }

    @TestFactory
    fun `a fingerprint is SHA-256 of the DER certificate`(): List<DynamicTest> =
        fixture.objects("certificates").map { c ->
            DynamicTest.dynamicTest(c.str("name")) {
                assertEquals(c.str("fingerprint"), certificateFingerprint(c.hex("der")))
            }
        }

    @TestFactory
    fun `both sides derive the same secrets`(): List<DynamicTest> = fixture.objects("cases").map { c ->
        DynamicTest.dynamicTest(c.str("name")) {
            val mac = X25519.privateKey(c.hex("macPrivateKey"))
            val phone = X25519.privateKey(c.hex("phonePrivateKey"))
            val macPublic = X25519.publicKeyOf(mac)
            val phonePublic = X25519.publicKeyOf(phone)
            assertEquals(c.str("macPublicKey"), macPublic.toHexString())
            assertEquals(c.str("phonePublicKey"), phonePublic.toHexString())
            assertEquals(c.str("macPublicKeyBase64"), encodeBase64(macPublic))
            assertEquals(c.str("phonePublicKeyBase64"), encodeBase64(phonePublic))

            assertEquals(
                c.str("commitInput"),
                lengthPrefixed(PAIRING_COMMIT_LABEL.encodeToByteArray(), phonePublic).toHexString(),
            )
            assertEquals(c.str("commit"), PairingDerivation.commitment(phonePublic).toHexString())

            val onMac = X25519.agree(mac, decodeBase64(c.str("phonePublicKeyBase64")))
            val onPhone = X25519.agree(phone, decodeBase64(c.str("macPublicKeyBase64")))
            assertEquals(c.str("sharedSecret"), onMac.toHexString())
            assertArrayEquals(onMac, onPhone)

            val transcript = PairingDerivation.transcript(
                c.hex("macFingerprint"), c.hex("phoneFingerprint"), macPublic, phonePublic,
            )
            assertEquals(c.str("transcript"), transcript.toHexString())
            val hash = PairingDerivation.transcriptHash(
                c.hex("macFingerprint"), c.hex("phoneFingerprint"), macPublic, phonePublic,
            )
            assertEquals(c.str("transcriptHash"), hash.toHexString())

            val secrets = PairingDerivation.derive(onMac, hash)
            assertEquals(c.str("kBle"), secrets.bleKey.toHexString())
            assertEquals(c.str("sas"), secrets.sas)
            assertEquals(SAS_DIGITS, secrets.sas.length)
            val okm = Hkdf.sha256(onMac, hash, PAIRING_SAS_INFO.encodeToByteArray(), c.hex("sasOkm").size)
            assertEquals(c.str("sasOkm"), okm.toHexString())

            val confirmKey = secrets.confirmKey
            assertEquals(c.str("confirmKey"), confirmKey.toHexString())
            assertEquals(c.str("proofPhone"), PairingDerivation.proof(confirmKey, PairingRole.PHONE).toHexString())
            assertEquals(c.str("proofMac"), PairingDerivation.proof(confirmKey, PairingRole.MAC).toHexString())
        }
    }

    // The Mac's check, done here the way the spec words it, so the vectors' expectations are pinned
    // down on both sides.
    @TestFactory
    fun `a revealed key is checked against its commitment`(): List<DynamicTest> =
        fixture.objects("commitChecks").map { v ->
            DynamicTest.dynamicTest(v.str("name")) {
                val commit = v.str("commit")
                val key = runCatching { decodeBase64(v.str("revealedKey")) }.getOrNull()
                val outcome = when {
                    !isLowerHex(commit, COMMIT_BYTES) || key?.size != X25519_KEY_BYTES -> "invalid"
                    MessageDigest.isEqual(PairingDerivation.commitment(key), commit.hexToByteArray()) -> "valid"
                    else -> "mismatch"
                }
                assertEquals(v.str("expect"), outcome)
            }
        }

    @TestFactory
    fun `a low-order public key yields no shared secret`(): List<DynamicTest> =
        fixture.objects("lowOrderPublicKeys").map { v ->
            DynamicTest.dynamicTest(v.str("name")) {
                val phone = X25519.privateKey(fixture.objects("cases").first().hex("phonePrivateKey"))

                assertEquals("zero-shared-secret", v.str("expect"))
                assertThrows(GeneralSecurityException::class.java) { X25519.agree(phone, v.hex("publicKey")) }
            }
        }

    @TestFactory
    fun `abort reasons read as the vectors expect`(): List<DynamicTest> =
        fixture.objects("abortReasons").map { v ->
            DynamicTest.dynamicTest(v.str("wire")) {
                assertEquals(v.str("reads"), PairAbortReason.of(v.str("wire")).wire)
            }
        }

    @Test
    fun `every abort reason of the code is in the vectors`() {
        val wires = fixture.objects("abortReasons").map { it.str("wire") }.toSet()

        PairAbortReason.entries.forEach { assertTrue(it.wire in wires) { "${it.wire} is not in the vectors" } }
    }

    @Test
    fun `the vectors cover a zero-padded SAS and swapped roles`() {
        val cases = fixture.objects("cases")

        assertTrue(cases.any { it.str("sas").startsWith("0") }) { "no case has a SAS with a leading zero" }
        assertTrue(cases.map { it.str("sharedSecret") }.toSet().size < cases.size) {
            "no two cases share a secret, so none shows that the roles change the SAS"
        }
        assertEquals(cases.size, cases.map { it.str("sas") }.toSet().size)
        assertEquals(cases.size * 2, cases.flatMap { listOf(it.str("proofPhone"), it.str("proofMac")) }.toSet().size)
    }
}
