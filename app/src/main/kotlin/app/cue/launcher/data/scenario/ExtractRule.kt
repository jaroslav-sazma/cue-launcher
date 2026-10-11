package app.cue.launcher.data.scenario

import app.cue.launcher.engine.NOTIFICATION_FIELD_TEXT
import app.cue.launcher.engine.NOTIFICATION_FIELD_TITLE
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ExtractRule(
    val name: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val after: String? = null,
    val kind: ExtractKind,

    // Read only for kind until; any other kind ignores it and tidying drops it.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val until: String? = null,
) {
    // A text that folds to nothing, such as bare combining marks, counts as blank.
    val anchor: String? get() = after?.trim()?.takeIf { extractKey(it).isNotEmpty() }

    val marker: String? get() = until?.trim()?.takeIf { kind == ExtractKind.UNTIL && extractKey(it).isNotEmpty() }
}

@Serializable
enum class ExtractKind(val jsonName: String) {
    @SerialName("number")
    NUMBER("number"),

    @SerialName("code")
    CODE("code"),

    @SerialName("amount")
    AMOUNT("amount"),

    @SerialName("time")
    TIME("time"),

    @SerialName("date")
    DATE("date"),

    @SerialName("word")
    WORD("word"),

    @SerialName("line")
    LINE("line"),

    @SerialName("until")
    UNTIL("until"),
    ;

    companion object {
        fun fromJsonName(jsonName: String): ExtractKind? = entries.firstOrNull { it.jsonName == jsonName }
    }
}

enum class ExtractProblem {

    NAME,

    RESERVED_NAME,

    DUPLICATE_NAME,

    MISSING_UNTIL,

    ANCHOR_TOO_LONG,

    MARKER_TOO_LONG,
}

const val EXTRACT_MAX_RULES = 4

const val EXTRACT_NAME_MAX_LENGTH = 32

const val EXTRACT_MARKER_MAX_LENGTH = 64

val EXTRACT_RESERVED_NAMES: Set<String> = setOf(NOTIFICATION_FIELD_TITLE, NOTIFICATION_FIELD_TEXT)

fun isExtractName(name: String): Boolean =
    name.length in 1..EXTRACT_NAME_MAX_LENGTH && name.all { it in 'a'..'z' || it in '0'..'9' || it == '_' }

fun extractProblem(rule: ExtractRule, earlier: List<ExtractRule> = emptyList()): ExtractProblem? = when {
    !isExtractName(rule.name) -> ExtractProblem.NAME
    rule.name in EXTRACT_RESERVED_NAMES -> ExtractProblem.RESERVED_NAME
    earlier.any { it.name == rule.name } -> ExtractProblem.DUPLICATE_NAME
    (rule.anchor?.length ?: 0) > EXTRACT_MARKER_MAX_LENGTH -> ExtractProblem.ANCHOR_TOO_LONG
    rule.kind == ExtractKind.UNTIL && rule.marker == null -> ExtractProblem.MISSING_UNTIL
    (rule.marker?.length ?: 0) > EXTRACT_MARKER_MAX_LENGTH -> ExtractProblem.MARKER_TOO_LONG
    else -> null
}

fun extractProblems(rules: List<ExtractRule>): List<Pair<ExtractRule, ExtractProblem>> =
    rules.mapIndexedNotNull { index, rule ->
        extractProblem(rule, rules.subList(0, index))?.let { rule to it }
    }

fun tidyExtractRules(rules: List<ExtractRule>): List<ExtractRule> = rules.map { rule ->
    rule.copy(name = rule.name.trim(), after = rule.anchor, until = rule.marker)
}

// Read from the raw JSON, so a kind from a newer version is named in the defect instead of the whole
// scenario turning into an unreadable one.
internal fun unknownExtractKinds(condition: JsonObject): List<String> =
    (condition[FIELD_EXTRACT] as? JsonArray).orEmpty()
        .mapNotNull { rule -> ((rule as? JsonObject)?.get(FIELD_EXTRACT_KIND) as? JsonPrimitive)?.content }
        .filter { ExtractKind.fromJsonName(it) == null }
