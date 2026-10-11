package app.cue.launcher.data.scenario

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val PLACEHOLDER_PATTERN = Regex("""\{\{PLACEHOLDER:([^{}]+)\}\}""")

fun placeholderFor(key: String): String = "{{PLACEHOLDER:$key}}"

fun placeholderKeysIn(text: String): List<String> =
    PLACEHOLDER_PATTERN.findAll(text).map { it.groupValues[1] }.toList()

fun hasPlaceholder(text: String): Boolean = PLACEHOLDER_PATTERN.containsMatchIn(text)

fun placeholderKeysIn(scenario: Scenario): Set<String> =
    placeholderKeysIn(ScenarioFormat.toJsonObject(scenario))

fun placeholderKeysIn(element: JsonElement): Set<String> {
    val keys = linkedSetOf<String>()
    collectPlaceholders(element, keys)
    return keys
}

private fun collectPlaceholders(element: JsonElement, into: MutableSet<String>) {
    when (element) {
        is JsonObject -> element.values.forEach { collectPlaceholders(it, into) }
        is JsonArray -> element.forEach { collectPlaceholders(it, into) }
        is JsonPrimitive -> if (element.isString) into.addAll(placeholderKeysIn(element.content))
    }
}
