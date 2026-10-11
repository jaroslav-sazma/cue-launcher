package app.cue.launcher.feature.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.labelOnWallpaperStyle
import app.cue.launcher.designsystem.labelsVisibleAt

// A static cell centres icon and label together. Anything standing in for an icon — a widget, an
// empty slot's drop target — keeps the label's room with an empty line of it, so it lands on the
// same band as the icons in its row.
@Composable
internal fun StaticLabelReserve() {
    if (!labelsVisibleAt(LocalDensity.current.fontScale)) return
    Spacer(modifier = Modifier.height(Tokens.Type.labelGapBelowIcon))
    Text(text = "", style = labelOnWallpaperStyle(), maxLines = Tokens.Type.LABEL_LINES)
}
