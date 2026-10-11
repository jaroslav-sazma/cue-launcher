package app.cue.launcher.feature.scenarios.builder

import androidx.compose.runtime.saveable.SaverScope
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.system.InstalledApp
import app.cue.launcher.system.widget.AvailableWidget
import app.cue.launcher.system.widget.WidgetSizeSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BuilderSheetSavingTest {

    private fun roundTrip(state: BuilderState): BuilderState {
        val saved = with(BuilderState.Saver) { SaverScope { true }.save(state) }
        return BuilderState.Saver.restore(saved!!)!!
    }

    private fun reopened(sheet: BuilderSheet): BuilderSheet? {
        val state = BuilderState()
        state.show(sheet)
        return roundTrip(state).sheet
    }

    @Test
    fun `every open sheet comes back after being saved`() {
        val slot = ActionSlot(item = 1, action = 2)
        val notification = Condition.NotificationFrom(packageName = "com.chat", contains = "home")
        val app = InstalledApp(ComponentRef("com.music", "com.music.Main", userSerial = 10L), "Music")
        val sheets = listOf(
            BuilderSheet.Catalog(replacing = true),
            BuilderSheet.Settings(Condition.Charging(), index = null),
            BuilderSheet.Settings(notification, index = 0, diagnosis = true),
            BuilderSheet.WidgetPicker,
            BuilderSheet.ActionPicker(slot),
            BuilderSheet.AppPicker(AppPickRequest.OpenApp(slot)),
            BuilderSheet.AppPicker(AppPickRequest.InApp(slot)),
            BuilderSheet.AppPicker(AppPickRequest.ForCondition(notification, index = null)),
            BuilderSheet.AppPicker(AppPickRequest.ForCondition(notification, index = 3)),
            BuilderSheet.AppActions(slot, app),
            BuilderSheet.SystemAction(slot),
            BuilderSheet.HaAction(slot),
            BuilderSheet.UriAction(slot),
            BuilderSheet.IconPicker(item = 2),
        )

        sheets.forEach { sheet -> assertEquals(sheet, reopened(sheet), "$sheet") }
    }

    @Test
    fun `a widget about to be bound comes back as the picker it was chosen from`() {
        val widget = AvailableWidget(
            provider = ComponentRef("com.weather", "com.weather.Forecast", userSerial = 0L),
            label = "Forecast",
            appLabel = "Weather",
            needsConfiguration = false,
            size = WidgetSizeSpec(minWidthPx = 100, minHeightPx = 100, horizontallyResizable = false),
        )

        assertEquals(BuilderSheet.WidgetPicker, reopened(BuilderSheet.WidgetBind(widget)))

        val binding = BuilderState()
        binding.show(BuilderSheet.WidgetPicker)
        binding.show(BuilderSheet.WidgetBind(widget))
        assertEquals(binding.opening + 1, roundTrip(binding).opening, "the picker is a new opening")
    }

    @Test
    fun `no sheet stays no sheet`() {
        assertNull(roundTrip(BuilderState()).sheet)
    }

    @Test
    fun `a sheet opened over none is a new opening and moving between sheets is not`() {
        val state = BuilderState()

        state.show(BuilderSheet.Catalog())
        val first = state.opening
        state.show(BuilderSheet.Settings(Condition.Charging(), index = null))
        assertEquals(first, state.opening)

        state.show(null)
        state.show(BuilderSheet.Catalog())
        assertEquals(first + 1, state.opening)
    }

    @Test
    fun `the opening survives being saved, so the next one is still new`() {
        val state = BuilderState()
        state.show(BuilderSheet.IconPicker(item = 0))
        val restored = roundTrip(state)

        assertEquals(state.opening, restored.opening)

        restored.show(null)
        restored.show(BuilderSheet.IconPicker(item = 0))
        assertEquals(state.opening + 1, restored.opening)
    }
}
