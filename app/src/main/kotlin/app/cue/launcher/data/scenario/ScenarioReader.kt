package app.cue.launcher.data.scenario

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

sealed interface ScenarioRead {

    data class Ok(

        val setName: String? = null,
        val accepted: List<ImportedScenario> = emptyList(),
        val rejected: List<RejectedScenario> = emptyList(),

        val config: LauncherConfig? = null,

        val skipped: Int = 0,
    ) : ScenarioRead

    data class Failed(val defect: ScenarioDefect) : ScenarioRead
}

data class ImportedScenario(
    val scenario: Scenario,
    val placeholders: Set<String> = emptySet(),
) {
    val needsPlaceholders: Boolean get() = placeholders.isNotEmpty()

    fun asStored(): Scenario = if (needsPlaceholders) scenario.copy(enabled = false) else scenario
}

data class RejectedScenario(
    val id: String?,
    val name: String?,
    val defects: List<ScenarioDefect>,
)

private val CONFIG_FIELDS = listOf("staticZone", "appearance", "drawer", "gestures", "widgets")

// Far above any real set, yet small enough that a file of tiny scenarios cannot stall the preview
// or the import; the rest of the list is not even decoded, only counted.
const val MAX_IMPORTED_SCENARIOS = 500

internal fun readScenarioDocument(text: String): ScenarioRead {
    val root = runCatching { scenarioJson.parseToJsonElement(text) }.getOrNull() as? JsonObject
        ?: return ScenarioRead.Failed(ScenarioDefect.Malformed.NotJsonObject)

    val version = root.intFieldOrNull(FIELD_SCHEMA_VERSION) ?: SCENARIO_SCHEMA_VERSION
    val versionDefect = versionDefect(version)
    val scenarios = root[FIELD_SCENARIOS]

    return when {
        versionDefect != null -> ScenarioRead.Failed(versionDefect)
        scenarios == null -> readSingle(root)
        scenarios is JsonArray -> readCollection(root, scenarios)
        else -> ScenarioRead.Failed(ScenarioDefect.Malformed.ScenariosNotList)
    }
}

private fun readSingle(root: JsonObject): ScenarioRead =
    when (val outcome = readScenario(root)) {
        is ScenarioOutcome.Accepted -> ScenarioRead.Ok(accepted = listOf(outcome.scenario))
        is ScenarioOutcome.Rejected -> ScenarioRead.Ok(rejected = listOf(outcome.scenario))
    }

private fun readCollection(root: JsonObject, scenarios: JsonArray): ScenarioRead {
    val accepted = mutableListOf<ImportedScenario>()
    val rejected = mutableListOf<RejectedScenario>()
    scenarios.take(MAX_IMPORTED_SCENARIOS).forEach { element ->
        when (val outcome = readScenario(element)) {
            is ScenarioOutcome.Accepted -> accepted += outcome.scenario
            is ScenarioOutcome.Rejected -> rejected += outcome.scenario
        }
    }

    val isConfig = CONFIG_FIELDS.any { it in root }
    val config = if (isConfig) {
        runCatching { scenarioJson.decodeFromJsonElement<LauncherConfig>(root) }.getOrNull()
            ?: return ScenarioRead.Failed(ScenarioDefect.Malformed.ConfigUnreadable)
    } else {
        null
    }

    return ScenarioRead.Ok(
        setName = if (isConfig) null else root.stringFieldOrNull(FIELD_NAME),
        accepted = accepted,
        rejected = rejected,
        config = config,
        skipped = (scenarios.size - MAX_IMPORTED_SCENARIOS).coerceAtLeast(0),
    )
}

private fun readScenario(element: JsonElement): ScenarioOutcome {
    val scenario = element as? JsonObject
        ?: return rejected(
            id = null,
            name = null,
            defect = ScenarioDefect.Malformed.ScenarioNotObject,
        )
    return readScenarioObject(scenario)
}

