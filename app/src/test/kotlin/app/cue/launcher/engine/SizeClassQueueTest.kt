package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.data.scenario.TriggerPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SizeClassQueueTest {

    private val now = 10_000L

    private fun running(id: String, since: Long = 1_000L, expiresAt: Long? = null) =
        Activation(scenarioId = id, since = since, expiresAt = expiresAt)

    private fun ids(queue: SizeClassQueue) = queue.entries.map { it.scenario.id }

    @Test
    fun `higher priority sits further left`() {
        val queues = sizeClassQueues(
            scenarios = listOf(scenario("low", priority = 1), scenario("high", priority = 5)),
            activations = listOf(running("low"), running("high")),
            now = now,
        )

        assertEquals(listOf("high", "low"), ids(queues.small))
    }

    @Test
    fun `with equal priority the newer activation wins`() {
        val queues = sizeClassQueues(
            scenarios = listOf(scenario("stary"), scenario("novy")),
            activations = listOf(running("stary", since = 1_000L), running("novy", since = 5_000L)),
            now = now,
        )

        assertEquals(listOf("novy", "stary"), ids(queues.small))
    }

    @Test
    fun `a fallback is always after a triggered one even with higher priority`() {
        val queues = sizeClassQueues(
            scenarios = listOf(fallback("trvaly", priority = 100), scenario("trigger", priority = 0)),
            activations = listOf(running("trigger")),
            now = now,
        )

        assertEquals(listOf("trigger", "trvaly"), ids(queues.small))
        assertFalse(queues.small.entries.first().isFallback)
        assertTrue(queues.small.entries.last().isFallback)
    }

    @Test
    fun `fallbacks are ordered among themselves by priority`() {
        val queues = sizeClassQueues(
            scenarios = listOf(fallback("a", priority = 1), fallback("b", priority = 9)),
            activations = emptyList(),
            now = now,
        )

        assertEquals(listOf("b", "a"), ids(queues.small))
    }

    @Test
    fun `equal fallback priority is decided by name`() {
        val scenarios = listOf(
            fallback("z").copy(name = "Ahoj"),
            fallback("a").copy(name = "Zebra"),
        )

        val forward = sizeClassQueues(scenarios, activations = emptyList(), now = now)
        val reversed = sizeClassQueues(scenarios.reversed(), activations = emptyList(), now = now)

        assertEquals(listOf("z", "a"), ids(forward.small))
        assertEquals(ids(forward.small), ids(reversed.small))
    }

    @Test
    fun `a fallback is in the queue without an activation`() {
        val queues = sizeClassQueues(listOf(fallback("trvaly")), activations = emptyList(), now = now)

        val entry = queues.small.entries.single()
        assertTrue(entry.isFallback)
        assertEquals(null, entry.activation)
    }

    @Test
    fun `a scenario with conditions and no activation is not in the queue`() {
        val queues = sizeClassQueues(listOf(scenario("ceka")), activations = emptyList(), now = now)

        assertTrue(queues.small.isEmpty)
    }

    @Test
    fun `an expired activation does not count`() {
        val queues = sizeClassQueues(
            scenarios = listOf(scenario("vyprsely"), scenario("bezi")),
            activations = listOf(
                running("vyprsely", expiresAt = now),
                running("bezi", expiresAt = now + 1),
            ),
            now = now,
        )

        assertEquals(listOf("bezi"), ids(queues.small))
    }

    @Test
    fun `a disabled scenario is not in the queue even with an activation`() {
        val queues = sizeClassQueues(
            scenarios = listOf(
                scenario("vypnuty").copy(enabled = false),
                fallback("vypnuty-trvaly").copy(enabled = false),
            ),
            activations = listOf(running("vypnuty")),
            now = now,
        )

        assertTrue(queues.small.isEmpty)
    }

    @Test
    fun `each size has its own queue`() {
        val queues = sizeClassQueues(
            scenarios = listOf(
                scenario("hero", size = TileSize.HERO_4X2),
                scenario("stredni", size = TileSize.GRID_2X2),
                scenario("maly"),
            ),
            activations = listOf(running("hero"), running("stredni"), running("maly")),
            now = now,
        )

        assertEquals(listOf("hero"), ids(queues.hero))
        assertEquals(listOf("stredni"), ids(queues.medium))
        assertEquals(listOf("maly"), ids(queues.small))
    }

    @Test
    fun `overlay does not end up in bento queues`() {
        val queues = sizeClassQueues(
            scenarios = listOf(scenario("over", size = TileSize.OVERLAY)),
            activations = listOf(running("over")),
            now = now,
        )

        assertEquals(BENTO_SIZES, listOf(TileSize.HERO_4X2, TileSize.GRID_2X2, TileSize.GRID_1X1))
        assertTrue(queues[TileSize.OVERLAY].isEmpty)
        assertTrue(queues.hero.isEmpty && queues.medium.isEmpty && queues.small.isEmpty)
    }

    @Test
    fun `a widget without a bound id is skipped`() {
        val queues = sizeClassQueues(
            scenarios = listOf(
                scenario(
                    id = "nenavazany",
                    size = TileSize.HERO_4X2,
                    content = TileContent.AppWidget(
                        packageName = "com.example",
                        provider = "com.example/.Provider",
                        appWidgetId = "{{PLACEHOLDER:widget_7}}",
                    ),
                ),
                scenario(
                    id = "navazany",
                    size = TileSize.HERO_4X2,
                    content = TileContent.AppWidget(
                        packageName = "com.example",
                        provider = "com.example/.Provider",
                        appWidgetId = "8",
                    ),
                ),
            ),
            activations = listOf(running("nenavazany"), running("navazany")),
            now = now,
        )

        assertEquals(listOf("navazany"), ids(queues.hero))
    }

    @Test
    fun `the queue substitutes the activation payload into the template`() {
        val content = TileContent.Template(
            layout = TemplateLayout.TEXT_ONLY,
            text = "Prádlo {ha:teplota} °C",
            items = listOf(TemplateItem(actions = listOf(TapAction.App(packageName = "com.example")))),
        )
        val queues = sizeClassQueues(
            scenarios = listOf(scenario("pracka", size = TileSize.GRID_2X2, content = content)),
            activations = listOf(
                running("pracka").copy(payload = TriggerPayload(fields = mapOf("ha:teplota" to "38"))),
            ),
            now = now,
        )

        val tile = queues.medium.entries.single().scenario.content as TileContent.Template
        assertEquals("Prádlo 38 °C", tile.text)
    }

    @Test
    fun `a scenario missing a template value yields to the fallback`() {
        val content = TileContent.Template(
            layout = TemplateLayout.TEXT_ONLY,
            text = "Prádlo {ha:teplota} °C",
            items = listOf(TemplateItem(actions = listOf(TapAction.App(packageName = "com.example")))),
        )
        val queues = sizeClassQueues(
            scenarios = listOf(
                scenario("pracka", size = TileSize.GRID_2X2, content = content),
                fallback("trvaly", size = TileSize.GRID_2X2),
            ),
            activations = listOf(running("pracka")),
            now = now,
        )

        assertEquals(listOf("trvaly"), ids(queues.medium))
    }

    @Test
    fun `only a tile on a countdown can be closed`() {
        val queues = sizeClassQueues(
            scenarios = listOf(scenario("pulz"), scenario("stav"), fallback("trvaly")),
            activations = listOf(running("pulz", expiresAt = now + 1_000L), running("stav")),
            now = now,
        )

        val closable = queues.small.entries.associate { it.scenario.id to it.isClosable }
        assertEquals(mapOf("pulz" to true, "stav" to false, "trvaly" to false), closable)
    }

    @Test
    fun `a tile waiting for an end event can be closed`() {
        val waiting = Activation("pulz", since = 1_000L, payload = TriggerPayload(until = EndEvent.Alarm))
        val queues = sizeClassQueues(scenarios = listOf(scenario("pulz")), activations = listOf(waiting), now = now)

        assertTrue(queues.small.entries.single().isClosable)
    }

    @Test
    fun `a searchbar as scenario content is skipped`() {
        val queues = sizeClassQueues(
            scenarios = listOf(
                scenario("searchbar", content = TileContent.LauncherWidget(LauncherWidgetType.SEARCHBAR)),
            ),
            activations = listOf(running("searchbar")),
            now = now,
        )

        assertTrue(queues.small.isEmpty)
    }

    @Test
    fun `empty input gives three empty queues`() {
        val queues = sizeClassQueues(scenarios = emptyList(), activations = emptyList(), now = now)

        assertEquals(BentoQueues.EMPTY, queues)
        BENTO_SIZES.forEach { size -> assertTrue(queues[size].isEmpty) }
    }

    @Test
    fun `an activation without a scenario is ignored`() {
        val queues = sizeClassQueues(
            scenarios = listOf(scenario("zivy")),
            activations = listOf(running("zivy"), running("smazany")),
            now = now,
        )

        assertEquals(listOf("zivy"), ids(queues.small))
    }
}
