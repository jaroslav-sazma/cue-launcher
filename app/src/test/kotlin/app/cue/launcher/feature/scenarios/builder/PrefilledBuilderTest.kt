package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.aichat.CandidateCheck
import app.cue.launcher.data.aichat.checkCandidate
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.ScenarioFormat
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.data.scenario.placeholderFor
import app.cue.launcher.feature.settings.ScenarioBuilder
import app.cue.launcher.system.widget.AvailableWidget
import app.cue.launcher.system.widget.WidgetSizeSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PrefilledBuilderTest {

    private val weather = AvailableWidget(
        provider = ComponentRef("com.forecast", "com.forecast.WeatherWidget", 0L),
        label = "Weather",
        appLabel = "Forecast",
        needsConfiguration = false,
        size = WidgetSizeSpec(minWidthPx = 100, minHeightPx = 100, horizontallyResizable = false),
    )

    private val widgetScenario = Scenario(
        id = "pasted-draft",
        name = "Alarm → Weather",
        size = TileSize.HERO_4X2,
        conditions = listOf(Condition.Alarm(durationSec = 1800)),
        content = TileContent.AppWidget("com.forecast", "com.forecast.WeatherWidget", placeholderFor("widget")),
    )

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

    @Test
    fun `without a scenario the builder starts empty on the first step`() {
        val state = prefilledBuilder(initial = null, initialStep = SUMMARY_STEP)

        assertEquals(ScenarioDraft(), state.draft)
        assertEquals(FIRST_STEP, state.step)
    }

    @Test
    fun `a scenario fills the draft and opens on the requested step`() {
        val state = prefilledBuilder(initial = scenario, initialStep = SUMMARY_STEP)

        assertEquals(scenarioDraftOf(scenario), state.draft)
        assertEquals(SUMMARY_STEP, state.step)
    }

    @Test
    fun `a prefilled builder saves under a fresh id`() {
        val state = prefilledBuilder(initial = scenario, initialStep = FIRST_STEP)

        assertNotEquals(scenario.id, state.id)
        assertEquals(scenario.copy(id = state.id), scenarioOf(state.draft, state.id, scenario.name))
    }

    @Test
    fun `a drafted widget opens unbound on its provider and saves switched off once bound`() {
        val drafted = (checkCandidate(ScenarioFormat.encodeScenario(widgetScenario)) as CandidateCheck.Valid).scenario
        val state = prefilledBuilder(initial = drafted, initialStep = SUMMARY_STEP)

        val content = state.draft.content
        assertEquals(ContentKind.APP_WIDGET, content.kind)
        assertNull(content.widget)
        assertEquals(ComponentRef("com.forecast", "com.forecast.WeatherWidget", 0L), content.suggestedWidget)
        assertEquals(ContentFormError.NO_WIDGET, contentFormError(content))
        assertNull(scenarioOf(state.draft, state.id, drafted.name))

        val bound = state.draft.copy(content = content.copy(widget = weather.bound(appWidgetId = 42)))
        val saved = scenarioOf(bound, state.id, drafted.name)

        assertEquals(TileContent.AppWidget("com.forecast", "com.forecast.WeatherWidget", "42"), saved?.content)
        assertFalse(saved!!.enabled)
    }

    @Test
    fun `the suggested provider is found in any profile, its own first, and placeholders suggest none`() {
        val work = weather.copy(provider = weather.provider.copy(userSerial = 10L))
        val other = weather.copy(provider = weather.provider.copy(className = "com.forecast.Radar"))

        assertEquals(work, suggestedWidgetIn(listOf(other, work), weather.provider))
        assertEquals(weather, suggestedWidgetIn(listOf(work, weather), weather.provider))
        assertNull(suggestedWidgetIn(listOf(other), weather.provider))
        val placeholder = TileContent.AppWidget(placeholderFor("pkg"), placeholderFor("provider"), placeholderFor("id"))
        assertNull(contentDraftOf(placeholder).suggestedWidget)
    }

    @Test
    fun `a step outside the wizard is clamped`() {
        assertEquals(SUMMARY_STEP, prefilledBuilder(scenario, initialStep = 99).step)
        assertEquals(FIRST_STEP, prefilledBuilder(scenario, initialStep = 0).step)
    }

    @Test
    fun `the default key opens an empty builder`() {
        assertEquals(ScenarioBuilder(scenarioJson = null, step = FIRST_STEP), ScenarioBuilder())
    }

    @Test
    fun `the key carries the scenario as canonical json`() {
        val key = ScenarioBuilder(scenarioJson = ScenarioFormat.canonicalJson(scenario), step = SUMMARY_STEP)

        assertEquals(scenario, key.scenarioJson?.let(ScenarioFormat::decodeCanonical))
    }
}
