package app.cue.launcher.engine

import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.Scenario

fun interface HaEventLog {

    suspend fun record(event: HaEvent, matched: Int)
}

// Incoming codes are cut to HA_CODE_MAX_LENGTH, so a scenario's code has to go through the same cut —
// otherwise a longer one could never match, nor ever be marked as seen.
fun haCodeKey(code: String): String = haEventCode(code).orEmpty().lowercase()

// What the log counts as matched: every scenario waiting for the code, to start or to end, so a code
// that only ends tiles is neither reported as unknown nor left unmarked as seen.
fun haEventWaiters(scenarios: List<Scenario>, listeners: List<Scenario>, ended: List<String>, code: String): Int {
    val ending = scenarios.filter { scenario ->
        scenario.endsBy { it is EndEvent.HaEvent && endEventMatches(it, code) }
    }
    return (listeners.map(Scenario::id) + ending.map(Scenario::id) + ended).toSet().size
}
