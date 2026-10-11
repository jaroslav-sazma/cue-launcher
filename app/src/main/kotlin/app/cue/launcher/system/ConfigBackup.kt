package app.cue.launcher.system

import android.util.Log
import app.cue.launcher.data.drawer.HiddenAppRepository
import app.cue.launcher.data.icons.IconOverrideRepository
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.layout.LauncherLayoutRepository
import app.cue.launcher.data.scenario.AppearanceConfig
import app.cue.launcher.data.scenario.ComponentEntry
import app.cue.launcher.data.scenario.ConfigAppWidget
import app.cue.launcher.data.scenario.DrawerConfig
import app.cue.launcher.data.scenario.LauncherConfig
import app.cue.launcher.data.scenario.PanelEntry
import app.cue.launcher.data.scenario.StaticZoneConfig
import app.cue.launcher.data.scenario.WidgetsConfig
import app.cue.launcher.data.scenario.accentHexOf
import app.cue.launcher.data.scenario.appWidgetsIn
import app.cue.launcher.data.scenario.componentEntryOf
import app.cue.launcher.data.scenario.componentRefOf
import app.cue.launcher.data.scenario.launcherItemsOf
import app.cue.launcher.data.scenario.staticZoneConfigOf
import app.cue.launcher.data.scenario.widgetKeyOf
import app.cue.launcher.data.settings.LauncherSettings
import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.data.widgets.HostedWidget
import app.cue.launcher.data.widgets.HostedWidgetRepository
import app.cue.launcher.data.widgets.PANEL_WIDGET_ROWS_DEFAULT
import app.cue.launcher.data.widgets.PanelColumnRepository
import app.cue.launcher.data.widgets.PanelLauncherWidget
import app.cue.launcher.data.widgets.WidgetPlacement
import app.cue.launcher.system.widget.WidgetHost
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Suppress("LongParameterList")
@SingleIn(AppScope::class)
@Inject
class ConfigBackup(
    private val layout: LauncherLayoutRepository,
    private val panel: PanelColumnRepository,
    private val hostedWidgets: HostedWidgetRepository,
    private val settings: SettingsRepository,
    private val hiddenApps: HiddenAppRepository,
    private val iconOverrides: IconOverrideRepository,
    private val profiles: UserProfiles,
    private val host: WidgetHost,
) {

    suspend fun read(): LauncherConfig {
        val stored = settings.settings.first()
        val providers = hostedWidgets.widgets().associate { it.appWidgetId to it.provider }
        return launcherConfigOf(
            stored = stored,
            staticZone = staticZoneConfigOf(layout.layout(), panel.column(), providers),
            iconOverrides = sortedEntries(iconOverrides.overrides()),
            hiddenApps = sortedEntries(hiddenApps.hidden()),
        )
    }

    suspend fun apply(config: LauncherConfig): ConfigRestore = withContext(NonCancellable) {
        val zone = config.staticZone
        val userSerial = profiles.serialOf(profiles.own)
        val wanted = appWidgetsIn(zone)
        val panelEntries = zone.widgetPanel.widgets.filterIsInstance<PanelEntry.AppWidget>()
            .associateBy { widgetKeyOf(it.appWidgetId) }

        val bound = wanted.mapNotNull { widget -> rebind(widget, userSerial)?.let { widget.key to it } }.toMap()
        val rows = wanted.filter { it.key in bound }.map { widget ->
            val panelEntry = panelEntries[widget.key]
            HostedWidget(
                appWidgetId = bound.getValue(widget.key),
                provider = ComponentRef(widget.packageName, widget.provider, userSerial),
                boundAt = System.currentTimeMillis(),
                position = panelEntry?.position ?: 0,
                placement = if (panelEntry != null) WidgetPlacement.PANEL else WidgetPlacement.HOME,
                heightRows = panelEntry?.rowSpan ?: PANEL_WIDGET_ROWS_DEFAULT,
            )
        }

        val released = hostedWidgets.replacePlaced(rows)
        layout.replaceAll(launcherItemsOf(zone, userSerial, bound))
        panel.replaceLauncherWidgets(
            zone.widgetPanel.widgets.filterIsInstance<PanelEntry.LauncherWidget>()
                .map { entry -> PanelLauncherWidget(widget = entry.widget, position = entry.position) },
        )

        released.forEach(host::deleteAppWidgetId)

        settings.applyConfig(config)
        hiddenApps.replaceAll(config.drawer.hiddenApps.map { componentRefOf(it, userSerial) })
        iconOverrides.replaceAll(config.appearance.iconOverrides.map { componentRefOf(it, userSerial) })

        Log.i(TAG, "Configuration restored: ${rows.size}/${wanted.size} widgets, ${released.size} released")
        ConfigRestore(widgets = rows.size, skippedWidgets = wanted.size - rows.size)
    }

    private fun rebind(widget: ConfigAppWidget, userSerial: Long): Int? {
        val provider = ComponentRef(widget.packageName, widget.provider, userSerial)
        val appWidgetId = host.allocateAppWidgetId()
        if (host.bindIfAllowed(appWidgetId, provider)) return appWidgetId
        host.deleteAppWidgetId(appWidgetId)
        Log.i(TAG, "Widget $provider from the backup could not be bound")
        return null
    }

    private fun sortedEntries(components: Collection<ComponentRef>): List<ComponentEntry> = components
        .map(::componentEntryOf)
        .sortedWith(compareBy(ComponentEntry::packageName, ComponentEntry::component))

    private companion object {
        const val TAG = "ConfigBackup"
    }
}

data class ConfigRestore(val widgets: Int, val skippedWidgets: Int)

internal fun launcherConfigOf(
    stored: LauncherSettings,
    staticZone: StaticZoneConfig,
    iconOverrides: List<ComponentEntry>,
    hiddenApps: List<ComponentEntry>,
): LauncherConfig = LauncherConfig(
    staticZone = staticZone,
    appearance = AppearanceConfig(
        iconMode = stored.appearance.iconMode,
        iconOverrides = iconOverrides,
        accent = stored.appearance.accent,
        customAccent = stored.appearance.customAccent?.let(::accentHexOf),
    ),
    drawer = DrawerConfig(
        contacts = stored.drawer.contacts,
        aiRow = stored.drawer.aiRow,
        webRow = stored.drawer.webRow,
        hiddenApps = hiddenApps,
    ),
    gestures = stored.gestures,
    widgets = WidgetsConfig(mediaApp = stored.mediaWidget.app),
)
