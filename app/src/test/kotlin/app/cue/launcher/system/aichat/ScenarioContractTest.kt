package app.cue.launcher.system.aichat

import app.cue.launcher.data.aichat.CandidateCheck
import app.cue.launcher.data.aichat.checkCandidate
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionType
import app.cue.launcher.data.scenario.EndEventType
import app.cue.launcher.data.scenario.ExtractKind
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.RingerModeState
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.ScenarioFormat
import app.cue.launcher.data.scenario.ScenarioRead
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.data.scenario.WifiMode
import app.cue.launcher.data.scenario.hasPlaceholder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScenarioContractTest {

    private val reference = scenarioReference()

    @Test
    fun `the condition types of the format are exactly the enum's`() {
        assertEquals(ConditionType.entries.map { it.jsonName }.toSet(), CONDITION_TYPE_NAMES.toSet())
    }

    @Test
    fun `every content kind and every action kind has its meaning, and no stale one is left`() {
        assertEquals(listOf("template", "launcher_widget", "app_widget"), CONTENT_KINDS)
        assertEquals(CONTENT_KINDS.toSet(), CONTENT_MEANINGS.keys)
        assertEquals(
            listOf("app", "shortcut", "deeplink", "capability", "activity", "system", "ha"),
            ACTION_KINDS,
            "in the order a reader expects",
        )
        assertEquals(ACTION_KINDS.toSet(), ACTION_MEANINGS.keys)
    }

    @Test
    fun `every condition, content and action is described with its fields`() {
        (ConditionType.entries.map { it.jsonName } + CONTENT_KINDS + ACTION_KINDS).forEach { name ->
            val entry = entryOf(name)
            assertTrue(entry.lines().drop(1).all { it.startsWith("  - `") }, entry)
        }
        assertTrue("  - `from` (string, required): HH:mm, 24-hour" in entryOf("time_window"))
        assertTrue("  - `durationSec` (integer, optional): " in entryOf("alarm"))
        assertTrue("  - `negate` (boolean, optional): " in entryOf("charging"))
        assertTrue("  - `service` (string, required): " in entryOf("ha"))
        assertTrue("  - `data` (JSON object, optional): " in entryOf("ha"))
        assertTrue("  - `items` (array of item objects, optional): " in entryOf("template"))
        assertTrue("  - `extract` (array of extract rule objects, optional): " in entryOf("notification"))
        assertTrue("- `action` (action object, or array of action objects, required): " in reference)
    }

    @Test
    fun `state conditions take negate and instant ones durationSec`() {
        ConditionType.entries.forEach { type ->
            val entry = entryOf(type.jsonName)
            assertEquals(type.isInstant, "`durationSec`" in entry, type.jsonName)
            assertEquals(!type.isInstant, "`negate`" in entry, type.jsonName)
        }
    }

    @Test
    fun `instant conditions point to the end events, extract rules keep their own until`() {
        val until = "  - `until` (end event object, optional): the event that ends the tile, see End events"
        ConditionType.entries.forEach { type ->
            assertEquals(type.isInstant, entryOf(type.jsonName).lines().any { it.startsWith(until) }, type.jsonName)
        }
        val extract = reference.substringAfter("### Values from a notification").lines()
        assertTrue(
            extract.any { it.startsWith("- `until` (string, optional): only for kind until: the text that ends") },
            reference,
        )
    }

    @Test
    fun `every end event is taught with exactly its own fields`() {
        val section = reference.substringAfter("### End events\n").substringBefore("### Content")
        val lines = section.lines()

        assertEquals(EndEventType.entries.map { it.jsonName }.toSet(), END_EVENT_TYPE_NAMES.toSet())
        EndEventType.entries.forEach { type ->
            val start = lines.indexOfFirst { it.startsWith("- `${type.jsonName}`: Ends the tile") }
            assertTrue(start >= 0, type.jsonName)
            val fields = lines.drop(start + 1).takeWhile { it.startsWith("  - ") }
            assertTrue(fields.none { "`durationSec`" in it || "`extract`" in it || "`until`" in it }, type.jsonName)
        }
        assertTrue("  - `package` (string, required): " in section, section)
        assertTrue("  - `code` (string, required): " in section, section)
        assertTrue("  - `at` (string, required): HH:mm, 24-hour" in section, section)
        val endDays = "  - `days` (array of integers, optional): ISO weekdays, 1 = Monday to 7 = Sunday, at least one"
        assertTrue(endDays in section, section)
        assertTrue("upper bound" in section && "restarts the tile" in section, section)
    }

    @Test
    fun `every enum value of the format is spelled out`() {
        val values = TileSize.entries.map { it.jsonName } +
            listOf(WifiMode.ANY, WifiMode.SSID).map { it.name.lowercase() } +
            RingerModeState.entries.map { it.name.lowercase() } +
            TemplateLayout.entries.map { it.jsonName } +
            LauncherWidgetType.entries.map { it.jsonName } +
            SystemActionType.entries.map { it.jsonName } +
            ExtractKind.entries.map { it.jsonName }
        values.forEach { assertTrue("\"$it\"" in reference, it) }
    }

    @Test
    fun `every layout states its size and item count`() {
        TemplateLayout.entries.forEach { layout ->
            val line = reference.lineSequence().single { it.startsWith("- `${layout.jsonName}`: ") }
            assertTrue("size \"${layout.size.jsonName}\"" in line, line)
            assertTrue("exactly ${layout.itemCount} item" in line, line)
        }
    }

    @Test
    fun `a launcher widget that cannot be a tile says so`() {
        LauncherWidgetType.entries.forEach { widget ->
            val line = reference.lineSequence().single { it.startsWith("- `${widget.jsonName}`: ") }
            assertEquals(widget.size == null, "cannot be a scenario tile" in line, line)
        }
    }

    @Test
    fun `every system action and extract kind is described`() {
        SystemActionType.entries.forEach { action ->
            assertTrue(reference.lineSequence().any { it.startsWith("- `${action.jsonName}`: ") }, action.jsonName)
        }
        ExtractKind.entries.forEach { kind ->
            assertTrue(reference.lineSequence().any { it.startsWith("- `${kind.jsonName}`: ") }, kind.jsonName)
        }
    }

    @Test
    fun `the alarm is the phone's alarm clock and a doorbell is a Home Assistant event`() {
        assertTrue("alarm clock" in entryOf("alarm") && "ha_event" in entryOf("alarm"))
        assertTrue("doorbell" in entryOf("ha_event"))
    }

    @Test
    fun `the remembered place is taught with both of its actions`() {
        val placeSaved = entryOf("place_saved")

        assertTrue("remember_place" in placeSaved && "show_place" in placeSaved, placeSaved)
    }

    @Test
    fun `an app widget is always left for the user to bind`() {
        assertTrue("{{PLACEHOLDER:widget}}" in entryOf("app_widget"))
    }

    @Test
    fun `every example passes the import gate as a single scenario`() {
        SCENARIO_EXAMPLES.forEach { example ->
            val read = ScenarioFormat.read(example.json)

            val ok = assertInstanceOf(ScenarioRead.Ok::class.java, read, example.request)
            assertEquals(1, ok.accepted.size, example.request)
            assertTrue(ok.rejected.isEmpty(), example.request)
            assertInstanceOf(CandidateCheck.Valid::class.java, checkCandidate(example.json), example.request)
        }
    }

    @Test
    fun `an example shows an app widget with the duration and size its request states, left for the builder to bind`() {
        val example = SCENARIO_EXAMPLES.single { it.scenario().content is TileContent.AppWidget }
        val read = ScenarioFormat.read(example.json) as ScenarioRead.Ok
        val scenario = read.accepted.single().scenario
        val content = scenario.content as TileContent.AppWidget
        val widgetLine = example.inventory.single { it.startsWith("widget ") }

        assertTrue("15 minutes" in example.request && "as ${scenario.size.jsonName}" in example.request)
        val event = scenario.conditions.single() as Condition.InstantCondition
        assertEquals(900, event.durationSec)
        assertTrue("[" in widgetLine && scenario.size.jsonName in widgetLine.substringAfter('['), widgetLine)
        assertTrue(hasPlaceholder(content.appWidgetId))
        assertTrue(read.accepted.single().needsPlaceholders)
        assertEquals(
            "${content.packageName}/${content.provider}",
            widgetLine.substringAfter("→ "),
            "the provider as the inventory spells it",
        )
    }

    @Test
    fun `examples name only what their inventory lists or leave a placeholder`() {
        SCENARIO_EXAMPLES.forEach { example ->
            val identifiers = example.inventory.flatMap { line ->
                val identifier = line.substringAfter("→ ")
                val owned = line.startsWith("widget ") || line.startsWith("shortcut ")
                if (owned) identifier.split('/', limit = 2) else listOf(identifier)
            }
            IDENTIFIER.findAll(example.json).forEach { match ->
                val identifier = match.groupValues[1]
                assertTrue(identifier in identifiers || hasPlaceholder(identifier), "${example.request}: $identifier")
            }
        }
    }

    @Test
    fun `an example shows a Home Assistant entity the inventory lacks as a placeholder`() {
        val entities = SCENARIO_EXAMPLES.flatMap { example ->
            example.scenario().actions().filterIsInstance<TapAction.HaAction>().mapNotNull { it.entity }
        }

        assertTrue(entities.any(::hasPlaceholder))
    }

    @Test
    fun `an example opens a place in an app through its inventory shortcut`() {
        val example = SCENARIO_EXAMPLES.single { example ->
            example.scenario().actions().any { it is TapAction.Shortcut }
        }
        val shortcut = example.scenario().actions().filterIsInstance<TapAction.Shortcut>().single()

        assertTrue("shortcut Home → ${shortcut.packageName}/${shortcut.shortcutId}" in example.inventory)
        assertTrue("app Maps → ${shortcut.packageName}" in example.inventory, "the app line precedes its actions")
    }

    @Test
    fun `an example reads a value out of a notification into its text`() {
        val example = SCENARIO_EXAMPLES.single { example ->
            example.scenario().conditions.any { it is Condition.NotificationFrom }
        }
        val condition = example.scenario().conditions.single() as Condition.NotificationFrom
        val content = example.scenario().content as TileContent.Template

        val rule = condition.extract.single()
        assertTrue("{notif:${rule.name}}" in content.text.orEmpty(), content.text)
    }

    @Test
    fun `examples span condition types, content kinds and actions without repeating a trigger`() {
        val scenarios = SCENARIO_EXAMPLES.map { it.scenario() }
        val conditions = scenarios.flatMap { it.conditions }
        val actions = scenarios.flatMap { it.actions() }

        assertEquals(conditions.size, conditions.map { it::class }.toSet().size, conditions.toString())
        assertTrue(
            conditions.any { it is Condition.InstantCondition } && conditions.any { it is Condition.StateCondition },
            conditions.toString(),
        )
        assertEquals(
            setOf(TileContent.Template::class, TileContent.AppWidget::class),
            scenarios.map { it.content::class }.toSet(),
        )
        assertTrue(actions.any { it is TapAction.App } && actions.any { it is TapAction.HaAction }, actions.toString())
    }

    @Test
    fun `examples carry distinct ids`() {
        val ids = SCENARIO_EXAMPLES.map { it.scenario().id }

        assertEquals(ids.size, ids.toSet().size, ids.toString())
    }

    // The entry of one condition, content or action: its line and the field lines under it.
    private fun entryOf(name: String): String {
        val lines = reference.lines()
        val start = lines.indexOfFirst { it.startsWith("- `$name`: ") }
        assertTrue(start >= 0, name)
        val fields = lines.drop(start + 1).takeWhile { it.startsWith("  - ") }
        return (listOf(lines[start]) + fields).joinToString("\n")
    }

    private fun ScenarioExample.scenario(): Scenario =
        (ScenarioFormat.read(json) as ScenarioRead.Ok).accepted.single().scenario

    private fun Scenario.actions(): List<TapAction> =
        (content as? TileContent.Template)?.items.orEmpty().flatMap { it.actions }

    private companion object {
        val IDENTIFIER = Regex(""""(?:package|provider|mac|entity|shortcutId)"\s*:\s*"([^"]+)"""")
    }
}
