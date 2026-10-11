package app.cue.launcher.data.ha

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class HaServiceCall(
    val domain: String,
    val service: String,

    val entityId: String?,
    val data: JsonObject,
)

fun haServiceCallOf(
    service: String,
    entity: String? = null,
    data: JsonObject = JsonObject(emptyMap()),
): HaServiceCall? {
    val match = HA_SERVICE_PATTERN.matchEntire(service.trim().lowercase()) ?: return null
    return HaServiceCall(
        domain = match.groupValues[1],
        service = match.groupValues[2],
        entityId = entity?.trim()?.takeIf(String::isNotEmpty),
        data = data,
    )
}

fun haServicePath(call: HaServiceCall): String = "/api/services/${call.domain}/${call.service}"

fun haServiceBody(call: HaServiceCall): JsonObject {
    val entity = call.entityId ?: return call.data
    return JsonObject(call.data + (HA_ENTITY_ID_KEY to JsonPrimitive(entity)))
}

fun haStatePath(entityId: String): String = "/api/states/$entityId"

fun haEntityIdOrNull(raw: String): String? =
    raw.trim().lowercase().takeIf(HA_ENTITY_PATTERN::matches)

fun haStateUsable(state: String?): Boolean =
    state != null && state.isNotBlank() && state != HA_STATE_UNAVAILABLE && state != HA_STATE_UNKNOWN

private const val HA_ENTITY_ID_KEY = "entity_id"

private const val HA_STATE_UNAVAILABLE = "unavailable"

private const val HA_STATE_UNKNOWN = "unknown"

private val HA_SERVICE_PATTERN = Regex("""([a-z0-9_]+)\.([a-z0-9_]+)""")

private val HA_ENTITY_PATTERN = Regex("""[a-z][a-z0-9_]*\.[a-z0-9_]+""")
