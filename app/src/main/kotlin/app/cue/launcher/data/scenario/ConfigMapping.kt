package app.cue.launcher.data.scenario

import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.layout.LauncherItem
import app.cue.launcher.data.layout.LayoutContainer
import app.cue.launcher.data.layout.StaticLayout
import app.cue.launcher.data.widgets.PanelItem

fun staticZoneConfigOf(
    layout: StaticLayout,
    panel: List<PanelItem>,
    providers: Map<Int, ComponentRef>,
): StaticZoneConfig = StaticZoneConfig(
    grid = entriesOf(layout, LayoutContainer.GRID, providers),
    dock = entriesOf(layout, LayoutContainer.DOCK, providers),
    widgetPanel = WidgetPanelConfig(
        widgets = panelEntriesOf(panel),
        dock = entriesOf(layout, LayoutContainer.PANEL_DOCK, providers),
    ),
)

fun panelEntriesOf(panel: List<PanelItem>): List<PanelEntry> = panel.mapIndexedNotNull { index, item ->
    when (item) {
        is PanelItem.AppWidget -> PanelEntry.AppWidget(
            position = index,
            packageName = item.widget.provider.packageName,
            provider = item.widget.provider.className,
            appWidgetId = placeholderFor(widgetPlaceholderKey(item.widget.appWidgetId)),
            rowSpan = item.widget.heightRows,
        )

        is PanelItem.LauncherWidget -> PanelEntry.LauncherWidget(position = index, widget = item.widget)
    }
}

private fun entriesOf(
    layout: StaticLayout,
    container: LayoutContainer,
    providers: Map<Int, ComponentRef>,
): List<StaticZoneEntry> = layout.slots(container).mapIndexedNotNull { position, item ->

    if (item == null || item.position != position) return@mapIndexedNotNull null
    when (item) {
        is LauncherItem.Shortcut -> StaticZoneEntry.Shortcut(
            position = position,
            packageName = item.component.packageName,
            component = item.component.className,
        )

        is LauncherItem.Folder -> StaticZoneEntry.Folder(
            position = position,
            name = item.name,
            items = item.items.map(::componentEntryOf),
        )

        is LauncherItem.LauncherWidget -> StaticZoneEntry.LauncherWidget(
            position = position,
            widget = item.widget,
        )

        is LauncherItem.AppWidget -> providers[item.appWidgetId]?.let { provider ->
            StaticZoneEntry.AppWidget(
                position = position,
                packageName = provider.packageName,
                provider = provider.className,
                appWidgetId = placeholderFor(widgetPlaceholderKey(item.appWidgetId)),
                columnSpan = item.columnSpan,
                rowSpan = item.rowSpan,
            )
        }
    }
}

fun componentEntryOf(component: ComponentRef): ComponentEntry =
    ComponentEntry(packageName = component.packageName, component = component.className)

fun componentRefOf(entry: ComponentEntry, userSerial: Long): ComponentRef =
    ComponentRef(packageName = entry.packageName, className = entry.component, userSerial = userSerial)

fun appWidgetsIn(config: StaticZoneConfig): List<ConfigAppWidget> {
    val entries = config.grid + config.dock + config.widgetPanel.dock
    val fromZone = entries.filterIsInstance<StaticZoneEntry.AppWidget>()
        .map { ConfigAppWidget(widgetKeyOf(it.appWidgetId), it.packageName, it.provider) }
    val fromPanel = config.widgetPanel.widgets.filterIsInstance<PanelEntry.AppWidget>()
        .map { ConfigAppWidget(widgetKeyOf(it.appWidgetId), it.packageName, it.provider) }
    return (fromZone + fromPanel).distinctBy(ConfigAppWidget::key)
}

fun launcherItemsOf(
    config: StaticZoneConfig,
    userSerial: Long,
    appWidgetIds: Map<String, Int> = emptyMap(),
): List<LauncherItem> =
    itemsOf(config.grid, LayoutContainer.GRID, userSerial, appWidgetIds) +
        itemsOf(config.dock, LayoutContainer.DOCK, userSerial, appWidgetIds) +
        itemsOf(config.widgetPanel.dock, LayoutContainer.PANEL_DOCK, userSerial, appWidgetIds)

private fun itemsOf(
    entries: List<StaticZoneEntry>,
    container: LayoutContainer,
    userSerial: Long,
    appWidgetIds: Map<String, Int>,
): List<LauncherItem> = entries.mapNotNull { entry ->
    val item = itemOf(entry, container, userSerial, appWidgetIds) ?: return@mapNotNull null

    item.takeIf { container.fitsArea(it.position, it.columnSpan, it.rowSpan) }
}

private fun itemOf(
    entry: StaticZoneEntry,
    container: LayoutContainer,
    userSerial: Long,
    appWidgetIds: Map<String, Int>,
): LauncherItem? = when (entry) {
    is StaticZoneEntry.Shortcut -> LauncherItem.Shortcut(
        id = 0,
        container = container,
        position = entry.position,
        component = ComponentRef(
            packageName = entry.packageName,
            className = entry.component,
            userSerial = userSerial,
        ),
    )

    is StaticZoneEntry.Folder -> LauncherItem.Folder(
        id = 0,
        container = container,
        position = entry.position,
        name = entry.name,
        items = entry.items.map { item -> componentRefOf(item, userSerial) },
    )

    is StaticZoneEntry.LauncherWidget -> LauncherItem.LauncherWidget(
        id = 0,
        container = container,
        position = entry.position,
        widget = entry.widget,
    )

    is StaticZoneEntry.AppWidget -> appWidgetIds[widgetKeyOf(entry.appWidgetId)]?.let { appWidgetId ->
        LauncherItem.AppWidget(
            id = 0,
            container = container,
            position = entry.position,
            appWidgetId = appWidgetId,
            columnSpan = entry.columnSpan,
            rowSpan = entry.rowSpan,
        )
    }
}
