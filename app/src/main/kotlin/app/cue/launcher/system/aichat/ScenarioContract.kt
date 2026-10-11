package app.cue.launcher.system.aichat

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionType
import app.cue.launcher.data.scenario.ExtractKind
import app.cue.launcher.data.scenario.ExtractRule
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.SCENARIO_SCHEMA_VERSION
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames

// Fields, their types, whether they are required and every enum value come from the serializers
// themselves, so the reference cannot drift from what the reader accepts; ScenarioSemantics.kt says
// what each of them means.

private val CONDITION_SUBCLASSES: SerialDescriptor = Condition.serializer().descriptor.sealedSubclasses()

private val CONTENT_SUBCLASSES: SerialDescriptor = TileContent.serializer().descriptor.sealedSubclasses()

private val ACTION_SUBCLASSES: SerialDescriptor = TapAction.serializer().descriptor.sealedSubclasses()

internal val CONDITION_TYPE_NAMES: List<String> = CONDITION_SUBCLASSES.elementNames.toList()

// The descriptor lists subclasses alphabetically; the meanings give the order a reader expects, and
// a kind they lack sorts first only to fail at its meaning.
internal val CONTENT_KINDS: List<String> =
    CONTENT_SUBCLASSES.elementNames.sortedBy { CONTENT_MEANINGS.keys.indexOf(it) }

internal val ACTION_KINDS: List<String> = ACTION_SUBCLASSES.elementNames.sortedBy { ACTION_MEANINGS.keys.indexOf(it) }

internal fun scenarioReference(): String = buildString {
    appendLine("### Scenario object")
    appendLine("- `schemaVersion` (integer, required): always $SCENARIO_SCHEMA_VERSION.")
    appendFields(SCENARIO_OWNER, Scenario.serializer().descriptor, indent = "")
    appendLine()
    appendConditions()
    appendContent()
    appendLine("### Items")
    appendLine("Each object in a template's `items`:")
    appendFields(ITEM_OWNER, TemplateItem.serializer().descriptor, indent = "")
    appendLine()
    appendActions()
    appendLine("### Values from a notification")
    appendLine("Each rule in a notification condition's `extract` pulls one value out of the notification's")
    appendLine("title and text, which the tile's texts then read as `{notif:<name>}`:")
    appendFields(EXTRACT_OWNER, ExtractRule.serializer().descriptor, indent = "")
    appendLine("Kinds, by what the value looks like:")
    ExtractKind.entries.forEach { appendLine("- `${it.jsonName}`: ${it.meaning()}") }
}

private fun StringBuilder.appendConditions() {
    appendLine("### Conditions")
    appendLine("Each object in `conditions` names its type in `\"type\"`.")
    appendLine()
    appendLine("State conditions hold while a state lasts; they take `negate` and never `durationSec`:")
    ConditionType.entries.filterNot(ConditionType::isInstant).forEach { appendCondition(it) }
    appendLine()
    appendLine("Instant conditions fire once; they take `durationSec` and never `negate`:")
    ConditionType.entries.filter(ConditionType::isInstant).forEach { appendCondition(it) }
    appendLine()
}

private fun StringBuilder.appendCondition(type: ConditionType) {
    appendLine("- `${type.jsonName}`: ${type.meaning()}")
    appendFields(type.jsonName, CONDITION_SUBCLASSES.subclass(type.jsonName), indent = NESTED)
}

private fun StringBuilder.appendContent() {
    appendLine("### Content")
    appendLine("`content` names its kind in `\"kind\"`.")
    CONTENT_KINDS.forEach { kind ->
        appendLine("- `$kind`: ${CONTENT_MEANINGS.getValue(kind)}")
        appendFields(kind, CONTENT_SUBCLASSES.subclass(kind), indent = NESTED)
    }
    appendLine()
    appendLine("Layouts of a template; the scenario's `size` must be the layout's:")
    TemplateLayout.entries.forEach { appendLine("- `${it.jsonName}`: ${it.meaning()}") }
    appendLine()
    appendLine("Launcher widgets; the scenario's `size` must be the widget's:")
    LauncherWidgetType.entries.forEach { appendLine("- `${it.jsonName}`: ${it.meaning()}") }
    appendLine()
}

private fun StringBuilder.appendActions() {
    appendLine("### Actions")
    appendLine("Each action object names its kind in `\"kind\"`.")
    ACTION_KINDS.forEach { kind ->
        appendLine("- `$kind`: ${ACTION_MEANINGS.getValue(kind)}")
        appendFields(kind, ACTION_SUBCLASSES.subclass(kind), indent = NESTED)
    }
    appendLine()
    appendLine("System actions, the `action` of kind system:")
    SystemActionType.entries.forEach { appendLine("- `${it.jsonName}`: ${it.meaning()}") }
    appendLine()
}

private fun StringBuilder.appendFields(owner: String, descriptor: SerialDescriptor, indent: String) {
    for (index in 0..<descriptor.elementsCount) {
        val field = descriptor.getElementName(index)
        val type = TYPE_OVERRIDES["$owner.$field"] ?: typeOf(descriptor.getElementDescriptor(index))
        val presence = if (descriptor.isElementOptional(index)) "optional" else "required"
        appendLine("$indent- `$field` ($type, $presence): ${fieldNote(owner, field)}")
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun typeOf(descriptor: SerialDescriptor): String = when (descriptor.kind) {
    PrimitiveKind.STRING -> "string"
    PrimitiveKind.INT, PrimitiveKind.LONG -> "integer"
    PrimitiveKind.BOOLEAN -> "boolean"
    SerialKind.ENUM -> descriptor.elementNames.joinToString(" | ") { "\"$it\"" }
    StructureKind.LIST -> "array of ${typeOf(descriptor.getElementDescriptor(0))}s"
    StructureKind.MAP -> "JSON object"
    else -> OBJECT_NAMES.getValue(descriptor.serialName.removeSuffix("?").substringAfterLast('.'))
}

private fun SerialDescriptor.subclass(name: String): SerialDescriptor = getElementDescriptor(getElementIndex(name))

// A sealed class serializes as a (type, value) pair; the value's descriptor lists every subclass.
private fun SerialDescriptor.sealedSubclasses(): SerialDescriptor = getElementDescriptor(1)

private val OBJECT_NAMES = mapOf(
    "Condition" to "condition object",
    "TileContent" to "content object",
    "TemplateItem" to "item object",
    "TapAction" to "action object",
    "ExtractRule" to "extract rule object",
)

// The item's action reads a lone object as a list of one, which its descriptor cannot tell.
private val TYPE_OVERRIDES = mapOf("$ITEM_OWNER.action" to "action object, or array of action objects")

internal const val SCENARIO_OWNER = "scenario"
internal const val ITEM_OWNER = "item"
internal const val EXTRACT_OWNER = "extract"
private const val NESTED = "  "
