package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TriggerPayload
import java.security.MessageDigest
import java.lang.reflect.Array as JavaArray

const val HA_EVENT_ACTION = "app.cue.launcher.HA_EVENT"

const val HA_EXTRA_CODE = "code"

const val HA_EXTRA_OP = "op"

const val HA_EXTRA_ICON = "icon"

const val HA_EXTRA_SECRET = "secret"

// The blueprint stamps every event with the contract version it was written against, so an automation
// built from an outdated copy can be named instead of silently behaving differently.
const val HA_EXTRA_VERSION = "v"

const val HA_EVENT_VERSION = 1

const val HA_OP_END = "end"

internal val HA_RESERVED_EXTRAS =
    setOf(HA_EXTRA_CODE, HA_EXTRA_OP, HA_EXTRA_ICON, HA_EXTRA_SECRET, HA_EXTRA_VERSION)

private const val HA_VALUE_SEPARATOR = ";"

enum class HaEventOp {
    START,
    END,
}

const val HA_VALUE_NAMESPACE = "ha"

data class HaEvent(
    val code: String,
    val op: HaEventOp = HaEventOp.START,
    val icon: String? = null,
    val fields: Map<String, String> = emptyMap(),
    val version: Int = 0,
) {

    val legacy: Boolean get() = version < HA_EVENT_VERSION
}

// The receiver is exported — the companion app is a foreign process and would not reach an
// unexported one — so anything on the device can send this broadcast. The pairing secret is what
// separates the user's own automations from a foreign sender spoofing a tile. Compared in constant
// time so a wrong secret cannot be narrowed down one character at a time.
fun haEventSecretAccepted(expected: String, received: String?): Boolean {
    val wanted = expected.trim()
    val offered = received?.trim()
    return when {
        wanted.isEmpty() || offered.isNullOrEmpty() -> false
        else -> MessageDigest.isEqual(
            wanted.toByteArray(Charsets.UTF_8),
            offered.toByteArray(Charsets.UTF_8),
        )
    }
}

fun haEventSecretOf(extras: Map<String, Any?>): String? = haExtraText(extras[HA_EXTRA_SECRET])

fun haEventOf(extras: Map<String, Any?>): HaEvent? {
    val code = haEventCode(haExtraText(extras[HA_EXTRA_CODE])) ?: return null
    val end = haExtraText(extras[HA_EXTRA_OP])?.trim().equals(HA_OP_END, ignoreCase = true)
    val fields = extras.keys
        .filter(::haFieldKey)
        .sorted()
        .mapNotNull { key -> haExtraText(extras[key])?.let { key to it.take(HA_VALUE_MAX_LENGTH) } }
        .take(HA_FIELDS_MAX)
        .toMap()
    return HaEvent(
        code = code,
        op = if (end) HaEventOp.END else HaEventOp.START,
        icon = haExtraText(extras[HA_EXTRA_ICON])?.trim()?.takeIf(::haIconFits),
        fields = fields,
        version = haExtraText(extras[HA_EXTRA_VERSION])?.trim()?.toIntOrNull() ?: 0,
    )
}

fun haExtraText(value: Any?): String? = when {
    value == null -> null
    value is CharSequence || value is Number || value is Boolean || value is Char -> value.toString()
    value::class.java.isArray -> arrayElements(value).joinToString(HA_VALUE_SEPARATOR) {
        haExtraText(it).orEmpty()
    }

    value is Collection<*> -> value.joinToString(HA_VALUE_SEPARATOR) { haExtraText(it).orEmpty() }
    else -> null
}

fun haPayload(event: HaEvent): TriggerPayload = TriggerPayload(
    icon = event.icon,
    fields = event.fields.mapKeys { (key, _) -> "$HA_VALUE_NAMESPACE:${key.trim().lowercase()}" },
)

fun haEventMatches(condition: Condition.HaEvent, code: String): Boolean {
    val wanted = haCodeKey(condition.code)
    return wanted.isNotEmpty() && wanted == haCodeKey(code)
}

fun haEventTrigger(scenario: Scenario, code: String): Condition.HaEvent? = scenario.instantConditions
    .filterIsInstance<Condition.HaEvent>()
    .firstOrNull { haEventMatches(it, code) }

fun haEventListeners(scenarios: List<Scenario>, code: String): List<Scenario> =
    scenarios.filter { scenario -> scenario.enabled && haEventTrigger(scenario, code) != null }

fun dueHaEvents(scenarios: List<Scenario>, state: SystemState, code: String): List<Scenario> =
    haEventListeners(scenarios, code).filter { instantTriggerAllowed(it, state) }

private fun arrayElements(value: Any): List<Any?> =
    (0 until JavaArray.getLength(value)).map { index -> JavaArray.get(value, index) }
