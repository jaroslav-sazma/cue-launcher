package app.cue.launcher.data.ha

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

// features and deviceClass are what a service's target filter asks of an entity; null means the state
// did not report them.
data class HaEntity(
    val id: String,
    val name: String?,
    val features: Int? = null,
    val deviceClass: String? = null,
) {

    val domain: String get() = id.substringBefore('.')
}

data class HaEntityList(val probe: HaProbe, val entities: List<HaEntity> = emptyList())

val HA_ID_PATTERN = Regex("""[a-z0-9_]+\.[a-z0-9_]+""")

fun haEntitiesOf(states: JsonElement): List<HaEntity> = (states as? JsonArray).orEmpty()
    .mapNotNull { element ->
        val state = element as? JsonObject ?: return@mapNotNull null
        val id = (state["entity_id"] as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
            ?.takeIf(HA_ID_PATTERN::matches) ?: return@mapNotNull null
        val attributes = state["attributes"] as? JsonObject
        HaEntity(
            id = id,
            name = attributes.text("friendly_name")?.trim()?.takeIf(String::isNotEmpty),
            features = (attributes?.get("supported_features") as? JsonPrimitive)
                ?.takeUnless(JsonPrimitive::isString)?.intOrNull,
            deviceClass = attributes.text("device_class"),
        )
    }
    .distinctBy(HaEntity::id)
    .sortedBy(HaEntity::id)

private fun JsonObject?.text(key: String): String? =
    (this?.get(key) as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
