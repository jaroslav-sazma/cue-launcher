package app.cue.launcher.system.ha

import android.util.Log
import app.cue.launcher.data.ha.HA_API_PATH
import app.cue.launcher.data.ha.HA_STATES_PATH
import app.cue.launcher.data.ha.HaEndpoint
import app.cue.launcher.data.ha.HaEntityList
import app.cue.launcher.data.ha.HaProbe
import app.cue.launcher.data.ha.HaServiceCall
import app.cue.launcher.data.ha.haAuthorizationHeader
import app.cue.launcher.data.ha.haEntitiesOf
import app.cue.launcher.data.ha.haNeverSent
import app.cue.launcher.data.ha.haServiceBody
import app.cue.launcher.data.ha.haServicePath
import app.cue.launcher.data.ha.haWebsocketUrl
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import java.net.SocketTimeoutException

@SingleIn(AppScope::class)
@Inject
class HaClient(private val http: HaHttp) {

    suspend fun probe(endpoint: HaEndpoint, token: String): HaProbe =
        timed(token) { runProbe(endpoint, token) }

    suspend fun callService(endpoint: HaEndpoint, token: String, call: HaServiceCall): HaProbe =
        timed(token) { runCall(endpoint, token, call) }

    suspend fun entities(endpoint: HaEndpoint, token: String): HaEntityList =
        if (token.isBlank()) HaEntityList(HaProbe.NotConfigured) else runEntities(endpoint, token)

    // The live channel: one socket follows every referenced entity, so a visible tile changes with
    // the instance instead of with the next poll. It is an accelerator only — the REST read stays the
    // source of truth, and a proxy that refuses the upgrade just falls back to it.
    @Suppress("TooGenericExceptionCaught")
    suspend fun <R> socket(
        endpoint: HaEndpoint,
        purpose: String = "Live channel",
        block: suspend (WebSocketSession) -> R,
    ): R? = try {
        var result: R? = null
        http.client.webSocket(urlString = haWebsocketUrl(endpoint.url)) { result = block(this) }
        result
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        Log.i(TAG, "$purpose unavailable at ${endpoint.kind} (${cause::class.java.simpleName})")
        null
    }

    private suspend inline fun timed(token: String, call: () -> HaProbe): HaProbe {
        if (token.isBlank()) return HaProbe.NotConfigured
        val started = System.nanoTime()
        val result = call()
        val millis = (System.nanoTime() - started) / NANOS_PER_MILLI
        return if (result is HaProbe.Ok) result.copy(millis = millis) else result
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runProbe(endpoint: HaEndpoint, token: String): HaProbe = try {
        val api = request(endpoint.url + HA_API_PATH, token)
        when {
            !api.status.isSuccess() -> HaProbe.Http(api.status.value)
            else -> {
                val states = request(endpoint.url + HA_STATES_PATH, token)
                if (states.status.isSuccess()) {
                    HaProbe.Ok(entities = entityCount(states.bodyAsText()), millis = 0L)
                } else {
                    HaProbe.Http(states.status.value)
                }
            }
        }
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        Log.i(TAG, "HA unreachable at ${endpoint.kind} (${cause::class.java.simpleName})")
        HaProbe.Network(cause.message ?: cause::class.simpleName)
    }

    // The action form asks from the main thread, and on a large instance the state list runs to
    // megabytes of JSON; decoding and parsing it there would stall the form.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun runEntities(endpoint: HaEndpoint, token: String): HaEntityList = try {
        val states = request(endpoint.url + HA_STATES_PATH, token)
        if (states.status.isSuccess()) {
            val entities = withContext(Dispatchers.Default) {
                val body = states.bodyAsText()
                runCatching { haEntitiesOf(http.json.parseToJsonElement(body)) }.getOrDefault(emptyList())
            }
            HaEntityList(HaProbe.Ok(entities = entities.size, millis = 0L), entities)
        } else {
            HaEntityList(HaProbe.Http(states.status.value))
        }
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        Log.i(TAG, "Entities not listed at ${endpoint.kind} (${cause::class.java.simpleName})")
        HaEntityList(HaProbe.Network(cause.message ?: cause::class.simpleName))
    }

    // A service call runs to completion before Home Assistant answers, so it gets a timeout of its
    // own — and a timeout is reported as such, never as a failure: the gate may well have opened.
    // For the same reason only a request that never left the phone is allowed to be tried on the
    // other address; anything that broke later is reported as sent, so the call cannot run twice.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun runCall(endpoint: HaEndpoint, token: String, call: HaServiceCall): HaProbe = try {
        val response = http.callClient.post(endpoint.url + haServicePath(call)) {
            header(HttpHeaders.Authorization, haAuthorizationHeader(token))
            contentType(ContentType.Application.Json)
            timeout {
                requestTimeoutMillis = HaHttp.CALL_TIMEOUT_MILLIS
                socketTimeoutMillis = HaHttp.CALL_TIMEOUT_MILLIS
            }

            setBody(http.json.encodeToString(JsonObject.serializer(), haServiceBody(call)))
        }
        if (response.status.isSuccess()) {
            HaProbe.Ok(entities = entityCount(response.bodyAsText()), millis = 0L)
        } else {
            HaProbe.Http(response.status.value)
        }
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: HttpRequestTimeoutException) {
        Log.i(TAG, "Service call was not answered in time at ${endpoint.kind} (${cause::class.java.simpleName})")
        HaProbe.Timeout
    } catch (cause: SocketTimeoutException) {
        Log.i(TAG, "Service call was not answered in time at ${endpoint.kind} (${cause::class.java.simpleName})")
        HaProbe.Timeout
    } catch (cause: Exception) {
        Log.i(TAG, "Service call failed at ${endpoint.kind} (${cause::class.java.simpleName})")
        when {
            haNeverSent(cause) -> HaProbe.Network(cause.message ?: cause::class.simpleName)
            else -> HaProbe.Timeout
        }
    }

    private suspend fun request(url: String, token: String): HttpResponse = http.get(url, token)

    private fun entityCount(body: String): Int =
        runCatching { http.json.parseToJsonElement(body).jsonArray.size }.getOrDefault(0)

    private companion object {

        const val NANOS_PER_MILLI = 1_000_000L

        const val TAG = "HaClient"
    }
}
