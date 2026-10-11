package app.cue.launcher.system.ha

import android.util.Log
import app.cue.launcher.data.ha.HaEndpoint
import app.cue.launcher.data.ha.HaProbe
import app.cue.launcher.data.ha.HaStateRead
import app.cue.launcher.data.ha.haStatePath
import app.cue.launcher.data.ha.haStateUsable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Reading the states a tile refers to: one GET per entity, all in flight at once over the shared
// pool. Not POST /api/template, although that would be a single round trip — Home Assistant only
// renders it for administrators, and every refused call lands in the instance log as a failed login
// and counts towards its IP ban. The per-entity read works for any account.
@SingleIn(AppScope::class)
@Inject
class HaStateClient(private val http: HaHttp) {

    suspend fun states(endpoint: HaEndpoint, token: String, entities: Set<String>): HaStateRead = when {
        token.isBlank() -> HaStateRead(HaProbe.NotConfigured)
        entities.isEmpty() -> HaStateRead.NONE
        else -> readAll(endpoint, token, entities)
    }

    private suspend fun readAll(
        endpoint: HaEndpoint,
        token: String,
        entities: Set<String>,
    ): HaStateRead = coroutineScope {
        val results = entities
            .map { entity -> async { entity to read(endpoint, token, entity) } }
            .awaitAll()
        val values = mutableMapOf<String, String>()
        var failure: HaProbe? = null
        for ((entity, result) in results) {
            when (result) {
                is HaStateResult.Value -> values[entity] = result.state

                // An unreadable state is not a transport failure: the instance answered. Reporting it
                // as one would keep the launcher from remembering which endpoint works.
                HaStateResult.Unusable -> Unit
                is HaStateResult.Failed -> failure = worst(failure, result.probe)
            }
        }
        HaStateRead(probe = failure ?: HaProbe.Ok(values.size, millis = 0L), values = values)
    }

    // A network failure outranks an HTTP one: it is the only kind that may send the whole read to the
    // other address.
    private fun worst(current: HaProbe?, next: HaProbe): HaProbe = when {
        current == null || next is HaProbe.Network -> next
        else -> current
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun read(endpoint: HaEndpoint, token: String, entity: String): HaStateResult = try {
        val response = http.get(endpoint.url + haStatePath(entity), token)
        when {
            !response.status.isSuccess() -> HaStateResult.Failed(HaProbe.Http(response.status.value))
            else -> stateOf(response.bodyAsText())?.let(HaStateResult::Value) ?: HaStateResult.Unusable
        }
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        Log.i(TAG, "A state was not read at ${endpoint.kind} (${cause::class.java.simpleName})")
        HaStateResult.Failed(HaProbe.Network(cause.message ?: cause::class.simpleName))
    }

    private fun stateOf(body: String): String? = runCatching {
        http.json.parseToJsonElement(body).jsonObject[STATE_FIELD]?.jsonPrimitive?.content
    }.getOrNull()?.takeIf(::haStateUsable)

    private sealed interface HaStateResult {
        data class Value(val state: String) : HaStateResult
        data object Unusable : HaStateResult
        data class Failed(val probe: HaProbe) : HaStateResult
    }

    private companion object {

        const val STATE_FIELD = "state"

        const val TAG = "HaStates"
    }
}
