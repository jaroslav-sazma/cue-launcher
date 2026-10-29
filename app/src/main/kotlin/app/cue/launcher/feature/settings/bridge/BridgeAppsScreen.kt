package app.cue.launcher.feature.settings.bridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cue.launcher.R
import app.cue.launcher.designsystem.AppIcon
import app.cue.launcher.designsystem.DetailPaneScaffold
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.rememberSearchListState
import app.cue.launcher.feature.drawer.listKey
import app.cue.launcher.feature.icons.rememberAppIcon
import app.cue.launcher.feature.settings.AppListHeader
import app.cue.launcher.feature.settings.SettingsSwitchRow
import app.cue.launcher.feature.settings.filterAppRows
import app.cue.launcher.system.InstalledApp
import app.cue.launcher.system.LauncherAppsRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// One row per package, as the exclusion is by package and holds in every profile. Cue: itself is
// never mirrored, so it is not offered.
@Composable
internal fun BridgeAppsScreen(launcherApps: LauncherAppsRepository, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val provide = LocalBridgeServices.current
    val config = remember(provide) { provide().config }
    val ownPackage = LocalContext.current.packageName
    val apps by produceState<List<InstalledApp>?>(initialValue = null, launcherApps, ownPackage) {
        value = launcherApps.installedApps()
            .filter { it.component.packageName != ownPackage }
            .distinctBy { it.component.packageName }
    }
    val excluded by remember(config) { config.settings.map { it.mirrorExcluded } }
        .collectAsStateWithLifecycle(initialValue = emptySet())
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(apps, query) { filterAppRows(apps.orEmpty(), query) }
    val scope = rememberCoroutineScope()

    DetailPaneScaffold(
        title = stringResource(R.string.bridge_apps_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        AppListHeader(
            hint = stringResource(R.string.bridge_apps_hint),
            searchPlaceholder = stringResource(R.string.bridge_apps_search),
            query = query,
            onQueryChange = { query = it },
            note = when {
                apps == null -> R.string.app_list_loading
                visible.isEmpty() -> R.string.builder_catalog_no_match
                else -> null
            },
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            state = rememberSearchListState(apps?.let { visible }),
            verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
        ) {
            items(items = visible, key = { it.listKey }) { app ->
                val packageName = app.component.packageName
                val icon = rememberAppIcon(app.component, launcherApps)
                SettingsSwitchRow(
                    label = app.label,
                    detail = packageName,
                    checked = packageName in excluded,
                    onCheckedChange = { wanted -> scope.launch { config.setMirrorExcluded(packageName, wanted) } },
                    leading = { AppIcon(icon = icon, contentDescription = null, size = Tokens.Settings.leadingBox) },
                )
            }
        }
    }
}
