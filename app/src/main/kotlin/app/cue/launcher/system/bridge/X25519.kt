package app.cue.launcher.system.bridge

import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

internal class X25519KeyPair(val publicKey: ByteArray, val privateKey: PrivateKey)

// Raw 32-byte keys travel through the fixed RFC 8410 DER prefixes: both the JDK and Conscrypt accept
// X509 and PKCS#8 encodings for XDH, while the XEC*KeySpec classes behave differently between them.
internal object X25519 {

    private const val ALGORITHM = "XDH"

    private val PUBLIC_PREFIX = "302a300506032b656e032100".hexToByteArray()
    private val PRIVATE_PREFIX = "302e020100300506032b656e04220420".hexToByteArray()
    private val BASE_POINT = ByteArray(X25519_KEY_BYTES).also { it[0] = 9 }

    fun generate(): X25519KeyPair {
        val pair = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair()
        return X25519KeyPair(rawPublicKey(pair.public), pair.private)
    }

    fun privateKey(raw: ByteArray): PrivateKey {
        require(raw.size == X25519_KEY_BYTES) { "an X25519 private key has $X25519_KEY_BYTES bytes" }
        return KeyFactory.getInstance(ALGORITHM).generatePrivate(PKCS8EncodedKeySpec(PRIVATE_PREFIX + raw))
    }

    fun publicKey(raw: ByteArray): PublicKey {
        require(raw.size == X25519_KEY_BYTES) { "an X25519 public key has $X25519_KEY_BYTES bytes" }
        return KeyFactory.getInstance(ALGORITHM).generatePublic(X509EncodedKeySpec(PUBLIC_PREFIX + raw))
    }

    fun publicKeyOf(privateKey: PrivateKey): ByteArray = agree(privateKey, BASE_POINT)

    fun agree(privateKey: PrivateKey, peerPublicKey: ByteArray): ByteArray {
        val agreement = KeyAgreement.getInstance(ALGORITHM)
        agreement.init(privateKey)
        agreement.doPhase(publicKey(peerPublicKey), true)
        val shared = agreement.generateSecret()
        if (shared.all { it == 0.toByte() }) throw InvalidKeyException("X25519 produced the all-zero secret")
        return shared
    }

    private fun rawPublicKey(key: PublicKey): ByteArray {
        val encoded = key.encoded
        check(encoded.size == PUBLIC_PREFIX.size + X25519_KEY_BYTES && encoded.copyOf(PUBLIC_PREFIX.size)
            .contentEquals(PUBLIC_PREFIX)) { "the XDH provider did not generate an X25519 key" }
        return encoded.copyOfRange(PUBLIC_PREFIX.size, encoded.size)
    }
}
