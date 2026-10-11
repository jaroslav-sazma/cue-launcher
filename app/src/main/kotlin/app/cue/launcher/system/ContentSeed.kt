package app.cue.launcher.system

import android.app.Application
import android.app.LocaleConfig
import android.content.res.Configuration
import android.util.Log
import app.cue.launcher.R
import app.cue.launcher.data.scenario.CLOCK_SEED_SCENARIO_ID
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.ScenarioRepository
import app.cue.launcher.data.scenario.clockSeedScenario
import app.cue.launcher.data.scenario.relabeledSeedScenario
import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.data.widgets.PanelColumnRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

@SingleIn(AppScope::class)
@Inject
class ContentSeed(
    private val application: Application,
    private val scenarios: ScenarioRepository,
    private val settings: SettingsRepository,
    private val panel: PanelColumnRepository,
) {

    fun start(scope: CoroutineScope) {
        scope.launch { seedClock() }
        scope.launch { seedPanel() }
    }

    // The widget page opens empty on a fresh install, which reads as a dead end rather than as room
    // to fill. One calendar makes the point, and it stays useful even before access is granted: its
    // notice doubles as the button that asks for the permission.
    private suspend fun seedPanel() {
        if (settings.settings.first().panelSeeded) return
        if (panel.observeColumn().first().isEmpty()) {
            panel.addLauncherWidget(LauncherWidgetType.CALENDAR)
            Log.i(TAG, "Panel content seed: calendar")
        }
        settings.setPanelSeeded(true)
    }

    private suspend fun seedClock() {
        val name = application.getString(R.string.seed_clock_name)
        if (settings.settings.first().clockSeeded) {
            relabelClockSeed(name)
            return
        }
        if (scenarios.isEmpty()) {
            scenarios.save(clockSeedScenario(name))
            Log.i(TAG, "Static content seed: 4×2 clock")
        }
        settings.setClockSeeded(true)
    }

    private suspend fun relabelClockSeed(name: String) {
        val seed = scenarios.scenario(CLOCK_SEED_SCENARIO_ID)?.takeIf { it.name != name } ?: return
        val relabeled = relabeledSeedScenario(seed, name, seedClockNames()) ?: return
        scenarios.save(relabeled)
        Log.i(TAG, "Clock seed relabelled for the current language")
    }

    private fun seedClockNames(): Set<String> {
        val supported = LocaleConfig(application).supportedLocales ?: return emptySet()
        return (0 until supported.size())
            .map { index -> localizedSeedClockName(supported[index]) }
            .toSet()
    }

    private fun localizedSeedClockName(locale: Locale): String {
        val configuration = Configuration(application.resources.configuration)
        configuration.setLocale(locale)
        return application.createConfigurationContext(configuration)
            .getString(R.string.seed_clock_name)
    }

    private companion object {
        const val TAG = "ContentSeed"
    }
}
