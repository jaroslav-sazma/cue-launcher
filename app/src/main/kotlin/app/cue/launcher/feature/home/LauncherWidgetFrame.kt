package app.cue.launcher.feature.home

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import app.cue.launcher.designsystem.GlassLevel
import app.cue.launcher.designsystem.TileSurface
import app.cue.launcher.designsystem.Tokens

@Composable
fun LauncherWidgetFrame(
    radius: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    TileSurface(
        modifier = modifier,
        level = GlassLevel.REGULAR,
        radius = radius,
        contentPadding = Tokens.Padding.widgetHolder,
        content = content,
    )
}
