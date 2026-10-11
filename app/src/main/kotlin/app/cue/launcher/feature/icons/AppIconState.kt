package app.cue.launcher.feature.icons

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.scenario.IconMode
import app.cue.launcher.designsystem.IconTint
import app.cue.launcher.designsystem.LocalSurfaceIconTint
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.system.ShortcutApp

@Composable
internal fun rememberAppIcon(
    component: ComponentRef,
    launcherApps: LauncherAppsRepository,
): ImageBitmap? {
    val revision by launcherApps.iconRevision.collectAsStateWithLifecycle()
    val surfaceTint = tintForSurface(launcherApps)
    if (surfaceTint != null) {
        val tinted by produceState<ImageBitmap?>(null, component, revision, surfaceTint) {
            value = launcherApps.surfaceIcon(component, surfaceTint)
        }
        return tinted
    }

    val icon by produceState(launcherApps.cachedIcon(component), component, revision) {
        value = launcherApps.icon(component)
    }
    return icon
}

@Composable
private fun tintForSurface(launcherApps: LauncherAppsRepository): IconTint? {
    val surfaceTint = LocalSurfaceIconTint.current ?: return null
    val revision by launcherApps.iconRevision.collectAsStateWithLifecycle()
    val tinted = remember(launcherApps, revision) { launcherApps.icons.mode == IconMode.TINTED }
    return surfaceTint.takeIf { tinted }
}

@Composable
internal fun rememberShortcut(
    component: ComponentRef,
    launcherApps: LauncherAppsRepository,
): ShortcutApp? {
    val revision by launcherApps.iconRevision.collectAsStateWithLifecycle()
    val shortcut by produceState<ShortcutApp?>(null, component, revision) {
        value = launcherApps.shortcut(component)
    }
    return shortcut
}

@Composable
internal fun rememberTintedPackageIcon(
    iconPackage: String?,
    launcherApps: LauncherAppsRepository,
    tint: IconTint,
): ImageBitmap? {
    val revision by launcherApps.iconRevision.collectAsStateWithLifecycle()
    val icon by produceState<ImageBitmap?>(null, iconPackage, revision, tint) {
        val component = iconPackage?.let { launcherApps.mainActivityOf(it) }
        value = component?.let { launcherApps.tintedIcon(it, tint) }
    }
    return icon
}

@Composable
internal fun rememberPackageIcon(
    iconPackage: String?,
    launcherApps: LauncherAppsRepository,
): ImageBitmap? {
    val revision by launcherApps.iconRevision.collectAsStateWithLifecycle()
    val surfaceTint = tintForSurface(launcherApps)
    val icon by produceState<ImageBitmap?>(null, iconPackage, revision, surfaceTint) {
        val component = iconPackage?.let { launcherApps.mainActivityOf(it) }
        value = component?.let {
            if (surfaceTint == null) launcherApps.icon(it) else launcherApps.surfaceIcon(it, surfaceTint)
        }
    }
    return icon
}
