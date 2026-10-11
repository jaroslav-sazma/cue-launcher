package app.cue.launcher.feature.panel

import androidx.compose.ui.unit.Dp
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.home.StaticSlot

// An all-empty dock gives no clue that it takes icons at all, so it shows its slots. One icon is
// enough of a clue, from then on the outlines belong to a drag again.
fun panelBarNeedsSlotHint(slots: List<StaticSlot>): Boolean = slots.all { it.item == null }

fun panelBarStripHeight(navigationBarInset: Dp): Dp =
    Tokens.Size.PanelBar.height +
        (panelBarBottomPadding(navigationBarInset) - Tokens.Panel.Bar.bottomPadding)

fun panelBarBottomPadding(navigationBarInset: Dp): Dp =
    maxOf(Tokens.Panel.Bar.bottomPadding, navigationBarInset)
