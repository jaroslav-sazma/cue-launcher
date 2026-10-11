package app.cue.launcher.feature.scenarios.builder

import androidx.compose.runtime.saveable.SaverScope
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuilderNavigationTest {

    private val scenario = Scenario(
        id = "evening",
        name = "Evening",
        size = TileSize.GRID_1X1,
        conditions = listOf(Condition.Charging()),
        content = TileContent.Template(
            TemplateLayout.ICON_TEXT,
            items = listOf(TemplateItem(label = "Music", actions = listOf(TapAction.App("com.music")))),
        ),
    )

    private fun summary() = prefilledBuilder(scenario, initialStep = SUMMARY_STEP)

    private fun roundTrip(state: BuilderState): BuilderState {
        val saved = with(BuilderState.Saver) { SaverScope { true }.save(state) }
        return BuilderState.Saver.restore(saved!!)!!
    }

    @Test
    fun `a step opened from the summary goes back to the summary`() {
        val state = summary()

        state.editFromSummary(FIRST_STEP)
        assertEquals(FIRST_STEP, state.step)
        assertTrue(state.canGoBack)

        state.back()
        assertEquals(SUMMARY_STEP, state.step)
        assertFalse(state.returnsToSummary)
    }

    @Test
    fun `next from a step opened from the summary skips the complete steps`() {
        val state = summary()

        state.editFromSummary(TYPE_STEP)
        state.advance(save = { error("not on the summary") })

        assertEquals(SUMMARY_STEP, state.step)
        assertFalse(state.returnsToSummary)
    }

    @Test
    fun `a change that undoes a later step stops there and keeps heading for the summary`() {
        val state = summary()

        state.editFromSummary(TYPE_STEP)
        state.draft = state.draft.withSize(TileSize.GRID_2X2)
        state.back()

        assertEquals(CONTENT_STEP, state.step)
        assertTrue(state.returnsToSummary)
    }

    @Test
    fun `the plain wizard steps back one at a time and leaves from the first step`() {
        val state = BuilderState(draft = scenarioDraftOf(scenario), step = TYPE_STEP)

        state.back()
        assertEquals(FIRST_STEP, state.step)
        assertFalse(state.canGoBack)

        state.back()
        assertEquals(FIRST_STEP, state.step)
    }

    @Test
    fun `back from a later step opened from the summary skips the steps before it`() {
        val state = summary()

        state.editFromSummary(CONTENT_STEP)
        state.back()

        assertEquals(SUMMARY_STEP, state.step)
    }

    @Test
    fun `the summary reached past an undone step knows the step to reopen`() {
        val state = summary()
        state.editFromSummary(TYPE_STEP)
        state.draft = state.draft.withSize(TileSize.GRID_2X2)
        state.back()
        state.back()

        assertEquals(SUMMARY_STEP, state.step)
        assertEquals(CONTENT_STEP, state.incompleteStep)
    }

    @Test
    fun `a complete draft has no step to reopen`() {
        assertNull(summary().incompleteStep)
    }

    @Test
    fun `a pasted scenario without its widget reopens the content step and returns to the summary`() {
        val pasted = scenario.copy(
            size = TileSize.GRID_2X2,
            content = TileContent.AppWidget(
                packageName = "com.weather",
                provider = "com.weather.Forecast",
                appWidgetId = "{{PLACEHOLDER:widget}}",
            ),
        )
        val state = prefilledBuilder(pasted, initialStep = SUMMARY_STEP)

        val incomplete = requireNotNull(state.incompleteStep)
        state.editFromSummary(incomplete)
        assertEquals(CONTENT_STEP, state.step)
        assertTrue(state.returnsToSummary)

        state.back()
        assertEquals(SUMMARY_STEP, state.step)
    }

    @Test
    fun `only an untouched new builder is pristine`() {
        val fresh = prefilledBuilder(initial = null, initialStep = FIRST_STEP)
        assertTrue(fresh.pristine)
        assertTrue(roundTrip(fresh).pristine)

        fresh.draft = fresh.draft.withCondition(Condition.Charging())
        assertFalse(fresh.pristine)

        assertFalse(summary().pristine)
    }

    @Test
    fun `the way back to the summary survives being saved`() {
        val state = summary()
        state.editFromSummary(PRIORITY_STEP)

        val restored = roundTrip(state)

        assertEquals(PRIORITY_STEP, restored.step)
        assertTrue(restored.returnsToSummary)
        assertEquals(state.draft, restored.draft)
        assertEquals(state.id, restored.id)
    }
}
