package app.cue.launcher.system.bridge

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.security.GeneralSecurityException

class CryptoVectorsTest {

    @TestFactory
    fun `ChaCha20-Poly1305 seals and opens the RFC 8439 vectors`(): List<DynamicTest> =
        BridgeFixtures.load("chacha20poly1305.json").objects("vectors").map { v ->
            DynamicTest.dynamicTest(v.str("source")) {
                val key = v.hex("key")
                val nonce = v.hex("nonce")
                val aad = v.hex("aad")
                val sealed = v.hex("ciphertext") + v.hex("tag")

                assertArrayEquals(sealed, ChaCha20Poly1305.seal(key, nonce, aad, v.hex("plaintext")))
                assertArrayEquals(v.hex("plaintext"), ChaCha20Poly1305.open(key, nonce, aad, sealed))
            }
        }

    @Test
    fun `ChaCha20-Poly1305 refuses a tampered tag`() {
        val v = BridgeFixtures.load("chacha20poly1305.json").objects("vectors").first()
        val sealed = v.hex("ciphertext") + v.hex("tag")
        sealed[sealed.lastIndex] = (sealed.last().toInt() xor 1).toByte()

        assertThrows(GeneralSecurityException::class.java) {
            ChaCha20Poly1305.open(v.hex("key"), v.hex("nonce"), v.hex("aad"), sealed)
        }
    }

    @TestFactory
    fun `HKDF-SHA256 matches RFC 5869`(): List<DynamicTest> =
        BridgeFixtures.load("hkdf.json").objects("vectors").map { v ->
            DynamicTest.dynamicTest(v.str("source")) {
                val length = v.int("length")
                val prk = Hkdf.extract(v.hex("salt"), v.hex("ikm"))

                assertEquals(v.str("prk"), prk.toHexString())
                assertEquals(v.str("okm"), Hkdf.expand(prk, v.hex("info"), length).toHexString())
                val okm = Hkdf.sha256(v.hex("ikm"), v.hex("salt"), v.hex("info"), length)
                assertEquals(v.str("okm"), okm.toHexString())
            }
        }

    @TestFactory
    fun `X25519 scalar multiplication matches RFC 7748`(): List<DynamicTest> =
        BridgeFixtures.load("x25519.json").objects("scalarMult").map { v ->
            DynamicTest.dynamicTest(v.str("source")) {
                val output = X25519.agree(X25519.privateKey(v.hex("scalar")), v.hex("u"))

                assertEquals(v.str("output"), output.toHexString())
            }
        }

    @TestFactory
    fun `X25519 iterated vectors match RFC 7748`(): List<DynamicTest> =
        BridgeFixtures.load("x25519.json").objects("iterated").map { v ->
            DynamicTest.dynamicTest(v.str("source")) {
                var k = v.hex("start")
                var u = v.hex("start")
                repeat(v.int("iterations")) {
                    val next = X25519.agree(X25519.privateKey(k), u)
                    u = k
                    k = next
                }
                assertEquals(v.str("output"), k.toHexString())
            }
        }

    @TestFactory
    fun `X25519 Diffie-Hellman matches RFC 7748`(): List<DynamicTest> =
        BridgeFixtures.load("x25519.json").objects("diffieHellman").map { v ->
            DynamicTest.dynamicTest(v.str("source")) {
                val alice = X25519.privateKey(v.hex("alicePrivate"))
                val bob = X25519.privateKey(v.hex("bobPrivate"))

                assertEquals(v.str("alicePublic"), X25519.publicKeyOf(alice).toHexString())
                assertEquals(v.str("bobPublic"), X25519.publicKeyOf(bob).toHexString())
                assertEquals(v.str("shared"), X25519.agree(alice, v.hex("bobPublic")).toHexString())
                assertEquals(v.str("shared"), X25519.agree(bob, v.hex("alicePublic")).toHexString())
            }
        }

    @Test
    fun `fresh X25519 key pairs agree on one secret`() {
        val mac = X25519.generate()
        val phone = X25519.generate()

        assertEquals(X25519_KEY_BYTES, mac.publicKey.size)
        assertArrayEquals(mac.publicKey, X25519.publicKeyOf(mac.privateKey))
        assertArrayEquals(X25519.agree(mac.privateKey, phone.publicKey), X25519.agree(phone.privateKey, mac.publicKey))
    }

    @Test
    fun `a small-order public key is refused`() {
        val key = X25519.generate().privateKey

        assertThrows(GeneralSecurityException::class.java) { X25519.agree(key, ByteArray(X25519_KEY_BYTES)) }
    }
}
