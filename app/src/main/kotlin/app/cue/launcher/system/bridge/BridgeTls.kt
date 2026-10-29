package app.cue.launcher.system.bridge

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedTrustManager

// Trust is the leaf certificate's SHA-256 and nothing else: chain, expiry, hostname and key usage
// carry no meaning in the protocol, and every certificate involved is self-signed anyway. Without an
// expected fingerprint — a pairing connection — any leaf passes; the ceremony takes the one the
// handshake showed into its transcript, and the Mac's code would differ for any other.
internal class PinnedServerTrust(private val expectedFingerprint: ByteArray?) : X509ExtendedTrustManager() {

    @Volatile
    var rejected: Boolean = false
        private set

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: reject("the server presented no certificate")
        if (expectedFingerprint != null && !MessageDigest.isEqual(sha256(leaf.encoded), expectedFingerprint)) {
            reject("the server certificate is not the pinned one")
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) =
        checkServerTrusted(chain, authType)

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) =
        checkServerTrusted(chain, authType)

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?): Unit =
        throw CertificateException("the phone never accepts TLS clients")

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?): Unit =
        throw CertificateException("the phone never accepts TLS clients")

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?): Unit =
        throw CertificateException("the phone never accepts TLS clients")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    private fun reject(reason: String): Nothing {
        rejected = true
        throw CertificateException(reason)
    }
}

internal class BridgePinMismatchException(cause: Throwable) :
    IOException("the peer certificate is not the expected one", cause)

internal interface BridgeCarrier : Closeable {

    fun setReadTimeout(millis: Int)
}

// Bytes as they come off the carrier, below the framing and any record layer, so a frame that takes
// long to cross a slow carrier still shows that the link is alive.
internal class ReadActivity {

    @Volatile
    private var listener: (() -> Unit)? = null

    fun follow(listener: (() -> Unit)?) {
        this.listener = listener
    }

    fun arrived() {
        listener?.invoke()
    }
}

internal class ActivityInputStream(private val source: InputStream, private val activity: ReadActivity) :
    InputStream() {

    override fun read(): Int = source.read().also { if (it >= 0) activity.arrived() }

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        source.read(b, off, len).also { if (it > 0) activity.arrived() }

    override fun available(): Int = source.available()

    override fun close() {
        source.close()
    }
}

internal open class BridgeConnection(
    private val input: InputStream,
    private val output: OutputStream,
    private val carrier: BridgeCarrier,
    val activity: ReadActivity = ReadActivity(),
) : Closeable {

    private val writeLock = Any()

    fun send(message: BridgeMessage, binary: ByteArray? = null) {
        val frame = BridgeFraming.encode(message, binary)
        synchronized(writeLock) {
            output.write(frame)
            output.flush()
        }
    }

    fun read(): BridgeInbound? = BridgeFraming.read(input)

    fun setReadTimeout(millis: Int) {
        carrier.setReadTimeout(millis)
    }

    override fun close() {
        runCatching { carrier.close() }
    }
}

private class SocketCarrier(private val socket: Socket) : BridgeCarrier {

    override fun setReadTimeout(millis: Int) {
        socket.soTimeout = millis
    }

    override fun close() {
        socket.close()
    }
}

internal class TlsConnection private constructor(
    socket: SSLSocket,
    peerCertificate: ByteArray,
    activity: ReadActivity,
) : BridgeConnection(
    BufferedInputStream(ActivityInputStream(socket.inputStream, activity)),
    socket.outputStream,
    SocketCarrier(socket),
    activity,
) {

    constructor(socket: SSLSocket, peerCertificate: ByteArray) : this(socket, peerCertificate, ReadActivity())

    val peerFingerprint: ByteArray = sha256(peerCertificate)
}

