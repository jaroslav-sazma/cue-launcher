package app.cue.launcher.feature.icons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.scenario.IconMode
import app.cue.launcher.designsystem.CueMenuItem
import app.cue.launcher.designsystem.LocalTintedIcons
import app.cue.launcher.designsystem.rememberIconTint
import app.cue.launcher.feature.lock.LauncherAction
import app.cue.launcher.feature.lock.LocalLayoutLocked
import app.cue.launcher.system.LauncherAppsRepository

@Composable
fun IconAppearanceHost(launcherApps: LauncherAppsRepository, content: @Composable () -> Unit) {
    val icons = launcherApps.icons
    val tint = rememberIconTint()
    SideEffect { icons.setTint(tint) }
    val revision by launcherApps.iconRevision.collectAsStateWithLifecycle()
    val tinted = remember(icons, revision) { icons.mode == IconMode.TINTED }
    CompositionLocalProvider(LocalTintedIcons provides tinted, content = content)
}

@Composable
private fun rememberIconOverride(
    component: ComponentRef,
    launcherApps: LauncherAppsRepository,
): Boolean? {
    val icons = launcherApps.icons
    val revision by launcherApps.iconRevision.collectAsStateWithLifecycle()
    return remember(icons, component, revision) {
        if (icons.mode == IconMode.TINTED) icons.isNative(component) else null
    }
}

@Composable
internal fun IconOverrideMenuItem(
    component: ComponentRef,
    launcherApps: LauncherAppsRepository,
    onDone: () -> Unit,
) {
    if (!LauncherAction.OVERRIDE_ICON.availableWhen(LocalLayoutLocked.current)) return
    val icons = launcherApps.icons
    val state = rememberIconOverride(component, launcherApps) ?: return
    CueMenuItem(
        label = stringResource(
            if (state) R.string.icon_override_tint else R.string.icon_override_native,
        ),
        glyph = GLYPH_ICON_OVERRIDE,
        onClick = {
            onDone()
            icons.setNative(component, native = !state)
        },
    )
}

private const val GLYPH_ICON_OVERRIDE = "palette-outline"
