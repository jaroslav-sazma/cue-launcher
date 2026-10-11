package app.cue.launcher.feature.widgets

import android.content.Context
import androidx.compose.runtime.Stable
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.widgets.HostedWidget
import app.cue.launcher.data.widgets.PanelColumnRepository
import app.cue.launcher.data.widgets.PanelItem
import app.cue.launcher.data.widgets.PanelItemKey
import app.cue.launcher.data.widgets.PanelItemKind
import app.cue.launcher.data.widgets.WidgetPlacement
import app.cue.launcher.system.widget.AvailableWidget
import app.cue.launcher.system.widget.HostedWidgetView
import app.cue.launcher.system.widget.WidgetBindResult
import app.cue.launcher.system.widget.WidgetBinder
import app.cue.launcher.system.widget.WidgetCatalog
import app.cue.launcher.system.widget.WidgetHost
import app.cue.launcher.system.widget.WidgetPreview
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
@Stable
class WidgetHosting(
    private val catalog: WidgetCatalog,
    private val binder: WidgetBinder,
    private val host: WidgetHost,
    private val panel: PanelColumnRepository,
) {

    fun observeColumn(): Flow<List<PanelItem>> = panel.observeColumn()

    val outcomes: Flow<WidgetBindResult> get() = binder.outcomes

    suspend fun available(): List<AvailableWidget> = catalog.availableWidgets()

    suspend fun preview(widget: AvailableWidget): WidgetPreview? = catalog.preview(widget)

    suspend fun labelOf(hosted: HostedWidget): String? = catalog.labelOf(hosted.appWidgetId)

    fun hostedView(context: Context, appWidgetId: Int): HostedWidgetView? =
        host.hostedView(context, appWidgetId)

    fun refresh(appWidgetId: Int) = host.refresh(appWidgetId)

    fun add(widget: AvailableWidget, placement: WidgetPlacement = WidgetPlacement.PANEL) =
        binder.bind(widget, placement)

    suspend fun addLauncherWidget(widget: LauncherWidgetType) {
        panel.addLauncherWidget(widget)
    }

    fun configure(appWidgetId: Int): Boolean = binder.reconfigure(appWidgetId)

    fun canConfigure(appWidgetId: Int): Boolean = host.isReconfigurable(appWidgetId)

    fun unbind(appWidgetId: Int) = binder.unbind(appWidgetId)

    suspend fun remove(key: PanelItemKey) {
        when (key.kind) {
            PanelItemKind.APP_WIDGET -> unbind(key.id.toInt())
            PanelItemKind.LAUNCHER_WIDGET -> panel.removeLauncherWidget(key.id)
        }
    }

    suspend fun move(key: PanelItemKey, delta: Int) {
        panel.move(key, delta)
    }

    suspend fun resize(appWidgetId: Int, heightRows: Int) {
        panel.setHeightRows(appWidgetId, heightRows)
    }
}
