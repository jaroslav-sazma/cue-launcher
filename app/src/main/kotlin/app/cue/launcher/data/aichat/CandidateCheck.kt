package app.cue.launcher.data.aichat

import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.ScenarioDefect
import app.cue.launcher.data.scenario.ScenarioFormat
import app.cue.launcher.data.scenario.ScenarioRead
import app.cue.launcher.data.scenario.description
import app.cue.launcher.data.scenario.scenarioJson
import kotlinx.serialization.json.JsonObject

sealed interface CandidateProblem {

    data class Defect(val defect: ScenarioDefect) : CandidateProblem

    data class ScenarioCount(val count: Int) : CandidateProblem

    data class Ungrounded(val problem: GroundingProblem) : CandidateProblem
}

fun CandidateProblem.description(): String = when (this) {
    is CandidateProblem.Defect -> defect.description()
    is CandidateProblem.ScenarioCount -> "Expected exactly one scenario, got $count."
    is CandidateProblem.Ungrounded -> problem.description()
}

sealed interface CandidateCheck {

    data class Valid(val scenario: Scenario) : CandidateCheck

    data class Invalid(val problems: List<CandidateProblem>) : CandidateCheck
}

// Exactly the import gate: what it would reject may not reach the builder either, and a draft left
// with placeholders arrives disabled just as an import does.
fun checkCandidate(raw: String): CandidateCheck {
    val read = ScenarioFormat.read(candidateBody(raw))
    if (read is ScenarioRead.Failed) return CandidateCheck.Invalid(listOf(CandidateProblem.Defect(read.defect)))

    read as ScenarioRead.Ok
    val count = read.accepted.size + read.rejected.size + read.skipped
    val problems = buildList {
        if (count != 1) add(CandidateProblem.ScenarioCount(count))
        read.rejected.flatMap { it.defects }.forEach { add(CandidateProblem.Defect(it)) }
    }
    return if (problems.isEmpty()) {
        CandidateCheck.Valid(read.accepted.single().asStored())
    } else {
        CandidateCheck.Invalid(problems)
    }
}

// A chat wraps the JSON in a Markdown fence and prose that may hold braces of its own — a placeholder
// it names, an entity in braces — so the first ```json block wins, then a bare fence holding an
// object, then the first whole object that parses. Whatever goes to the gate when nothing parses is
// the most likely candidate, so the user sees what is wrong with it rather than "no JSON".
internal fun candidateBody(raw: String): String {
    fencedBody(raw)?.let { return it }
    val scan = scanObjects(raw)
    return scan.objects.firstOrNull(::isJsonObject)
        ?: scan.cutFrom?.let(raw::substring)
        ?: scan.objects.firstOrNull()
        ?: raw
}

private fun fencedBody(raw: String): String? {
    val blocks = FENCE.findAll(raw).map { it.groupValues[1].lowercase() to it.groupValues[2].trim() }.toList()
    return blocks.firstOrNull { (lang, _) -> lang == "json" }?.second
        ?: blocks.firstOrNull { (lang, body) -> lang.isEmpty() && body.startsWith('{') }?.second
}

private class ObjectScan(val objects: List<String>, val cutFrom: Int?)

// One pass with a stack of open braces. A brace left open that is followed by a key starts an object
// cut short, and what closes inside it is only a fragment; a stray one in the prose cannot swallow the
// object after it. Quotes count only inside a brace, where they are JSON strings; outside, they are
// prose.
private fun scanObjects(text: String): ObjectScan {
    val open = ArrayDeque<Int>()
    val spans = ArrayDeque<IntRange>()
    var inString = false
    var escaped = false
    text.forEachIndexed { index, char ->
        when {
            inString -> when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }

            char == '"' && open.isNotEmpty() -> inString = true
            char == '{' -> open.addLast(index)
            char == '}' && open.isNotEmpty() -> {
                val start = open.removeLast()
                while (spans.lastOrNull()?.let { it.first > start } == true) spans.removeLast()
                spans.addLast(start..index)
            }
        }
    }
    val cutFrom = open.firstOrNull { brace ->
        val next = (brace + 1..<text.length).firstOrNull { !text[it].isWhitespace() }
        next == null || text[next] == '"'
    }
    val whole = spans.filter { cutFrom == null || it.first < cutFrom }
    return ObjectScan(whole.map { text.substring(it) }, cutFrom)
}

private fun isJsonObject(text: String): Boolean =
    runCatching { scenarioJson.parseToJsonElement(text) }.getOrNull() is JsonObject

private val FENCE = Regex("```[ \\t]*([A-Za-z0-9_+-]*)[^\\n]*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
