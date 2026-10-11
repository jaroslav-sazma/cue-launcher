package app.cue.launcher.data.scenario

import app.cue.launcher.data.layout.LayoutContainer
import app.cue.launcher.engine.BentoSlot
import app.cue.launcher.engine.bentoLayoutOf
import app.cue.launcher.engine.sizeClassQueues
import app.cue.launcher.feature.scenarios.SystemAccess
import app.cue.launcher.feature.scenarios.jitRequirements
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

// The :baselineprofile journeys import this file into the measured app before they collect, and
// look for the names below on its home screen, so a drift here would quietly profile a different
// home screen — or fail the run on the device.
class BaselineProfileFixtureTest {

    private val read: ScenarioRead.Ok = readOk(FIXTURE.readText())

    private val config: LauncherConfig = requireNotNull(read.config) { "the fixture must be a whole configuration" }

    private val scenarios: List<Scenario> = read.accepted.map(ImportedScenario::asStored)

    @Test
    fun `the fixture reads without a defect`() {
        assertTrue(read.rejected.isEmpty()) { read.rejected.toString() }
        read.accepted.forEach { imported ->
            assertEquals(emptyList<ScenarioDefect>(), validateScenario(imported.scenario))
        }
    }

    @Test
    fun `the fixture survives a roundtrip`() {
        val original = read.accepted.map(ImportedScenario::scenario)

        val reread = readOk(ScenarioFormat.encodeConfig(original, config))

        assertEquals(original, reread.accepted.map(ImportedScenario::scenario))
        assertEquals(config, reread.config)
    }

    @Test
    fun `every scenario imports enabled and shows without a trigger`() {
        assertTrue(read.accepted.none(ImportedScenario::needsPlaceholders))
        assertTrue(scenarios.all { it.enabled && it.isFallback })
        assertTrue(scenarios.all { it.content is TileContent.Template })
    }

    @Test
    fun `the fixture asks for no permission`() {
        assertEquals(emptyList<Any>(), jitRequirements(scenarios, SystemAccess()))
    }

    @Test
    fun `the bento puts the journey's tile on top of a 2x2 stack and ends the 1x1 row in a dynamic folder`() {
        val clockSeed = clockSeedScenario("Clock")
        val layout = bentoLayoutOf(sizeClassQueues(listOf(clockSeed) + scenarios, emptyList(), now = 0))

        assertEquals(listOf(clockSeed.id), layout.hero.mapNotNull { it.entry?.scenario?.id }) {
            "the hero belongs to the clock seed"
        }
        assertTrue(layout.medium.none(BentoSlot::isEmpty) && layout.small.none(BentoSlot::isEmpty))

        val stack = layout.medium.last()
        assertTrue(stack.hasOverflow)
        assertEquals(SEEDED_TILE, (stack.entry?.scenario?.content as TileContent.Template).text)
        assertTrue(layout.small.last().hasOverflow)
    }

    @Test
    fun `the static zone holds the folder the journey looks for, the search bar and a full dock`() {
        val grid = config.staticZone.grid
        assertEquals(
            listOf(SEEDED_FOLDER),
            grid.filterIsInstance<StaticZoneEntry.Folder>().map(StaticZoneEntry.Folder::name),
        )
        assertTrue(grid.any { it is StaticZoneEntry.LauncherWidget && it.widget == LauncherWidgetType.SEARCHBAR })
        assertEquals(
            (0 until LayoutContainer.DOCK.slotCount).toList(),
            config.staticZone.dock.map(StaticZoneEntry::position),
        )
        assertEquals(
            grid.size + config.staticZone.dock.size,
            launcherItemsOf(config.staticZone, userSerial = 0).count { it.container != LayoutContainer.PANEL_DOCK },
        ) { "an entry does not fit the grid" }
    }

    @Test
    fun `the widget panel stays as a fresh install leaves it`() {
        assertEquals(
            listOf(PanelEntry.LauncherWidget(position = 0, widget = LauncherWidgetType.CALENDAR)),
            config.staticZone.widgetPanel.widgets,
        )
        assertTrue(config.staticZone.widgetPanel.dock.isEmpty())
    }

    @Test
    fun `only apps of a stock emulator image are referenced`() {
        val zone = config.staticZone
        val shortcuts = (zone.grid + zone.dock).flatMap { entry ->
            when (entry) {
                is StaticZoneEntry.Shortcut -> listOf(entry.packageName)
                is StaticZoneEntry.Folder -> entry.items.map(ComponentEntry::packageName)
                else -> emptyList()
            }
        }
        val actions = scenarios.flatMap { scenario ->
            (scenario.content as TileContent.Template).items.flatMap(TemplateItem::actions)
        }
        assertTrue(actions.all { it is TapAction.App || it is TapAction.SystemAction }) { actions.toString() }
        val apps = shortcuts + actions.filterIsInstance<TapAction.App>().map(TapAction.App::packageName)

        assertEquals(emptySet<String>(), apps.toSet() - STOCK_APPS)
    }

    private fun readOk(text: String): ScenarioRead.Ok {
        val read = ScenarioFormat.read(text)
        assertTrue(read is ScenarioRead.Ok) { read.toString() }
        return read as ScenarioRead.Ok
    }

    private companion object {

        // The SEEDED_FOLDER and SEEDED_TILE selectors in CueJourney.kt.
        const val SEEDED_FOLDER = "Tools"
        const val SEEDED_TILE = "Wind down"

        val STOCK_APPS = setOf(
            "com.android.settings",
            "com.android.chrome",
            "com.google.android.deskclock",
            "com.android.camera2",
        )

        val FIXTURE: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "fixtures/baselineprofile/home.json") }
            .first { it.isFile }
    }
}
