package app.cue.launcher.feature.scenarios

import app.cue.launcher.data.scenario.Scenario

const val PRIORITY_SPACING = 10

data class PriorityUpdate(val id: String, val priority: Int)

data class PriorityMove(val priority: Int, val updates: List<PriorityUpdate>)

fun priorityMove(group: List<Scenario>, from: Int, to: Int): PriorityMove? {
    if (from !in group.indices || to !in group.indices || from == to) return null
    val order = group.reordered(from, to)
    val dragged = order[to]
    val above = order.getOrNull(to - 1)?.priority
    val below = order.getOrNull(to + 1)?.priority
    val between = priorityBetween(above, below)
    return if (between == null) {
        PriorityMove(priority = renumberedPriority(order.size, to), updates = renumber(order))
    } else {
        PriorityMove(priority = between, updates = listOf(PriorityUpdate(dragged.id, between)))
    }
}

fun priorityBetween(above: Int?, below: Int?): Int? = when {
    above == null && below == null -> null
    above == null -> below?.takeIf { it <= Int.MAX_VALUE - PRIORITY_SPACING }?.plus(PRIORITY_SPACING)
    below == null -> (above - PRIORITY_SPACING).takeIf { it >= 0 }
    above - below < 2 -> null
    else -> below + (above - below) / 2
}

private fun renumber(order: List<Scenario>): List<PriorityUpdate> =
    order.mapIndexedNotNull { index, scenario ->
        val priority = renumberedPriority(order.size, index)
        if (priority == scenario.priority) null else PriorityUpdate(scenario.id, priority)
    }

private fun renumberedPriority(size: Int, index: Int): Int = (size - 1 - index) * PRIORITY_SPACING
