package app.cue.launcher.data.widgets

import app.cue.launcher.data.scenario.LauncherWidgetType

sealed interface PanelItem {

    val key: PanelItemKey

    val position: Int

    data class AppWidget(val widget: HostedWidget) : PanelItem {
        override val key: PanelItemKey =
            PanelItemKey(PanelItemKind.APP_WIDGET, widget.appWidgetId.toLong())
        override val position: Int get() = widget.position
    }

    data class LauncherWidget(
        val id: Long,
        val widget: LauncherWidgetType,
        override val position: Int,
    ) : PanelItem {
        override val key: PanelItemKey = PanelItemKey(PanelItemKind.LAUNCHER_WIDGET, id)
    }
}

enum class PanelItemKind { APP_WIDGET, LAUNCHER_WIDGET }

data class PanelLauncherWidget(val widget: LauncherWidgetType, val position: Int)

data class PanelItemKey(val kind: PanelItemKind, val id: Long) {

    fun regionKey(): String = "$kind.$id"
}

fun panelColumnOf(
    hosted: List<HostedWidget>,
    launcherWidgets: List<PanelItem.LauncherWidget>,
): List<PanelItem> = (hosted.map(PanelItem::AppWidget) + launcherWidgets)
    .sortedWith(compareBy({ it.position }, { it.key.kind.ordinal }, { it.key.id }))

fun PanelWidgetEntity.toPanelItem(): PanelItem.LauncherWidget? =
    LauncherWidgetType.fromJsonName(widget)?.let { type ->
        PanelItem.LauncherWidget(id = id, widget = type, position = position)
    }
