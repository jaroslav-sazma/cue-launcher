package app.cue.launcher.system.bridge

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine

class PinnedServerTrustTest {

    private val fixture = BridgeFixtures.load("pairing.json")
    private val mac = certificate("mac")
    private val phone = certificate("phone")
    private val macFingerprint = fingerprint("mac")

    private fun entry(name: String) = fixture.objects("certificates").single { it.str("name") == name }

    private fun certificate(name: String): X509Certificate = CertificateFactory.getInstance("X.509")
        .generateCertificate(ByteArrayInputStream(entry(name).hex("der"))) as X509Certificate

    private fun fingerprint(name: String) = entry(name).hex("fingerprint")

    @Test
    fun `the pinned certificate is trusted`() {
        val trust = PinnedServerTrust(macFingerprint)

        trust.checkServerTrusted(arrayOf(mac), AUTH)
        trust.checkServerTrusted(arrayOf(mac), AUTH, null as Socket?)
        trust.checkServerTrusted(arrayOf(mac), AUTH, null as SSLEngine?)

        assertFalse(trust.rejected)
    }

    @Test
    fun `any other certificate is refused and remembered as a pin mismatch`() {
        val trust = PinnedServerTrust(macFingerprint)

        assertThrows(CertificateException::class.java) { trust.checkServerTrusted(arrayOf(phone), AUTH) }

        assertTrue(trust.rejected)
    }

    // Only the leaf counts: a trusted certificate further up the chain proves nothing.
    @Test
    fun `the pin is checked against the leaf only`() {
        val trust = PinnedServerTrust(macFingerprint)

        trust.checkServerTrusted(arrayOf(mac, phone), AUTH)
        assertThrows(CertificateException::class.java) { trust.checkServerTrusted(arrayOf(phone, mac), AUTH) }
    }

    @Test
    fun `an empty chain is refused`() {
        listOf(PinnedServerTrust(macFingerprint), PinnedServerTrust(null)).forEach { trust ->
            assertThrows(CertificateException::class.java) { trust.checkServerTrusted(emptyArray(), AUTH) }
            assertThrows(CertificateException::class.java) { trust.checkServerTrusted(null, AUTH) }
        }
    }

    // Spec, transport security: a pairing connection takes whatever leaf the Mac shows; the ceremony
    // puts its fingerprint into the transcript.
    @Test
    fun `a pairing connection accepts any leaf`() {
        val trust = PinnedServerTrust(null)

        trust.checkServerTrusted(arrayOf(mac), AUTH)
        trust.checkServerTrusted(arrayOf(phone), AUTH)

        assertFalse(trust.rejected)
        assertThrows(CertificateException::class.java) { trust.checkClientTrusted(arrayOf(mac), AUTH) }
    }

    @Test
    fun `the phone never trusts a client`() {
        val trust = PinnedServerTrust(macFingerprint)

        assertThrows(CertificateException::class.java) { trust.checkClientTrusted(arrayOf(mac), AUTH) }
        assertThrows(CertificateException::class.java) {
            trust.checkClientTrusted(arrayOf(mac), AUTH, null as Socket?)
        }
        assertThrows(CertificateException::class.java) {
            trust.checkClientTrusted(arrayOf(mac), AUTH, null as SSLEngine?)
        }
        assertTrue(trust.acceptedIssuers.isEmpty())
    }

    @Test
    fun `the fixture fingerprints are those of the certificates`() {
        assertTrue(sha256(mac.encoded).contentEquals(macFingerprint))
        assertTrue(sha256(phone.encoded).contentEquals(fingerprint("phone")))
    }

    private companion object {
        const val AUTH = "ECDHE_ECDSA"
    }
}
