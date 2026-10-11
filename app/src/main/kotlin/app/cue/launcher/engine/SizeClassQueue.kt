package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.data.scenario.TriggerPayload

data class QueueEntry(
    val scenario: Scenario,
    val activation: Activation?,
    val accentedEntities: Set<String> = emptySet(),
) {

    val isFallback: Boolean get() = activation == null

    val payload: TriggerPayload get() = activation?.payload ?: TriggerPayload.EMPTY

    val isClosable: Boolean get() = activation?.expiresAt != null
}

data class SizeClassQueue(
    val size: TileSize,
    val entries: List<QueueEntry> = emptyList(),
) {
    val isEmpty: Boolean get() = entries.isEmpty()
}

val BENTO_SIZES: List<TileSize> = listOf(TileSize.HERO_4X2, TileSize.GRID_2X2, TileSize.GRID_1X1)

data class BentoQueues(private val queues: Map<TileSize, SizeClassQueue>) {

    operator fun get(size: TileSize): SizeClassQueue = queues[size] ?: SizeClassQueue(size)

    val hero: SizeClassQueue get() = get(TileSize.HERO_4X2)

    val medium: SizeClassQueue get() = get(TileSize.GRID_2X2)

    val small: SizeClassQueue get() = get(TileSize.GRID_1X1)

    companion object {
        val EMPTY = BentoQueues(BENTO_SIZES.associateWith { SizeClassQueue(it) })
    }
}

// This also settles a position several scenarios are pinned to: the first entry here wins the slot.
private val QUEUE_ORDER: Comparator<QueueEntry> = compareBy<QueueEntry> { it.isFallback }
    .thenByDescending { it.scenario.priority }
    .thenByDescending { it.activation?.since ?: 0L }
    .thenBy { it.scenario.name }
    .thenBy { it.scenario.id }

fun sizeClassQueues(
    scenarios: List<Scenario>,
    activations: List<Activation>,
    now: Long,
    haStates: Map<String, String> = emptyMap(),
): BentoQueues {
    val byScenario = activations.associateBy(Activation::scenarioId)
    val states = haStateValues(haStates)
    val entries = scenarios
        .filter { it.enabled && it.size in BENTO_SIZES && it.content.isRenderable }
        .mapNotNull { scenario -> scenario.queueEntry(byScenario[scenario.id], now, states) }
        .groupBy { it.scenario.size }
    return BentoQueues(
        BENTO_SIZES.associateWith { size ->
            SizeClassQueue(size, entries[size].orEmpty().sortedWith(QUEUE_ORDER))
        },
    )
}

private fun Scenario.queueEntry(
    activation: Activation?,
    now: Long,
    states: Map<String, String>,
): QueueEntry? {
    val running = activation?.takeUnless { it.isExpired(now) }
    return when {

        isFallback -> entryWithPayload(TriggerPayload.EMPTY, activation = null, states)
        running == null -> null
        else -> entryWithPayload(running.payload, running, states)
    }
}

private fun Scenario.entryWithPayload(
    payload: TriggerPayload,
    activation: Activation?,
    states: Map<String, String>,
): QueueEntry? {
    val resolved = resolveTemplateValues(content, payload, states) ?: return null
    return QueueEntry(
        scenario = if (resolved === content) this else copy(content = resolved),
        activation = activation,
        accentedEntities = haAccentedEntities(content, states),
    )
}

private val TileContent.isRenderable: Boolean
    get() = when (this) {
        is TileContent.AppWidget -> boundAppWidgetId != null
        is TileContent.LauncherWidget -> widget.size != null
        is TileContent.Template -> true
    }
