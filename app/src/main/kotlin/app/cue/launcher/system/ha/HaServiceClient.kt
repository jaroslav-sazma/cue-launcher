package app.cue.launcher.system.ha

import android.util.Log
import app.cue.launcher.data.ha.HA_SERVICES_PATH
import app.cue.launcher.data.ha.HaEndpoint
import app.cue.launcher.data.ha.HaProbe
import app.cue.launcher.data.ha.HaServiceList
import app.cue.launcher.data.ha.haServiceNamesStep
import app.cue.launcher.data.ha.haServicesOf
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject

@SingleIn(AppScope::class)
@Inject
class HaServiceClient(private val http: HaHttp, private val client: HaClient) {

    suspend fun services(endpoint: HaEndpoint, token: String): HaServiceList =
        if (token.isBlank()) HaServiceList(HaProbe.NotConfigured) else read(endpoint, token)

    // Decoded and parsed off the main thread for the same reason as the state list in HaClient.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun read(endpoint: HaEndpoint, token: String): HaServiceList = try {
        val response = http.get(endpoint.url + HA_SERVICES_PATH, token)
        if (response.status.isSuccess()) {
            val services = withContext(Dispatchers.Default) {
                val body = response.bodyAsText()
                runCatching { haServicesOf(http.json.parseToJsonElement(body)) }.getOrDefault(emptyList())
            }
            HaServiceList(HaProbe.Ok(entities = services.size, millis = 0L), services)
        } else {
            HaServiceList(HaProbe.Http(response.status.value))
        }
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        Log.i(TAG, "Services not listed at ${endpoint.kind} (${cause::class.java.simpleName})")
        HaServiceList(HaProbe.Network(cause.message ?: cause::class.simpleName))
    }

    suspend fun names(endpoint: HaEndpoint, token: String, language: String): Map<String, String> =
        withTimeoutOrNull(NAMES_TIMEOUT_MILLIS) {
            client.socket(endpoint, purpose = "Service names") { session -> names(session, token, language) }
        }.orEmpty()

    private suspend fun names(session: WebSocketSession, token: String, language: String): Map<String, String> {
        for (frame in session.incoming) {
            val step = (frame as? Frame.Text)?.let { text ->
                withContext(Dispatchers.Default) {
                    runCatching { http.json.parseToJsonElement(text.readText()) as? JsonObject }.getOrNull()
                        ?.let { haServiceNamesStep(it, token, language) }
                }
            }
            step?.send?.let { session.send(Frame.Text(it)) }
            step?.names?.let { return it }
        }
        return emptyMap()
    }

    private companion object {

        const val NAMES_TIMEOUT_MILLIS = HaHttp.REQUEST_TIMEOUT_MILLIS

        const val TAG = "HaServiceClient"
    }
}
