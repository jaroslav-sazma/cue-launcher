package app.cue.launcher.system.widget

import android.util.Log
import app.cue.launcher.data.layout.LauncherLayoutRepository
import app.cue.launcher.data.scenario.ScenarioRepository
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.widgets.HostedWidgetRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@SingleIn(AppScope::class)
@Inject
class WidgetHostMaintenance(
    private val host: WidgetHost,
    private val widgets: HostedWidgetRepository,
    private val scenarios: ScenarioRepository,
    private val layout: LauncherLayoutRepository,
) {

    fun start(scope: CoroutineScope) {
        scope.launch {
            reconcile()
            releaseOrphanScenarioWidgets()
            host.removedWidgets.collect(::forget)
        }
    }

    suspend fun reconcile() {
        val hostIds = host.allocatedIds()
        val plan = reconcileWidgets(
            hostIds = hostIds,
            knownIds = widgets.knownIds(),
            isBound = { appWidgetId -> host.providerInfo(appWidgetId) != null },
        )

        val live = hostIds - plan.releaseFromHost.toSet()
        val deadInGrid = layout.removeDeadAppWidgets(live.toSet())
        if (plan.isEmpty && deadInGrid == 0) return
        plan.releaseFromHost.forEach(host::deleteAppWidgetId)
        widgets.forget(plan.forgetRows)
        Log.i(
            TAG,
            "Widget cleanup: ${plan.releaseFromHost.size} ids released, " +
                "${plan.forgetRows.size} rows dropped, " +
                "$deadInGrid from the home grid",
        )
    }

    private suspend fun releaseOrphanScenarioWidgets() {
        val referenced = scenarios.scenarios()
            .mapNotNull { (it.content as? TileContent.AppWidget)?.boundAppWidgetId }
            .toSet()
        val orphans = orphanScenarioWidgets(widgets.scenarioIds(), referenced)
        if (orphans.isEmpty()) return
        orphans.forEach(host::deleteAppWidgetId)
        widgets.forget(orphans)
        Log.i(TAG, "Widget cleanup: ${orphans.size} bound to an unsaved scenario")
    }

    private suspend fun forget(appWidgetId: Int) {
        if (widgets.forget(listOf(appWidgetId)) > 0) {
            Log.i(TAG, "Widget $appWidgetId vanished from the system, row dropped")
        }
    }

    private companion object {
        const val TAG = "WidgetHost"
    }
}
