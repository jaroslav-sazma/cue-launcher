package app.cue.launcher.feature.home

import android.graphics.Rect
import android.widget.Toast
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.designsystem.CueMenuItem
import app.cue.launcher.designsystem.CueMenuRule
import app.cue.launcher.designsystem.CueMenuSection
import app.cue.launcher.system.AppShortcut
import app.cue.launcher.system.LauncherAppsRepository

// Asking the system for shortcuts is worth it only once the menu wants them, but the answer is kept
// afterwards: the menu keeps rendering while it animates away, and the rows must not drop out of it.
@Composable
internal fun rememberAppShortcuts(
    component: ComponentRef,
    launcherApps: LauncherAppsRepository,
    menuOpen: Boolean,
): List<AppShortcut>? {
    var shortcuts by remember(component, launcherApps) { mutableStateOf<List<AppShortcut>?>(null) }
    LaunchedEffect(component, launcherApps, menuOpen) {
        if (menuOpen && shortcuts == null) shortcuts = launcherApps.appShortcuts(component)
    }
    return if (menuOpen) shortcuts else shortcuts ?: emptyList()
}

@Composable
internal fun ColumnScope.AppShortcutMenuItems(
    shortcuts: List<AppShortcut>,
    launcherApps: LauncherAppsRepository,
    sourceBounds: Rect?,
    onDone: () -> Unit,
    trailingRule: Boolean,
) {
    val context = LocalContext.current
    if (shortcuts.isEmpty()) return
    CueMenuSection(label = stringResource(R.string.menu_shortcuts))

    shortcuts.take(MAX_SHORTCUTS).forEach { shortcut ->
        CueMenuItem(

            label = shortcut.label,
            glyph = GLYPH_SHORTCUT,
            onClick = {
                onDone()
                if (!launcherApps.startShortcut(shortcut, sourceBounds)) {
                    Toast.makeText(context, R.string.home_launch_failed, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
    if (trailingRule) CueMenuRule()
}

private const val MAX_SHORTCUTS = 4

private const val GLYPH_SHORTCUT = "lightning-bolt-outline"
