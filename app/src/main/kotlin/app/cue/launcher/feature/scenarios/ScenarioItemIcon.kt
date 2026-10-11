package app.cue.launcher.feature.scenarios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import app.cue.launcher.designsystem.AppIcon
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.icons.rememberPackageIcon
import app.cue.launcher.system.LauncherAppsRepository

@Composable
internal fun ScenarioItemIcon(
    glyph: String?,
    iconPackage: String?,
    launcherApps: LauncherAppsRepository,
    modifier: Modifier = Modifier,
    size: Dp = Tokens.Size.glyphInTile,
    tint: Color? = null,
) {
    val icon = rememberPackageIcon(iconPackage, launcherApps)
    if (iconPackage != null) {

        AppIcon(icon = icon, contentDescription = null, modifier = modifier, size = size)
        return
    }
    GlyphIcon(
        name = glyph,
        modifier = modifier,
        size = size,

        tint = tint ?: LocalCueColors.current.textPrimary,
    )
}