private fun readScenarioObject(obj: JsonObject): ScenarioOutcome {
    val id = obj.stringFieldOrNull(FIELD_ID)
    val name = obj.stringFieldOrNull(FIELD_NAME)

    val version = obj.intFieldOrNull(FIELD_SCHEMA_VERSION) ?: SCENARIO_SCHEMA_VERSION
    val earlyDefects = versionDefect(version)?.let(::listOf) ?: rawDefects(obj)
    if (earlyDefects.isNotEmpty()) return ScenarioOutcome.Rejected(RejectedScenario(id, name, earlyDefects))

    val decoded = runCatching { scenarioJson.decodeFromJsonElement<Scenario>(obj) }
    val scenario = decoded.getOrNull()
    val defects = if (scenario == null) {
        listOf(ScenarioDefect.Malformed.Unreadable(decoded.exceptionOrNull()?.message))
    } else {
        validateScenario(scenario)
    }

    return if (scenario != null && defects.isEmpty()) {
        // An export always writes a placeholder for the widget id. A concrete one in a file would
        // point the tile at whatever widget this launcher already hosts under that number.
        val stored = scenario.copy(content = portableContent(scenario.content)).tidied()
        val widgetId = (stored.content as? TileContent.AppWidget)?.appWidgetId.orEmpty()
        val placeholders = placeholderKeysIn(obj) + placeholderKeysIn(widgetId)
        ScenarioOutcome.Accepted(ImportedScenario(stored, placeholders))
    } else {
        ScenarioOutcome.Rejected(RejectedScenario(id, name, defects))
    }
}

private fun rawDefects(scenario: JsonObject): List<ScenarioDefect> {
    val defects = mutableListOf<ScenarioDefect>()

    scenario.stringFieldOrNull(FIELD_SIZE)?.let { size ->
        if (TileSize.fromJsonName(size) == null) defects += ScenarioDefect.UnknownEnum(FIELD_SIZE, size)
    }

    (scenario[FIELD_CONDITIONS] as? JsonArray)?.forEach { element ->
        val condition = element as? JsonObject ?: return@forEach
        val rawType = condition.stringFieldOrNull(FIELD_TYPE)
        val type = rawType?.let(ConditionType::fromJsonName)
        if (type == null) {
            defects += ScenarioDefect.UnknownEnum("$FIELD_CONDITIONS[].$FIELD_TYPE", rawType.orEmpty())
            return@forEach
        }
        if (type.isInstant) {
            unknownUntilType(condition)?.let { until ->
                defects += ScenarioDefect.UnknownEnum("$FIELD_CONDITIONS[].$FIELD_UNTIL.$FIELD_TYPE", until)
            }
        } else {
            if (FIELD_DURATION in condition) defects += ScenarioDefect.DurationOnStateCondition(type.jsonName)
            if (FIELD_UNTIL in condition) defects += ScenarioDefect.UntilOnStateCondition(type.jsonName)
        }
        // Any other condition has no extract field, so a stray one is ignored like any unknown key.
        if (type != ConditionType.NOTIFICATION) return@forEach
        unknownExtractKinds(condition).forEach { kind ->
            defects += ScenarioDefect.UnknownEnum("$FIELD_CONDITIONS[].$FIELD_EXTRACT[].$FIELD_EXTRACT_KIND", kind)
        }
    }

    return defects
}

private fun versionDefect(version: Int): ScenarioDefect? = when {
    version > SCENARIO_SCHEMA_VERSION -> ScenarioDefect.UnsupportedSchemaVersion(version)
    version < SCENARIO_SCHEMA_VERSION -> ScenarioDefect.Malformed.UnknownVersion(version)
    else -> null
}

private fun rejected(id: String?, name: String?, defect: ScenarioDefect): ScenarioOutcome.Rejected =
    ScenarioOutcome.Rejected(RejectedScenario(id, name, listOf(defect)))

private fun JsonObject.stringFieldOrNull(field: String): String? =
    (this[field] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.intFieldOrNull(field: String): Int? =
    runCatching { this[field]?.jsonPrimitive?.int }.getOrNull()

private sealed interface ScenarioOutcome {
    data class Accepted(val scenario: ImportedScenario) : ScenarioOutcome
    data class Rejected(val scenario: RejectedScenario) : ScenarioOutcome
}
