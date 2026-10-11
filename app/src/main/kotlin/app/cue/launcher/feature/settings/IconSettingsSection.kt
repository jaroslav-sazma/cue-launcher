package app.cue.launcher.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.scenario.IconMode
import app.cue.launcher.data.settings.LauncherSettings
import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.data.settings.selectedPack
import app.cue.launcher.designsystem.AppIcon
import app.cue.launcher.designsystem.IconTint
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.system.iconpack.IconPack
import app.cue.launcher.system.iconpack.IconPackRepository
import kotlinx.coroutines.launch

@Composable
internal fun IconSettingsSection(
    settings: SettingsRepository,
    stored: LauncherSettings,
    iconPacks: IconPackRepository,
    launcherApps: LauncherAppsRepository,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val packs by iconPacks.installedPacks.collectAsStateWithLifecycle(initialValue = emptyList())
    val mode = stored.appearance.iconMode
    val selectedPack = stored.appearance.selectedPack
    val sample = previewComponent(launcherApps, iconPacks)

    SettingsSection(
        title = stringResource(R.string.settings_icons_section),
        modifier = modifier,
        choices = true,
    ) {
        ModeRow(
            label = stringResource(R.string.settings_icons_native),
            detail = stringResource(R.string.settings_icons_native_hint),
            selected = mode == IconMode.NATIVE,
            preview = sample,
            previewMode = IconMode.NATIVE,
            launcherApps = launcherApps,
            onSelect = { scope.launch { settings.setIconMode(IconMode.NATIVE) } },
        )
        IconPackRows(
            packs = packs,
            remembered = stored.appearance.iconPack,
            selectedPack = selectedPack,
            active = mode == IconMode.ICONPACK,
            preview = sample,
            launcherApps = launcherApps,
            onSelect = { packageName ->
                scope.launch { settings.setIconMode(IconMode.ICONPACK, packageName) }
            },
        )
        ModeRow(
            label = stringResource(R.string.settings_icons_tinted),
            detail = stringResource(R.string.settings_icons_tinted_hint),
            selected = mode == IconMode.TINTED,
            preview = sample,
            previewMode = IconMode.TINTED,
            launcherApps = launcherApps,
            onSelect = { scope.launch { settings.setIconMode(IconMode.TINTED) } },
        )
    }
}

@Composable
private fun IconPackRows(
    packs: List<IconPack>,
    remembered: String?,
    selectedPack: String?,
    active: Boolean,
    preview: ComponentRef?,
    launcherApps: LauncherAppsRepository,
    onSelect: (String) -> Unit,
) {
    val rememberedPack = packs.firstOrNull { it.packageName == remembered }
    ModeRow(
        label = stringResource(R.string.settings_icons_pack),

        detail = rememberedPack?.label ?: stringResource(
            if (packs.isEmpty()) R.string.settings_icons_pack_missing else R.string.settings_icons_pack_hint,
        ),
        selected = active,
        enabled = packs.isNotEmpty(),
        preview = preview,
        previewMode = IconMode.ICONPACK,
        launcherApps = launcherApps,

        onSelect = { (rememberedPack ?: packs.firstOrNull())?.let { onSelect(it.packageName) } },
    )

    if (active && packs.size > 1) {
        packs.forEach { pack ->
            PackRow(
                pack = pack,
                selected = selectedPack == pack.packageName,
                onSelect = { onSelect(pack.packageName) },
            )
        }
    }
}

@Composable
private fun ModeRow(
    label: String,
    detail: String,
    selected: Boolean,
    preview: ComponentRef?,
    previewMode: IconMode,
    launcherApps: LauncherAppsRepository,
    onSelect: () -> Unit,
    enabled: Boolean = true,
) {
    SettingsChoiceRow(
        label = label,
        detail = detail,
        selected = selected,
        onSelect = onSelect,
        enabled = enabled,
        trailing = {
            IconModePreview(component = preview, mode = previewMode, launcherApps = launcherApps)
        },
    )
}

@Composable
private fun PackRow(pack: IconPack, selected: Boolean, onSelect: () -> Unit) {
    SettingsChoiceRow(
        label = pack.label,
        detail = pack.packageName,
        selected = selected,
        onSelect = onSelect,

        modifier = Modifier.padding(start = Tokens.Settings.screenPadding),
    )
}

@Composable
private fun IconModePreview(
    component: ComponentRef?,
    mode: IconMode,
    launcherApps: LauncherAppsRepository,
) {
    val revision by launcherApps.iconRevision.collectAsStateWithLifecycle()

    val tint = rememberSettingsIconTint()
    val icon by produceState<ImageBitmap?>(null, component, mode, revision, tint) {
        value = component?.let { launcherApps.previewIcon(it, mode, tint) }
    }

    AppIcon(icon = icon, contentDescription = null, size = Tokens.Settings.iconPreview)
}

@Composable
private fun previewComponent(
    launcherApps: LauncherAppsRepository,
    iconPacks: IconPackRepository,
): ComponentRef? {
    val revision by launcherApps.iconRevision.collectAsStateWithLifecycle()
    val component by produceState<ComponentRef?>(null, launcherApps, iconPacks, revision) {
        val apps = launcherApps.installedApps().map { it.component }
        value = iconPacks.firstMapped(apps) ?: apps.firstOrNull()
    }
    return component
}
