package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.data.scenario.bentoSlotCount
import app.cue.launcher.data.scenario.pinnablePositions

data class PinSlot(
    val position: Int,
    val selected: Boolean,
    val holders: List<String>,
    val pinnable: Boolean,
) {
    val shared: Boolean get() = holders.isNotEmpty()
}

// A position can be shared, and the bento draws whichever scenario comes first in queue order, so
// the names have to be listed the same way round: conditions before static, then the higher
// priority. Activation time, which the queue breaks ties with next, is not known here.
private val HOLDER_ORDER = compareBy<Scenario> { it.isFallback }
    .thenByDescending { it.priority }
    .thenBy { it.name.lowercase() }
    .thenBy { it.id }

fun pinHolders(scenarios: List<Scenario>, size: TileSize, excludeId: String?): Map<Int, List<String>> =
    scenarios
        .filter { it.size == size && it.id != excludeId }
        .mapNotNull { scenario -> scenario.pinnedPosition?.let { position -> position to scenario } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, holders) -> holders.sortedWith(HOLDER_ORDER).map { it.name } }

fun pinSlots(size: TileSize, selected: Int?, holders: Map<Int, List<String>>): List<PinSlot> {
    val pinnable = pinnablePositions(size)
    return List(bentoSlotCount(size)) { position ->
        PinSlot(
            position = position,
            selected = position == selected,
            holders = holders[position].orEmpty(),
            pinnable = position in pinnable,
        )
    }
}

// Sharing a position is legitimate, but an empty one is still the better opening offer.
fun defaultPin(size: TileSize, holders: Map<Int, List<String>>): Int? {
    val pinnable = pinnablePositions(size)
    return pinnable.firstOrNull { holders[it].isNullOrEmpty() } ?: pinnable.firstOrNull()
}
