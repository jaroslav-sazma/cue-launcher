package app.cue.launcher.system.ha

import android.app.Application
import app.cue.launcher.BuildConfig
import app.cue.launcher.data.ha.haAuthorizationHeader
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.OkHttp as OkHttpPlatform
import java.util.concurrent.TimeUnit

// One connection pool for everything that talks to Home Assistant. Reads, the sign-in and the live
// channel keep OkHttp's silent retry, which re-sends a request whose pooled connection the server had
// already closed. A service call goes through callClient without it: that retry would also re-send a
// POST whose connection broke after the request had left, and the call would run twice. Whether a
// service call may be tried again is decided in one place only, by haNeverSent.
@SingleIn(AppScope::class)
@Inject
class HaHttp(private val application: Application) {

    // Idle connections live half a minute rather than OkHttp's five: long enough for a quick return
    // to the home screen to skip a new TLS handshake, short enough that OkHttp's own cleanup has
    // closed them soon after the launcher left the screen.
    private val pool by lazy { ConnectionPool(MAX_IDLE_CONNECTIONS, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS) }

    val client: HttpClient by lazy { clientOf(retry = true) }

    val callClient: HttpClient by lazy { clientOf(retry = false) }

    // The manifest removes OkHttp's startup initializer, so the context its public suffix list reads
    // from assets arrives here, before any client exists.
    private fun clientOf(retry: Boolean): HttpClient {
        OkHttpPlatform.initialize(application)
        return HttpClient(OkHttp) {
            engine {
                config {
                    connectionPool(pool)
                    retryOnConnectionFailure(retry)
                }
            }
            expectSuccess = false
            // Home Assistant prints the User-Agent next to every refused request; the engine's
            // default "ktor-client" names every Ktor app on the network, this names the launcher.
            install(UserAgent) { agent = "Cue/${BuildConfig.VERSION_NAME}" }
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
                requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                socketTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            }
            // No client pings on purpose. The OkHttp engine never applies the plugin's ping interval
            // to its client, and keepalive is the server's job anyway: Home Assistant pings every
            // 55 s on its own, so a client ping would only be a second radio wake-up for the same
            // assurance. No maxFrameSize either, for the same reason: the OkHttp engine hands back its
            // own session, which the plugin never configures and whose setter throws.
            install(WebSockets)
        }
    }

    val json: Json = Json { ignoreUnknownKeys = true }

    suspend fun get(url: String, token: String): HttpResponse = client.get(url) {
        header(HttpHeaders.Authorization, haAuthorizationHeader(token))
    }

    companion object {

        const val MAX_IDLE_CONNECTIONS = 5

        const val KEEP_ALIVE_SECONDS = 30L

        const val CONNECT_TIMEOUT_MILLIS = 2_000L

        const val REQUEST_TIMEOUT_MILLIS = 5_000L

        // A service call runs to completion before Home Assistant answers, so a gate that takes its
        // time must not look like a failure.
        const val CALL_TIMEOUT_MILLIS = 20_000L

        // Signing in and renewing an access token happen right after the app was in the background,
        // where the first connection is the slowest one the launcher ever makes.
        const val AUTH_CONNECT_TIMEOUT_MILLIS = 8_000L

        const val AUTH_TIMEOUT_MILLIS = 15_000L
    }
}
