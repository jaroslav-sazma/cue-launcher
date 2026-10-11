package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TileSize

enum class PriorityFormError {

    EMPTY,

    NOT_A_NUMBER,

    NEGATIVE,

    TOO_LARGE,
}

fun priorityError(text: String): PriorityFormError? {
    val value = text.trim()
    return when {
        value.isEmpty() -> PriorityFormError.EMPTY
        value.startsWith(MINUS) && value.drop(1).isDigitsOnly() -> PriorityFormError.NEGATIVE
        !value.isDigitsOnly() -> PriorityFormError.NOT_A_NUMBER
        value.toIntOrNull() == null -> PriorityFormError.TOO_LARGE
        else -> null
    }
}

fun parsePriority(text: String): Int? =
    if (priorityError(text) == null) text.trim().toIntOrNull() else null

data class PriorityPeer(
    val name: String,
    val priority: Int,

    val fallback: Boolean,
    val isDraft: Boolean = false,
)

fun priorityPeers(
    scenarios: List<Scenario>,
    size: TileSize,
    draft: PriorityPeer,

    excludeId: String? = null,
): List<PriorityPeer> {
    val peers = scenarios
        .filter { it.size == size && it.id != excludeId }
        .map { PriorityPeer(name = it.name, priority = it.priority, fallback = it.isFallback) }
    return (peers + draft).sortedWith(PEER_ORDER)
}

private val PEER_ORDER = compareBy<PriorityPeer> { it.fallback }
    .thenByDescending { it.priority }
    .thenBy { it.name.lowercase() }

private const val MINUS = "-"

private fun String.isDigitsOnly(): Boolean = isNotEmpty() && all(Char::isDigit)
