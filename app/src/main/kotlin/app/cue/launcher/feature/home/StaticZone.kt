package app.cue.launcher.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cue.launcher.data.layout.LayoutContainer
import app.cue.launcher.data.layout.StaticLayout
import app.cue.launcher.feature.widgets.WidgetHosting
import app.cue.launcher.designsystem.GlassLevel
import app.cue.launcher.designsystem.TileSurface
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.wallpaperBlurRegion
import app.cue.launcher.feature.dragdrop.ShortcutDragState
import app.cue.launcher.system.LauncherAppsRepository

@Composable
fun StaticZone(
    layout: StaticLayout,
    launcherApps: LauncherAppsRepository,
    dragState: ShortcutDragState,
    widgets: WidgetHosting,
    actions: StaticSlotActions,
    modifier: Modifier = Modifier,
    rowGap: Dp = LocalHomeBands.current.rowGap,
    pageIndicator: @Composable () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        StaticGrid(
            layout = layout,
            launcherApps = launcherApps,
            dragState = dragState,
            widgets = widgets,
            actions = actions,
            rowGap = rowGap,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Tokens.Grid.contentInsetStart,
                    end = Tokens.Grid.contentInsetEnd,
                ),
        )

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
            content = { pageIndicator() },
        )
        DockTray(
            slots = staticSlotRows(layout, LayoutContainer.DOCK).flatten(),
            launcherApps = launcherApps,
            dragState = dragState,
            actions = actions,
        )
    }
}

@Composable
private fun DockTray(
    slots: List<StaticSlot>,
    launcherApps: LauncherAppsRepository,
    dragState: ShortcutDragState,
    actions: StaticSlotActions,
    modifier: Modifier = Modifier,
) {
    TileSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Grid.ZoneBleed.dock)

            .wallpaperBlurRegion(key = "dock", cornerRadius = Tokens.Radius.zoneDock),
        level = GlassLevel.THIN,
        radius = Tokens.Radius.zoneDock,
        contentPadding = 0.dp,
        shadow = Tokens.Elevation.zone,
    ) {
        ShortcutSlotRow(
            slots = slots,
            launcherApps = launcherApps,
            dragState = dragState,
            showLabels = false,
            actions = actions,
            modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.Padding.zone),
        )
    }
}
