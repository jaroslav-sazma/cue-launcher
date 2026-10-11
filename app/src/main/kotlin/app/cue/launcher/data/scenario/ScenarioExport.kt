package app.cue.launcher.data.scenario

fun portableScenario(scenario: Scenario): Scenario = portableScenario(scenario, PlaceholderKeys())

fun portableScenarios(scenarios: List<Scenario>): List<Scenario> {
    val keys = PlaceholderKeys()
    return scenarios.map { portableScenario(it, keys) }
}

private class PlaceholderKeys {

    private val assigned = mutableMapOf<String, String>()

    private val counts = mutableMapOf<String, Int>()

    fun keyFor(prefix: String, value: String): String = assigned.getOrPut("$prefix\u0000$value") {
        val next = (counts[prefix] ?: 0) + 1
        counts[prefix] = next
        "${prefix}_$next"
    }
}

private const val PREFIX_BT = "bt"
private const val PREFIX_WIFI = "wifi"

private fun portableScenario(scenario: Scenario, keys: PlaceholderKeys): Scenario = scenario.copy(
    conditions = scenario.conditions.map { portableCondition(it, keys) },
    content = portableContent(scenario.content),
)

private fun portableCondition(condition: Condition, keys: PlaceholderKeys): Condition = when (condition) {
    is Condition.BtConnected -> condition.copy(mac = portable(condition.mac, PREFIX_BT, keys))
    is Condition.BtDisconnected -> condition.copy(mac = portable(condition.mac, PREFIX_BT, keys))

    is Condition.Wifi -> condition.copy(ssid = portable(condition.ssid, PREFIX_WIFI, keys))
    else -> condition
}

internal fun portableContent(content: TileContent): TileContent = when (content) {

    is TileContent.AppWidget -> content.boundAppWidgetId
        ?.let { id -> content.copy(appWidgetId = placeholderFor(widgetPlaceholderKey(id))) }
        ?: content

    else -> content
}

private fun portable(value: String?, prefix: String, keys: PlaceholderKeys): String? = when {
    value.isNullOrBlank() -> value
    hasPlaceholder(value) -> value
    else -> placeholderFor(keys.keyFor(prefix, value))
}
