package app.cue.launcher.engine

import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.data.scenario.bentoSlotCount
import app.cue.launcher.data.scenario.pinnablePositions

data class BentoSlot(
    val size: TileSize,
    val index: Int,
    val entry: QueueEntry? = null,
    val overflow: List<QueueEntry> = emptyList(),
) {
    val isEmpty: Boolean get() = entry == null

    val overflowCount: Int get() = overflow.size

    val hasOverflow: Boolean get() = overflow.isNotEmpty()

    val stackPages: List<QueueEntry> get() = if (entry == null) emptyList() else listOf(entry) + overflow
}

data class BentoLayout(
    val hero: List<BentoSlot>,
    val medium: List<BentoSlot>,
    val small: List<BentoSlot>,
) {
    companion object {
        val EMPTY = bentoLayoutOf(BentoQueues.EMPTY)
    }
}

fun bentoSlots(queue: SizeClassQueue): List<BentoSlot> {
    val count = bentoSlotCount(queue.size)
    val plan = slotPlan(queue, count)
    return List(count) { index ->
        BentoSlot(
            size = queue.size,
            index = index,
            entry = plan.slots[index],

            overflow = if (index == count - 1) plan.overflow else emptyList(),
        )
    }
}

fun bentoLayoutOf(queues: BentoQueues): BentoLayout = BentoLayout(
    hero = bentoSlots(queues.hero),
    medium = bentoSlots(queues.medium),
    small = bentoSlots(queues.small),
)

private class SlotPlan(val slots: Array<QueueEntry?>, val overflow: List<QueueEntry>)

private fun slotPlan(queue: SizeClassQueue, count: Int): SlotPlan {
    val pinnable = pinnablePositions(queue.size)
    val slots = arrayOfNulls<QueueEntry>(count)
    val floating = mutableListOf<QueueEntry>()

    queue.entries.forEach { entry ->
        // A position is shared, not owned: several scenarios may be pinned to it and the queue order
        // settles which one is drawn — conditions before static, then priority, then the most recent
        // activation. Entries arrive in that order, so the first to reach a free position takes it
        // and the rest flow on as if they had no pin. The range has to be cleared before the slot is
        // read, because a hand-written file can carry a position this class does not have.
        val position = entry.scenario.pinnedPosition
            ?.takeIf { it in pinnable }
            ?.takeIf { slots[it] == null }
        if (position == null) floating += entry else slots[position] = entry
    }

    // A pinned tile holds its index even when there is nothing to flow around it, which is the whole
    // point of pinning: the slots before it may well stay empty.
    val rest = floating.iterator()
    slots.indices.forEach { index -> if (slots[index] == null && rest.hasNext()) slots[index] = rest.next() }

    return SlotPlan(slots, rest.asSequence().toList())
}
