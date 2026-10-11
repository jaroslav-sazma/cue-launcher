package app.cue.launcher.feature.scenarios

import app.cue.launcher.data.ha.HaProbe
import app.cue.launcher.data.ha.HaStateRead
import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionMatch
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.data.scenario.WifiMode
import app.cue.launcher.engine.SystemState
import app.cue.launcher.engine.WifiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScenarioDiagnosisTest {

    private val now = 1_000L
    private val home = Condition.Wifi(mode = WifiMode.SSID, ssid = "Doma")
    private val onHotel =
        SystemState(charging = true, wifi = WifiState(connected = true, ssid = "Hotel-Guest"))
    private val unmeasured = SystemState(wifi = WifiState(connected = true, ssid = null))

    @Test
    fun `a failing condition is the first cause and carries a red frame`() {
        val subject = scenario(conditions = listOf(Condition.Charging(), home))

        val diagnosis = diagnoseScenario(subject, onHotel, listOf(subject), emptyList(), now)

        assertFalse(diagnosis.conditions[0].blocking)
        assertTrue(diagnosis.conditions[1].blocking)
        assertEquals(1, diagnosis.conditions[1].index)
    }

    @Test
    fun `under any a holding sibling excuses the conditions that miss`() {
        val subject = scenario(conditions = listOf(Condition.Charging(), home))
            .copy(match = ConditionMatch.ANY)

        val diagnosis = diagnoseScenario(subject, SystemState(charging = true), listOf(subject), emptyList(), now)

        assertFalse(diagnosis.conditions[1].blocking)
        assertTrue(diagnosis.conditions.none { it.blocking })
    }

    @Test
    fun `under any nothing excuses the conditions once they all miss`() {
        val subject = scenario(conditions = listOf(Condition.Charging(), home))
            .copy(match = ConditionMatch.ANY)

        val diagnosis = diagnoseScenario(subject, onHotel.copy(charging = false), listOf(subject), emptyList(), now)

        assertTrue(diagnosis.conditions[0].blocking)
        assertTrue(diagnosis.conditions[1].blocking)
    }

    @Test
    fun `an unmeasured state is the second cause, not the first`() {
        val subject = scenario(conditions = listOf(home))

        val diagnosis = diagnoseScenario(subject, unmeasured, listOf(subject), emptyList(), now)

        assertFalse(diagnosis.conditions[0].blocking)
        assertEquals(LiveVerdict.UNKNOWN, diagnosis.conditions[0].reading.verdict)
    }

    @Test
    fun `waiting for an event is not a cause`() {
        val subject = scenario(conditions = listOf(Condition.HaEvent(code = "zvonek")))

        val diagnosis = diagnoseScenario(subject, onHotel, listOf(subject), emptyList(), now)

        assertTrue(diagnosis.conditions.none { it.blocking })
        assertEquals(ContentHealth.READY, diagnosis.content)
    }

    @Test
    fun `an unbound widget is the third cause`() {
        val subject = scenario().copy(
            content = TileContent.AppWidget(
                packageName = "com.example",
                provider = "com.example.Widget",
                appWidgetId = "{{PLACEHOLDER:widget_1}}",
            ),
        )

        val diagnosis = diagnoseScenario(subject, onHotel, listOf(subject), emptyList(), now)

        assertEquals(ContentHealth.WIDGET_UNBOUND, diagnosis.content)
    }

    @Test
    fun `a custom widget outside the bento zone is also the third cause`() {
        val subject = scenario().copy(content = TileContent.LauncherWidget(LauncherWidgetType.SEARCHBAR))

        assertEquals(ContentHealth.WIDGET_UNSUPPORTED, contentHealth(subject.content))
    }

    @Test
    fun `an unread state from HA is the third cause`() {
        val subject = scenario().copy(
            content = TileContent.Template(layout = TemplateLayout.TEXT_ONLY, text = "{ha:sensor.teplota}"),
        )
        val failed = HaStateRead(HaProbe.Network("timeout"), atMillis = now)

        val diagnosis = diagnoseScenario(subject, onHotel, listOf(subject), emptyList(), now, failed)

        assertEquals(ContentHealth.HA_STATE_MISSING, diagnosis.content)
    }

    @Test
    fun `a TV remote without Google TV is a fault of the content`() {
        val remote = TemplateItem(actions = listOf(TapAction.SystemAction(SystemActionType.TV_REMOTE)))
        val content = TileContent.Template(layout = TemplateLayout.ICON_TEXT, items = listOf(remote))

        assertEquals(
            ContentHealth.GOOGLE_TV_MISSING,
            contentHealth(content, unavailable = setOf(SystemActionType.TV_REMOTE)),
        )
        assertEquals(ContentHealth.READY, contentHealth(content))
    }

    @Test
    fun `a read state from HA is fine`() {
        val content = TileContent.Template(layout = TemplateLayout.TEXT_ONLY, text = "{ha:sensor.teplota}")
        val read = HaStateRead(HaProbe.Ok(entities = 1, millis = 40), mapOf("sensor.teplota" to "21.5"), now)

        assertEquals(ContentHealth.READY, contentHealth(content, read))
    }

    @Test
    fun `a template without an HA value does not depend on reading`() {
        val content = TileContent.Template(layout = TemplateLayout.TEXT_ONLY, text = "Pevný text")

        assertEquals(ContentHealth.READY, contentHealth(content, HaStateRead(HaProbe.Network("timeout"))))
    }

    @Test
    fun `an outranked priority reports who occupies the slots`() {
        val first = scenario(id = "a", name = "A", priority = 90, conditions = listOf(Condition.Charging()))
        val second = scenario(id = "b", name = "B", priority = 80, conditions = listOf(Condition.Charging()))
        val third = scenario(id = "c", name = "C", priority = 10, conditions = listOf(Condition.Charging()))
        val all = listOf(first, second, third)
        val running = all.map { Activation(scenarioId = it.id, since = now) }

        val diagnosis = diagnoseScenario(third, SystemState(charging = true), all, running, now)

        assertEquals(listOf("a", "b"), diagnosis.outrankedBy.map { it.id })
    }

    @Test
    fun `a scenario without activation is not outranked`() {
        val first = scenario(id = "a", name = "A", priority = 90, conditions = listOf(Condition.Charging()))
        val second = scenario(id = "b", name = "B", priority = 80, conditions = listOf(Condition.Charging()))
        val subject = scenario(id = "c", name = "C", priority = 10, conditions = listOf(home))
        val all = listOf(first, second, subject)
        val running = listOf(first, second).map { Activation(scenarioId = it.id, since = now) }

        val diagnosis = diagnoseScenario(subject, onHotel, all, running, now)

        assertTrue(diagnosis.conditions.any { it.blocking })
        assertTrue(diagnosis.outrankedBy.isEmpty())
    }

    @Test
    fun `persistent content in a free slot is not outranked`() {
        val subject = scenario(id = "f", name = "F", size = TileSize.GRID_1X1)

        val diagnosis = diagnoseScenario(subject, SystemState(), listOf(subject), emptyList(), now)

        assertTrue(diagnosis.outrankedBy.isEmpty())
        assertEquals(ContentHealth.READY, diagnosis.content)
    }
}
