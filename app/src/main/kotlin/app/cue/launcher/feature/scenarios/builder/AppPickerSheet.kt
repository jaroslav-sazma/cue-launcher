package app.cue.launcher.feature.scenarios.builder

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.designsystem.GLYPH_CHEVRON
import app.cue.launcher.designsystem.AppIcon
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.rememberSearchListState
import app.cue.launcher.designsystem.toTextStyle
import app.cue.launcher.feature.drawer.DrawerSectionHeader
import app.cue.launcher.feature.drawer.listKey
import app.cue.launcher.feature.drawer.search.AppSearchIndex
import app.cue.launcher.feature.drawer.search.appSearchIndexOf
import app.cue.launcher.feature.drawer.vault.Vault
import app.cue.launcher.feature.drawer.vault.vaultOf
import app.cue.launcher.feature.icons.rememberAppIcon
import app.cue.launcher.system.AppActionEntry
import app.cue.launcher.system.AppActionGroup
import app.cue.launcher.system.AppActionKind
import app.cue.launcher.system.AppActionsRepository
import app.cue.launcher.system.InstalledApp
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.system.SearchableApp
import app.cue.launcher.system.ShortcutConfigurator
import app.cue.launcher.system.appActionGroups
import kotlinx.coroutines.launch
import app.cue.launcher.designsystem.TonalRow
import app.cue.launcher.designsystem.RowText
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.SheetFrame
import app.cue.launcher.designsystem.SheetHeader
import app.cue.launcher.designsystem.SheetSearchField
import app.cue.launcher.designsystem.SheetNote
import app.cue.launcher.designsystem.supportingInk
import app.cue.launcher.designsystem.supportingStyle

@Composable
internal fun AppPickerSheetContent(
    launcherApps: LauncherAppsRepository,
    onPick: (InstalledApp) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val ownSerial = remember(launcherApps) { launcherApps.profiles.serialOf(launcherApps.profiles.own) }
    val apps by produceState<List<InstalledApp>?>(initialValue = null, launcherApps, ownSerial) {
        value = launcherApps.installedApps()
            .filter { it.component.userSerial == ownSerial }
            .distinctBy { it.component.packageName }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val index = remember(apps) { appSearchIndexOf(apps.orEmpty().map { SearchableApp(it) }) }
    val vault = remember(apps) { vaultOf(apps.orEmpty()) }
    val rows = remember(index, vault, query) { pickerRows(index, vault, query) }

    SheetFrame(
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap)) {
                SheetHeader(
                    title = stringResource(R.string.action_app_title),
                    onClose = onClose,
                    onBack = onBack,
                    backDescription = R.string.action_sheet_back,
                )
                SheetSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(R.string.builder_catalog_search),
                )
            }
        },
        modifier = modifier,
        scrollable = false,
    ) {
        when {
            apps == null -> SheetNote(text = stringResource(R.string.app_list_loading))
            rows.isEmpty() -> SheetNote(text = stringResource(R.string.builder_catalog_no_match))
        }
        LazyColumn(state = rememberSearchListState(apps?.let { rows })) {
            items(items = rows, key = PickerRow::key) { row ->
                when (row) {
                    is PickerRow.Letter -> DrawerSectionHeader(text = row.letter)
                    is PickerRow.App -> AppPickerRow(
                        app = row.app,
                        launcherApps = launcherApps,
                        onClick = { onPick(row.app) },
                    )
                }
            }
        }
    }
}

private sealed interface PickerRow {

    val key: String

    data class Letter(val letter: String) : PickerRow {
        override val key: String get() = "#$letter"
    }

    data class App(val app: InstalledApp) : PickerRow {
        override val key: String get() = app.listKey
    }
}

private fun pickerRows(index: AppSearchIndex, vault: Vault, query: String): List<PickerRow> {
    if (query.isNotBlank()) return index.search(query).map { PickerRow.App(it.app) }
    return vault.sections.flatMap { section ->
        listOf(PickerRow.Letter(section.letter)) + section.apps.map(PickerRow::App)
    }
}

@Composable
private fun AppPickerRow(
    app: InstalledApp,
    launcherApps: LauncherAppsRepository,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val component: ComponentRef = app.component
    val icon = rememberAppIcon(component, launcherApps)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Tokens.Size.drawerRow)
            .clickable(onClick = onClick)
            .padding(horizontal = Tokens.Scenarios.rowPadding),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Drawer.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(icon = icon, contentDescription = null, size = Tokens.Size.drawerResultIcon)
        Text(
            text = app.label,
            color = MaterialTheme.colorScheme.onSurface,
            style = Tokens.Drawer.result.toTextStyle(),
        )
    }
}