// A peer that went away without closing — a Mac asleep, a phone that roamed — would otherwise hold the
// session open while everything sent on it is lost. android.system.OsConstants names none of the
// keepalive timings, so Linux's numbers stand in; a socket that refuses them still probes, later.
private fun keepAlive(socket: Socket) {
    socket.keepAlive = true
    runCatching {
        ParcelFileDescriptor.fromSocket(socket).use { descriptor ->
            val fd = descriptor.fileDescriptor
            Os.setsockoptInt(fd, OsConstants.IPPROTO_TCP, LINUX_TCP_KEEPIDLE, KEEPALIVE_IDLE_S)
            Os.setsockoptInt(fd, OsConstants.IPPROTO_TCP, LINUX_TCP_KEEPINTVL, KEEPALIVE_INTERVAL_S)
            Os.setsockoptInt(fd, OsConstants.IPPROTO_TCP, LINUX_TCP_KEEPCNT, KEEPALIVE_PROBES)
        }
    }.onFailure { BridgeLog.event("tcp keepalive timing not set: ${it.javaClass.simpleName}") }
}

private const val LINUX_TCP_KEEPIDLE = 4
private const val LINUX_TCP_KEEPINTVL = 5
private const val LINUX_TCP_KEEPCNT = 6
internal const val KEEPALIVE_IDLE_S = 30
internal const val KEEPALIVE_INTERVAL_S = 10
internal const val KEEPALIVE_PROBES = 3

@SingleIn(AppScope::class)
@Inject
class BridgeTlsClient(private val identity: BridgeIdentity) {

    internal suspend fun connectForPairing(host: String, port: Int): TlsConnection = connect(host, port, null)

    // Blocking socket calls do not notice cancellation, so a watcher closes the socket being opened when
    // the caller goes away; the connection is either returned open or closed, never leaked.
    internal suspend fun connect(host: String, port: Int, expectedFingerprint: ByteArray?): TlsConnection =
        coroutineScope {
            val opening = AtomicReference<Socket?>(null)
            val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    opening.getAndSet(null)?.let { runCatching { it.close() } }
                }
            }
            try {
                withContext(Dispatchers.IO) {
                    val job = coroutineContext.job
                    connectBlocking(host, port, expectedFingerprint) { socket ->
                        opening.set(socket)
                        if (!job.isActive) runCatching { socket.close() }
                    }
                }.also { opening.set(null) }
            } finally {
                watcher.cancel()
            }
        }

    // A fresh SSLContext per connection leaves no session to resume, so every connection is a full
    // handshake that shows both certificates. The socket is connected through a bare address, which
    // gives Conscrypt no host name to put into SNI.
    private fun connectBlocking(
        host: String,
        port: Int,
        expectedFingerprint: ByteArray?,
        onSocket: (Socket) -> Unit,
    ): TlsConnection {
        val trust = PinnedServerTrust(expectedFingerprint)
        val context = SSLContext.getInstance(TLS_1_3)
        context.init(arrayOf(IdentityKeyManager(identity.ensure())), arrayOf(trust), null)
        val resolved = InetAddress.getByName(host)
        val address = InetAddress.getByAddress(resolved.address)
        val socket = context.socketFactory.createSocket() as SSLSocket
        onSocket(socket)
        try {
            socket.enabledProtocols = arrayOf(TLS_1_3)
            socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
            keepAlive(socket)
            socket.soTimeout = PAIRING_STEP_TIMEOUT_MS.toInt()
            handshake(socket, trust)
            return TlsConnection(socket, peerLeaf(socket, expectedFingerprint))
        } catch (failure: IOException) {
            runCatching { socket.close() }
            throw failure
        }
    }

    private fun peerLeaf(socket: SSLSocket, expectedFingerprint: ByteArray?): ByteArray {
        val leaf = socket.session.peerCertificates.firstOrNull() as? X509Certificate
            ?: throw IOException("the server presented no certificate")
        val der = leaf.encoded
        if (expectedFingerprint != null && !MessageDigest.isEqual(sha256(der), expectedFingerprint)) {
            throw BridgePinMismatchException(CertificateException("the leaf changed after the handshake"))
        }
        return der
    }

    private fun handshake(socket: SSLSocket, trust: PinnedServerTrust) {
        try {
            socket.startHandshake()
        } catch (failure: IOException) {
            throw if (trust.rejected) BridgePinMismatchException(failure) else failure
        }
    }

    private companion object {
        const val TLS_1_3 = "TLSv1.3"
        const val CONNECT_TIMEOUT_MS = 10_000
    }
}
