package app.cue.launcher.feature.panel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.dragdrop.ShortcutDragState
import app.cue.launcher.feature.home.ShortcutSlotRow
import app.cue.launcher.feature.home.StaticSlot
import app.cue.launcher.feature.home.StaticSlotActions
import app.cue.launcher.system.LauncherAppsRepository

@Composable
fun PanelBar(
    slots: List<StaticSlot>,
    launcherApps: LauncherAppsRepository,
    dragState: ShortcutDragState,
    actions: StaticSlotActions,

    navigationBarInset: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(panelBarStripHeight(navigationBarInset)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        ShortcutSlotRow(
            slots = slots,
            launcherApps = launcherApps,
            dragState = dragState,
            showLabels = false,
            actions = actions,
            outlineEmptySlots = panelBarNeedsSlotHint(slots),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Tokens.Panel.Bar.horizontalPadding,
                    end = Tokens.Panel.Bar.horizontalPadding,
                    bottom = panelBarBottomPadding(navigationBarInset),
                )
                .height(Tokens.Size.icon),
        )
    }
}
