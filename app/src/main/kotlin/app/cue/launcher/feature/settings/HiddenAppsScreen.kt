package app.cue.launcher.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.drawer.HiddenAppRepository
import app.cue.launcher.designsystem.AppIcon
import app.cue.launcher.designsystem.SearchField
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.rememberSearchListState
import app.cue.launcher.feature.drawer.listKey
import app.cue.launcher.feature.icons.rememberAppIcon
import app.cue.launcher.system.InstalledApp
import app.cue.launcher.system.LauncherAppsRepository
import kotlinx.coroutines.launch
import app.cue.launcher.designsystem.DetailPaneScaffold
import app.cue.launcher.designsystem.supportingInk
import app.cue.launcher.designsystem.supportingStyle

@Composable
fun HiddenAppsScreen(
    launcherApps: LauncherAppsRepository,
    hiddenApps: HiddenAppRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {

    val apps by produceState<List<InstalledApp>?>(initialValue = null, launcherApps) {
        value = launcherApps.installedApps()
    }
    val hidden by remember(hiddenApps) { hiddenApps.observeHidden() }
        .collectAsStateWithLifecycle(initialValue = emptySet())
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(apps, query) { filterHiddenAppRows(apps.orEmpty(), query) }
    val scope = rememberCoroutineScope()

    DetailPaneScaffold(
        title = stringResource(R.string.settings_hidden_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        HiddenAppsHeader(
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
                HiddenAppRow(
                    app = app,
                    launcherApps = launcherApps,
                    hidden = app.component in hidden,
                    onHiddenChange = { wanted -> scope.launch { hiddenApps.setHidden(app.component, wanted) } },
                )
            }
        }
    }
}

@Composable
private fun HiddenAppsHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    @StringRes note: Int?,
    modifier: Modifier = Modifier,
) {
    val noteModifier = Modifier.padding(
        horizontal = Tokens.Settings.screenPadding,
        vertical = Tokens.Settings.textSpacing,
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_hidden_hint),
            modifier = noteModifier,
            style = supportingStyle,
            color = supportingInk(),
        )
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.settings_hidden_search),
            container = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Tokens.Settings.surfacePadding,
                    vertical = Tokens.Settings.rowGap,
                ),
        )
        if (note != null) {
            Text(
                text = stringResource(note),
                modifier = noteModifier,
                style = supportingStyle,
                color = supportingInk(),
            )
        }
    }
}

private fun filterHiddenAppRows(apps: List<InstalledApp>, query: String): List<InstalledApp> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return apps
    return apps.filter {
        it.label.contains(trimmed, ignoreCase = true) ||
            it.component.packageName.contains(trimmed, ignoreCase = true)
    }
}

@Composable
private fun HiddenAppRow(
    app: InstalledApp,
    launcherApps: LauncherAppsRepository,
    hidden: Boolean,
    onHiddenChange: (Boolean) -> Unit,
) {
    val icon = rememberAppIcon(app.component, launcherApps)
    SettingsSwitchRow(
        label = app.label,
        detail = app.component.packageName,
        checked = hidden,
        onCheckedChange = onHiddenChange,
        leading = {
            AppIcon(icon = icon, contentDescription = null, size = Tokens.Settings.leadingBox)
        },
    )
}
