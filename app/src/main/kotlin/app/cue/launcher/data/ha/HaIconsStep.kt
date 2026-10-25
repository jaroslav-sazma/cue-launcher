package app.cue.launcher.data.ha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

// The version comes with the handshake, before the answer, so it is handed out on its own and the
// caller keeps it. icons is set once the exchange is over, and is null whenever it did not work out.
// An answer that refuses the request counts as one with no icons: an instance older than the
// command would refuse it again on every try until it is updated.
data class HaIconsStep(
    val send: String? = null,
    val version: String? = null,
    val done: Boolean = false,
    val icons: Map<String, Map<String, HaIconSet>>? = null,
)

// The domains are named on purpose: without them Home Assistant answers only for the integrations
// it has loaded, which right after a restart can be a handful.
fun haIconsStep(message: JsonObject, token: String, domains: Set<String>): HaIconsStep {
    val version = haVersionOf(message)
    return when (textOf(message[HA_LIVE_TYPE])) {
        HA_LIVE_AUTH_REQUIRED -> HaIconsStep(send = haLiveAuth(token), version = version)
        HA_LIVE_AUTH_OK -> HaIconsStep(send = request(domains), version = version)
        HA_LIVE_AUTH_INVALID -> HaIconsStep(done = true)
        RESULT -> result(message)
        else -> HaIconsStep()
    }
}

fun haVersionOf(message: JsonObject): String? = textOf(message[HA_VERSION])?.takeIf(String::isNotBlank)

private fun result(message: JsonObject): HaIconsStep {
    if ((message[HA_LIVE_ID] as? JsonPrimitive)?.intOrNull != ICONS_ID) return HaIconsStep()
    val succeeded = (message[SUCCESS] as? JsonPrimitive)?.booleanOrNull == true
    val resources = (message[RESULT] as? JsonObject)?.get(RESOURCES) as? JsonObject
    return HaIconsStep(done = true, icons = if (succeeded) resources?.let(::haIconsOf).orEmpty() else emptyMap())
}

private fun request(domains: Set<String>): String = Json.encodeToString(
    JsonObject.serializer(),
    buildJsonObject {
        put(HA_LIVE_ID, ICONS_ID)
        put(HA_LIVE_TYPE, GET_ICONS)
        put(CATEGORY, ENTITY_COMPONENT)
        put(INTEGRATION, buildJsonArray { domains.sorted().forEach { add(JsonPrimitive(it)) } })
    },
)

private fun textOf(element: JsonElement?): String? =
    (element as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content

private const val HA_VERSION = "ha_version"

private const val ICONS_ID = 1

private const val GET_ICONS = "frontend/get_icons"

private const val CATEGORY = "category"

private const val ENTITY_COMPONENT = "entity_component"

private const val INTEGRATION = "integration"

private const val RESULT = "result"

private const val SUCCESS = "success"

private const val RESOURCES = "resources"
