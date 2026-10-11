package app.cue.launcher.data.ha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

// /api/services names every service in English only; the other languages are reachable solely over
// the WebSocket API, as the frontend's own translations. names is set once the exchange is over, and
// is empty whenever it did not work out — the English names then stand.
data class HaServiceNamesStep(val send: String? = null, val names: Map<String, String>? = null)

fun haServiceNamesNeeded(language: String): Boolean = language != HA_ENGLISH

fun haServiceNamesStep(message: JsonObject, token: String, language: String): HaServiceNamesStep =
    when (textOf(message[HA_LIVE_TYPE])) {
        HA_LIVE_AUTH_REQUIRED -> HaServiceNamesStep(send = haLiveAuth(token))
        HA_LIVE_AUTH_OK -> HaServiceNamesStep(send = request(language))
        HA_LIVE_AUTH_INVALID -> HaServiceNamesStep(names = emptyMap())
        RESULT -> result(message)
        else -> HaServiceNamesStep()
    }

fun haServiceNamesOf(resources: JsonObject): Map<String, String> = resources.entries
    .mapNotNull { (key, value) ->
        val match = NAME_KEY.matchEntire(key)
        val name = textOf(value)?.trim()?.takeIf(String::isNotEmpty)
        if (match == null || name == null) null else "${match.groupValues[1]}.${match.groupValues[2]}" to name
    }
    .toMap()

private fun result(message: JsonObject): HaServiceNamesStep {
    if ((message[HA_LIVE_ID] as? JsonPrimitive)?.intOrNull != NAMES_ID) return HaServiceNamesStep()
    val succeeded = (message[SUCCESS] as? JsonPrimitive)?.booleanOrNull == true
    val resources = ((message[RESULT] as? JsonObject)?.get(RESOURCES) as? JsonObject).takeIf { succeeded }
    return HaServiceNamesStep(names = resources?.let(::haServiceNamesOf).orEmpty())
}

private fun request(language: String): String = json.encodeToString(
    JsonObject.serializer(),
    buildJsonObject {
        put(HA_LIVE_ID, NAMES_ID)
        put(HA_LIVE_TYPE, GET_TRANSLATIONS)
        put(LANGUAGE, language)
        put(CATEGORY, SERVICES)
    },
)

private fun textOf(element: JsonElement?): String? =
    (element as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content

private val json = Json { ignoreUnknownKeys = true }

private val NAME_KEY = Regex("""component\.([a-z0-9_]+)\.services\.([a-z0-9_]+)\.name""")

private const val HA_ENGLISH = "en"

private const val NAMES_ID = 1

private const val GET_TRANSLATIONS = "frontend/get_translations"

private const val LANGUAGE = "language"

private const val CATEGORY = "category"

private const val SERVICES = "services"

private const val RESULT = "result"

private const val SUCCESS = "success"

private const val RESOURCES = "resources"
