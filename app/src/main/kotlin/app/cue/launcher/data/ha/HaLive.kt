package app.cue.launcher.data.ha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

// The live channel as a state machine over the frames Home Assistant sends, kept out of the socket so
// it can be exercised without one.
data class HaLiveState(
    val authorized: Boolean = false,

    // An event actually arrived. A connection that authenticates and then dies has proven nothing, and
    // counting it as a working channel is what would leave a tile with a value that never changes.
    val streamed: Boolean = false,
    val values: Map<String, String> = emptyMap(),
)

data class HaLiveStep(
    val state: HaLiveState,
    val send: String? = null,
    val emit: Boolean = false,
    val refused: Boolean = false,
)

fun haLiveStep(
    state: HaLiveState,
    message: JsonObject,
    entities: Set<String>,
    token: String,
): HaLiveStep = when (textOf(message[HA_LIVE_TYPE])) {
    HA_LIVE_AUTH_REQUIRED -> HaLiveStep(state, send = haLiveAuth(token))
    HA_LIVE_AUTH_OK -> HaLiveStep(state.copy(authorized = true), send = haLiveSubscribe(entities))
    HA_LIVE_AUTH_INVALID -> HaLiveStep(state, refused = true)
    HA_LIVE_EVENT -> event(state, message[HA_LIVE_EVENT] as? JsonObject, entities)

    else -> HaLiveStep(state)
}

// The channel counts as working only for an event the launcher understood — one carrying an object it
// could read. A frame that merely says "event" and nothing the parser recognises proves no more than
// authenticating did: were it counted, a Home Assistant whose format had moved on would keep the
// caller from ever falling back to reading over REST, and every tile would freeze on its first value.
private fun event(state: HaLiveState, event: JsonObject?, entities: Set<String>): HaLiveStep = when (event) {
    null -> HaLiveStep(state)
    else -> HaLiveStep(
        state = state.copy(streamed = true, values = haLiveValues(state.values, event, entities)),
        emit = true,
    )
}

// The compressed state format: "a" adds, "c" changes with "+" for what the entity gained, "r" removes,
// and a state itself sits under "s". An entity that goes unavailable is dropped rather than shown, the
// same as on the REST path — a tile whose text cannot be resolved has to step aside.
fun haLiveValues(
    current: Map<String, String>,
    event: JsonObject,
    entities: Set<String>,
): Map<String, String> {
    val values = current.toMutableMap()
    (event[HA_LIVE_ADD] as? JsonObject)?.forEach { (entity, added) ->
        values.keep(entity, stateOf(added), entities)
    }
    (event[HA_LIVE_CHANGE] as? JsonObject)?.forEach { (entity, diff) ->
        values.keep(entity, stateOf((diff as? JsonObject)?.get(HA_LIVE_DIFF_ADD)), entities)
    }
    (event[HA_LIVE_REMOVE] as? JsonArray)?.forEach { entity ->
        textOf(entity)?.let { values -= it }
    }
    return values
}

fun haLiveAuth(token: String): String = encode(
    buildJsonObject {
        put(HA_LIVE_TYPE, HA_LIVE_AUTH)
        put(HA_LIVE_ACCESS_TOKEN, token)
    },
)

fun haLiveSubscribe(entities: Set<String>): String = encode(
    buildJsonObject {
        put(HA_LIVE_ID, HA_LIVE_SUBSCRIPTION_ID)
        put(HA_LIVE_TYPE, HA_LIVE_SUBSCRIBE_ENTITIES)
        put(HA_LIVE_ENTITY_IDS, buildJsonArray { entities.forEach { add(JsonPrimitive(it)) } })
    },
)

// A channel that keeps dropping right after it opened is reconnected ever more slowly instead of every
// five seconds forever: each attempt is a socket, a subscription and the whole state set again, which on
// a proxy that closes the upgrade is a lot of radio for nothing. A session that held for a while starts
// over from the short delay.
fun haLiveWait(previous: Long, settled: Boolean): Long = when {
    settled -> HA_LIVE_RECONNECT_MILLIS
    else -> (previous * BACKOFF_FACTOR).coerceAtMost(HA_LIVE_POLL_MILLIS)
}

const val HA_LIVE_RECONNECT_MILLIS = 5_000L

const val HA_LIVE_POLL_MILLIS = 60_000L

const val HA_LIVE_SETTLED_MILLIS = 30_000L

private const val BACKOFF_FACTOR = 2

private fun MutableMap<String, String>.keep(entity: String, state: String?, entities: Set<String>) {
    if (entity !in entities || state == null) return
    if (haStateUsable(state)) put(entity, state) else remove(entity)
}

private fun stateOf(element: JsonElement?): String? = textOf((element as? JsonObject)?.get(HA_LIVE_STATE))

private fun textOf(element: JsonElement?): String? = (element as? JsonPrimitive)?.contentOrNull

private fun encode(message: JsonObject): String = json.encodeToString(JsonObject.serializer(), message)

private val json = Json { ignoreUnknownKeys = true }

internal const val HA_LIVE_TYPE = "type"

internal const val HA_LIVE_ID = "id"

private const val HA_LIVE_ACCESS_TOKEN = "access_token"

private const val HA_LIVE_ENTITY_IDS = "entity_ids"

private const val HA_LIVE_AUTH = "auth"

internal const val HA_LIVE_AUTH_REQUIRED = "auth_required"

internal const val HA_LIVE_AUTH_OK = "auth_ok"

internal const val HA_LIVE_AUTH_INVALID = "auth_invalid"

// The frame type and the field the event itself sits in are the same word.
private const val HA_LIVE_EVENT = "event"

private const val HA_LIVE_SUBSCRIBE_ENTITIES = "subscribe_entities"

private const val HA_LIVE_SUBSCRIPTION_ID = 1

private const val HA_LIVE_ADD = "a"

private const val HA_LIVE_CHANGE = "c"

private const val HA_LIVE_REMOVE = "r"

private const val HA_LIVE_DIFF_ADD = "+"

private const val HA_LIVE_STATE = "s"