@Composable
internal fun AppActionSheetContent(
    app: InstalledApp,
    appActions: AppActionsRepository,
    onPick: (TapAction, String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val packageName = app.component.packageName

    val entries by produceState<List<AppActionEntry>?>(null, appActions, packageName) {
        value = appActions.actionsOf(packageName)
    }
    val groups = remember(entries) { entries?.let(::appActionGroups).orEmpty() }
    var configFailed by remember { mutableStateOf(false) }
    val configurator = appActions.shortcutConfig
    val canReadShortcuts = remember(configurator) { configurator.canReadShortcuts() }
    val open = rememberAppActionOpener(
        configurator = configurator,
        packageName = packageName,
        onPick = onPick,
        onFailed = { configFailed = true },
        onStart = { configFailed = false },
    )

    SheetFrame(
        header = {
            SheetHeader(
                title = app.label,
                onClose = onClose,
                onBack = onBack,
                backDescription = R.string.action_sheet_back,
            )
        },
        modifier = modifier,
        scrollable = false,
    ) {
        when {
            entries == null -> SheetNote(text = stringResource(R.string.action_in_app_loading))
            !canReadShortcuts -> SheetNote(text = stringResource(R.string.action_in_app_needs_home))
            groups.isEmpty() -> SheetNote(text = stringResource(R.string.action_in_app_empty))
        }
        if (configFailed) SheetNote(text = stringResource(R.string.action_in_app_config_failed))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap)) {
            appActionRows(groups, open)
        }
    }
}

// The whole configure-then-pin dance lives here: the pinning launcher, the label it has to carry
// across the activity result, and the intent lookup. The sheet itself only renders rows.
@Composable
private fun rememberAppActionOpener(
    configurator: ShortcutConfigurator,
    packageName: String,
    onPick: (TapAction, String) -> Unit,
    onFailed: () -> Unit,
    onStart: () -> Unit,
): (AppActionEntry) -> Unit {
    var pendingLabel by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val configure = rememberLauncherForActivityResult(StartIntentSenderForResult()) { result ->
        scope.launch {
            val configured = configurator.resolve(result.data)
            when {
                configured != null -> onPick(configured.action, configured.label ?: pendingLabel)
                result.resultCode == Activity.RESULT_OK -> onFailed()
            }
        }
    }
    return { entry ->
        openAppAction(
            entry = entry,
            onPick = onPick,
            onConfigure = { label, configActivity ->
                scope.launch {
                    val sender = configurator.intentFor(packageName, configActivity)
                    if (sender == null) {
                        onFailed()
                    } else {
                        pendingLabel = label
                        onStart()
                        configure.launch(IntentSenderRequest.Builder(sender).build())
                    }
                }
            },
            onFailed = onFailed,
        )
    }
}

// An entry either carries a ready action, or names a configuration activity the app has to run
// first, or is unusable. Routing that stays free of coroutines so it reads as the three cases it is.
private fun openAppAction(
    entry: AppActionEntry,
    onPick: (TapAction, String) -> Unit,
    onConfigure: (label: String, configActivity: String) -> Unit,
    onFailed: () -> Unit,
) {
    val action = entry.action
    val configActivity = entry.configActivity
    when {
        action != null -> onPick(action, entry.label)
        configActivity != null -> onConfigure(entry.label, configActivity)
        else -> onFailed()
    }
}

private fun LazyListScope.appActionRows(
    groups: List<AppActionGroup>,
    onOpen: (AppActionEntry) -> Unit,
) {
    groups.forEach { group ->
        item(key = "#${group.kind.name}") {
            AppActionSection(kind = group.kind)
        }
        items(items = group.entries, key = AppActionEntry::key) { entry ->
            TonalRow(onClick = { onOpen(entry) }) {
                RowText(
                    title = entry.label,
                    modifier = Modifier.weight(1f),
                    detail = entry.detail,
                )
                GlyphIcon(
                    name = GLYPH_CHEVRON,
                    size = Tokens.Settings.glyph,
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun AppActionSection(kind: AppActionKind, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SectionLabel(title = appActionKindTitle(kind))
        Text(
            text = appActionKindDetail(kind),
            modifier = Modifier.padding(
                start = Tokens.Settings.screenPadding,
                end = Tokens.Settings.screenPadding,
                bottom = Tokens.Settings.textSpacing,
            ),
            style = supportingStyle,
            color = supportingInk(),
        )
    }
}
