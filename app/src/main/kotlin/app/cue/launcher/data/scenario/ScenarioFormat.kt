package app.cue.launcher.data.scenario

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

internal val scenarioJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal val scenarioJsonPretty = Json(scenarioJson) {
    prettyPrint = true
    prettyPrintIndent = "  "
}

internal const val FIELD_SCHEMA_VERSION = "schemaVersion"
internal const val FIELD_SCENARIOS = "scenarios"
internal const val FIELD_NAME = "name"
internal const val FIELD_ID = "id"
internal const val FIELD_SIZE = "size"
internal const val FIELD_TYPE = "type"
internal const val FIELD_CONDITIONS = "conditions"
internal const val FIELD_DURATION = "durationSec"
internal const val FIELD_EXTRACT = "extract"
internal const val FIELD_EXTRACT_KIND = "kind"

object ScenarioFormat {

    fun toJsonObject(scenario: Scenario): JsonObject = buildJsonObject {
        put(FIELD_SCHEMA_VERSION, JsonPrimitive(SCENARIO_SCHEMA_VERSION))
        scenarioJson.encodeToJsonElement(scenario).jsonObject.forEach { (key, value) -> put(key, value) }
    }

    fun encodeScenario(scenario: Scenario): String = scenarioJsonPretty.encodeToString(toJsonObject(scenario))

    fun encodeSet(name: String, scenarios: List<Scenario>): String = scenarioJsonPretty.encodeToString(
        buildJsonObject {
            put(FIELD_SCHEMA_VERSION, JsonPrimitive(SCENARIO_SCHEMA_VERSION))
            put(FIELD_NAME, JsonPrimitive(name))
            put(FIELD_SCENARIOS, JsonArray(scenarios.map(::toJsonObject)))
        },
    )

    fun encodeConfig(scenarios: List<Scenario>, config: LauncherConfig): String {
        val configFields = scenarioJson.encodeToJsonElement(config).jsonObject
        return scenarioJsonPretty.encodeToString(
            buildJsonObject {
                put(FIELD_SCHEMA_VERSION, JsonPrimitive(SCENARIO_SCHEMA_VERSION))
                put(FIELD_SCENARIOS, JsonArray(scenarios.map(::toJsonObject)))
                configFields.forEach { (key, value) -> put(key, value) }
            },
        )
    }

    fun canonicalJson(scenario: Scenario): String = scenarioJson.encodeToString(toJsonObject(scenario))

    fun encodeConditions(conditions: List<Condition>): String = scenarioJson.encodeToString(conditions)

    fun decodeConditions(json: String): List<Condition> = runCatching {
        scenarioJson.decodeFromString<List<Condition>>(json)
    }.getOrDefault(emptyList())

    fun decodeCanonical(json: String): Scenario? = runCatching {
        scenarioJson.decodeFromString<Scenario>(json)
    }.getOrNull()

    fun read(text: String): ScenarioRead = readScenarioDocument(text)
}
