package app.cue.launcher.feature.settings.ha

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.engine.haCodeKey
import app.cue.launcher.engine.haEventCode

data class HaPairingCode(

    val code: String,

    val scenarios: Int,

    val lastSeenAt: Long?,
) {
    val neverSeen: Boolean get() = lastSeenAt == null
}

fun haPairingCodes(scenarios: List<Scenario>, lastSeen: Map<String, Long>): List<HaPairingCode> =
    scenarios
        .flatMap { scenario -> scenario.instantConditions.filterIsInstance<Condition.HaEvent>().map { it.code } }
        .filter { it.isNotBlank() }
        .groupBy(::haCodeKey)
        .map { (key, codes) ->
            HaPairingCode(
                code = haEventCode(codes.first()).orEmpty(),
                scenarios = codes.size,
                lastSeenAt = lastSeen[key],
            )
        }
        .sortedWith(compareByDescending<HaPairingCode> { it.neverSeen }.thenBy { haCodeKey(it.code) })
