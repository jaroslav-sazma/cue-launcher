package app.cue.launcher.system.bridge

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.IOException
import java.math.BigInteger
import java.net.Socket
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import javax.security.auth.x500.X500Principal

internal class LocalIdentity(val privateKey: PrivateKey, val certificate: X509Certificate) {

    val fingerprint: ByteArray = sha256(certificate.encoded)
}

// The phone's one TLS identity for as long as it is paired. Unpairing and turning the bridge off
// delete it with the other keys, and the next pairing creates a fresh one.
@SingleIn(AppScope::class)
@Inject
class BridgeIdentity {

    @Synchronized
    internal fun ensure(): LocalIdentity = load() ?: create()

    @Synchronized
    fun forget() {
        try {
            keyStore().deleteEntry(ALIAS)
        } catch (failure: GeneralSecurityException) {
            BridgeLog.info("the bridge identity could not be deleted: ${failure.javaClass.simpleName}")
        } catch (failure: IOException) {
            BridgeLog.info("the bridge identity could not be deleted: ${failure.javaClass.simpleName}")
        }
    }

    private fun load(): LocalIdentity? {
        val entry = keyStore().getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry
        val certificate = entry?.certificate as? X509Certificate
        return certificate?.let { LocalIdentity(entry.privateKey, it) }
    }

    // Conscrypt signs the TLS 1.3 CertificateVerify by handing Keystore a digest it computed itself
    // ("NONEwithECDSA"), so DIGEST_NONE has to be allowed next to SHA-256, which signs the certificate.
    private fun create(): LocalIdentity {
        val now = System.currentTimeMillis()
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER)
        generator.initialize(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_NONE)
                .setCertificateSubject(X500Principal(SUBJECT))
                .setCertificateSerialNumber(BigInteger(SERIAL_BITS, SecureRandom()).add(BigInteger.ONE))
                .setCertificateNotBefore(Date(now - VALIDITY_SLACK_MS))
                .setCertificateNotAfter(Date(now + VALIDITY_MS))
                .build(),
        )
        generator.generateKeyPair()
        return checkNotNull(load()) { "Keystore did not keep the bridge identity" }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    internal companion object {
        const val ALIAS = "cue.bridge.tls"
        private const val PROVIDER = "AndroidKeyStore"
        private const val CURVE = "secp256r1"
        private const val SUBJECT = "CN=Cue: launcher"
        private const val SERIAL_BITS = 63
        private const val VALIDITY_SLACK_MS = 24L * 60 * 60 * 1000
        private const val VALIDITY_MS = 100L * 365 * 24 * 60 * 60 * 1000
    }
}

internal class IdentityKeyManager(private val identity: LocalIdentity) : X509ExtendedKeyManager() {

    private val alias = BridgeIdentity.ALIAS

    override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) =
        alias

    override fun chooseEngineClientAlias(
        keyType: Array<out String>?,
        issuers: Array<out Principal>?,
        engine: SSLEngine?,
    ) = alias

    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(alias)

    override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
        arrayOf(identity.certificate).takeIf { alias == this.alias }

    override fun getPrivateKey(alias: String?): PrivateKey? = identity.privateKey.takeIf { alias == this.alias }

    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null

    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
}
