package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionType
import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.EndEventType
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.ScenarioDefect
import app.cue.launcher.data.scenario.ScenarioFormat
import app.cue.launcher.data.scenario.ScenarioRead
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.data.scenario.validateScenario
import app.cue.launcher.feature.scenarios.EndPhrase
import app.cue.launcher.feature.scenarios.EndPhraseKey
import app.cue.launcher.feature.scenarios.conditionPhrase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EndEventFormTest {

    @Test
    fun `an imported cap off the presets stays on offer and comes back unchanged`() {
        val end = EndEvent.Notification(packageName = "com.watch", contains = "offline")
        val condition = Condition.NotificationFrom(packageName = "com.watch", durationSec = 5_400, until = end)

        val draft = endingDraftOf(condition, '.')

        assertEquals(5_400, draft.capSec)
        assertEquals(listOf(null, 3_600, 5_400, 14_400, 86_400), endCapChoices(draft.capSec))
        assertNull(endingError(draft))
        assertEquals(condition, canonicalCondition(withEnding(condition, draft)))
        assertEquals(listOf(null, 3_600, 14_400, 86_400), endCapChoices(3_600))
    }

    @Test
    fun `whatever end the form accepts, the import accepts too`() {
        val triggers = INSTANT_TYPES.map(::newCondition).map { trigger ->
            when (trigger) {
                is Condition.NotificationFrom -> trigger.copy(packageName = "com.watch")
                is Condition.NotificationEnded -> trigger.copy(packageName = "com.google.android.apps.maps")
                is Condition.HaEvent -> trigger.copy(code = "door")
                else -> trigger
            }
        }
        val ends = END_SHAPES + EndEventType.entries.map(::newEndEvent)
        val drafts = triggers.flatMap { trigger ->
            ends.flatMap { end ->
                listOf(null, 3_600, 5_400).map { cap ->
                    trigger to endingDraftOf(trigger as Condition.InstantCondition, '.')
                        .copy(choice = DurationChoice.EVENT, end = end, capSec = cap)
                }
            }
        }

        val accepted = drafts.filter { (trigger, draft) ->
            conditionFormError(trigger) == null && endingError(draft) == null
        }
        accepted.forEach { (trigger, draft) -> assertImportAccepts(canonicalCondition(withEnding(trigger, draft))) }
        assertTrue(accepted.size >= INSTANT_TYPES.size * END_SHAPES.size * 3) { "only ${accepted.size} were accepted" }
    }

    @Test
    fun `the suggested name leaves the end to the summary`() {
        val watch = Condition.NotificationFrom(
            packageName = "com.watch",
            until = EndEvent.Notification(packageName = "com.watch", contains = "offline"),
        )

        assertEquals(EndPhrase(EndPhraseKey.NOTIFICATION, "com.watch"), conditionPhrase(watch).until)
        assertEquals(conditionPhrase(watch).copy(until = null), namePhrase(watch))
        assertEquals(conditionPhrase(Condition.Charging()), namePhrase(Condition.Charging()))
    }

    @Test
    fun `a set time ending a set time is summed up with its own time and days`() {
        val morning = Condition.TimeInstant(
            at = "07:00",
            days = listOf(1, 2, 3, 4, 5),
            until = EndEvent.AtTime(at = "09:00", days = listOf(1, 2, 3, 4, 5)),
        )

        val until = conditionPhrase(morning).until

        assertEquals(EndPhraseKey.TIME, until?.key)
        assertEquals("9", until?.argument)
        assertEquals(conditionPhrase(morning.copy(until = null)).days, until?.days)
        assertFalse(endRepeatsTrigger(morning, morning.until!!))
    }

    private fun assertImportAccepts(condition: Condition) {
        val scenario = SCENARIO.copy(conditions = listOf(condition))
        assertEquals(emptyList<ScenarioDefect>(), validateScenario(scenario)) { "$condition" }

        val read = ScenarioFormat.read(ScenarioFormat.encodeScenario(scenario))
        assertTrue(read is ScenarioRead.Ok) { "$condition: $read" }
        val ok = read as ScenarioRead.Ok
        assertTrue(ok.rejected.isEmpty()) { "$condition: ${ok.rejected}" }
        assertEquals(listOf(condition), ok.accepted.single().scenario.conditions)
    }

    private companion object {

        val INSTANT_TYPES = ConditionType.entries.filter { it.isInstant }

        val END_SHAPES: List<EndEvent> = listOf(
            EndEvent.AtTime(at = "9:5"),
            EndEvent.AtTime(at = "17:00", days = listOf(7, 6, 6)),
            EndEvent.AtTime(at = "23:59", days = listOf(1)),
            EndEvent.Alarm,
            EndEvent.BtDisconnected(),
            EndEvent.BtDisconnected(mac = "AA:BB:CC:DD:EE:FF"),
            EndEvent.Notification(packageName = " com.watch "),
            EndEvent.Notification(packageName = "com.watch", contains = "  offline "),
            EndEvent.Notification(packageName = "com.watch", contains = "   "),
            EndEvent.HaEvent(code = " door_closed "),
        )

        val SCENARIO = Scenario(
            id = "until-shapes",
            name = "Until",
            size = TileSize.GRID_1X1,
            content = TileContent.Template(
                TemplateLayout.ICON_TEXT,
                items = listOf(TemplateItem(label = "Open", actions = listOf(TapAction.App("com.watch")))),
            ),
        )
    }
}
