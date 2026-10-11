package app.cue.launcher.feature.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.cue.launcher.R
import app.cue.launcher.data.drawer.HiddenAppRepository
import app.cue.launcher.data.launchlog.LaunchLogRepository
import app.cue.launcher.data.scenario.ScenarioFormat
import app.cue.launcher.data.scenario.ScenarioRepository
import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.LocalSurfaceIconTint
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.engine.ActivationStore
import app.cue.launcher.engine.SystemStateSource
import app.cue.launcher.feature.scenarios.ScenarioListScreen
import app.cue.launcher.feature.scenarios.ScenarioNotice
import app.cue.launcher.feature.scenarios.aichat.AiChatEntries
import app.cue.launcher.feature.scenarios.builder.FIRST_STEP
import app.cue.launcher.feature.scenarios.builder.HaAccess
import app.cue.launcher.feature.scenarios.builder.HaServiceInventory
import app.cue.launcher.feature.scenarios.builder.ScenarioBuilderScreen
import app.cue.launcher.feature.scenarios.edit.ScenarioEditScreen
import app.cue.launcher.feature.settings.ha.HaServices
import app.cue.launcher.feature.settings.ha.haActionTester
import app.cue.launcher.feature.settings.ha.HomeAssistantScreen
import app.cue.launcher.feature.widgets.WidgetHosting
import app.cue.launcher.system.AppActionsRepository
import app.cue.launcher.system.ConfigBackup
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.system.iconpack.IconPackRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
data object ScenariosTab : NavKey

@Serializable
data object AppearanceTab : NavKey

@Serializable
data object BackupsTab : NavKey

@Serializable
data class ScenarioBuilder(val scenarioJson: String? = null, val step: Int = FIRST_STEP) : NavKey

@Serializable
data class ScenarioEditor(val id: String, val notice: String? = null) : NavKey

@Serializable
data object HomeAssistant : NavKey

@Serializable
data object HiddenApps : NavKey

@Serializable
data object OpenSourceLicenses : NavKey

@Serializable
data class LicenseTextKey(val notice: String) : NavKey

private val TABS = listOf(ScenariosTab, AppearanceTab, BackupsTab)

@Composable
fun SettingsNavigation(
    onClose: () -> Unit,
    scenarios: ScenarioRepository,
    activations: ActivationStore,
    systemState: SystemStateSource,
    launcherApps: LauncherAppsRepository,
    appActions: AppActionsRepository,
    widgets: WidgetHosting,
    settings: SettingsRepository,
    iconPacks: IconPackRepository,
    hiddenApps: HiddenAppRepository,
    launchLog: LaunchLogRepository,
    backup: ConfigBackup,
    ha: HaServices,
    modifier: Modifier = Modifier,
    importUri: Uri? = null,
    onImportConsumed: () -> Unit = {},
) {
    val backStack = rememberNavBackStack(ScenariosTab)
    val deps = remember(scenarios, activations, systemState, launcherApps) {
        SettingsDeps(scenarios, activations, systemState, launcherApps, appActions, widgets, settings)
    }

    var listNotice by remember { mutableStateOf<ScenarioNotice?>(null) }
    val selectTab: (NavKey) -> Unit = { tab ->

        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        backStack[0] = tab
    }

    LaunchedEffect(importUri) {
        if (importUri != null) selectTab(BackupsTab)
    }

    BackHandler(enabled = backStack.size == 1) {
        if (backStack.first() == ScenariosTab) onClose() else selectTab(ScenariosTab)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {

        CompositionLocalProvider(LocalSurfaceIconTint provides rememberSettingsIconTint()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SettingsDestinations(
                    backStack = backStack,
                    onClose = onClose,
                    selectTab = selectTab,
                    deps = deps,
                    iconPacks = iconPacks,
                    hiddenApps = hiddenApps,
                    launchLog = launchLog,
                    backup = backup,
                    ha = ha,
                    importUri = importUri,
                    onImportConsumed = onImportConsumed,
                    listNotice = listNotice,
                    onListNotice = { listNotice = it },
                    modifier = Modifier.weight(1f),
                )
                if (backStack.size == 1) {
                    SettingsTabBar(selected = backStack.first(), onSelect = selectTab)
                }
            }
        }
    }
}

@Composable
private fun SettingsDestinations(
    backStack: androidx.navigation3.runtime.NavBackStack<NavKey>,
    onClose: () -> Unit,
    selectTab: (NavKey) -> Unit,
    deps: SettingsDeps,
    iconPacks: IconPackRepository,
    hiddenApps: HiddenAppRepository,
    launchLog: LaunchLogRepository,
    backup: ConfigBackup,
    ha: HaServices,
    importUri: Uri?,
    onImportConsumed: () -> Unit,
    listNotice: ScenarioNotice?,
    onListNotice: (ScenarioNotice?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pop: () -> Unit = { backStack.removeAt(backStack.lastIndex) }
    val haAccess = rememberHaAccess(backStack, ha)
    val scope = rememberCoroutineScope()
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { if (backStack.size > 1) pop() else onClose() },
        entryProvider = entryProvider {
            entry<ScenariosTab> {
                ScenariosDestination(deps, backStack, selectTab, listNotice) { onListNotice(null) }
            }
            entry<ScenarioBuilder> { key -> BuilderDestination(key, deps, haAccess, pop, onListNotice) }
            entry<ScenarioEditor> { key ->
                EditorDestination(key, deps, haAccess, pop, onListNotice)
            }
            entry<HomeAssistant> { HaDestination(deps.scenarios, ha, pop) }
            entry<OpenSourceLicenses> {
                LicensesScreen(onOpen = { backStack.add(LicenseTextKey(it.name)) }, onBack = pop)
            }
            entry<LicenseTextKey> { key ->
                LicenseTextScreen(name = key.notice, onBack = pop, onUnknown = { backStack.remove(key) })
            }
            entry<AiChatHandoff> { AiChatHandoffDestination(ha.configs, deps.settings, scope, backStack, pop) }
            entry<PasteScenario> { PasteScenarioDestination(ha.configs, deps.settings, scope, backStack, pop) }
            entry<AppearanceTab> {
                AppearanceScreen(
                    settings = deps.settings,
                    iconPacks = iconPacks,
                    launcherApps = deps.launcherApps,
                    appActions = deps.appActions,
                    hiddenApps = hiddenApps,
                    launchLog = launchLog,
                    onOpenHiddenApps = { backStack.add(HiddenApps) },

                    ha = haAccess,
                )
            }
            entry<HiddenApps> {
                HiddenAppsScreen(
                    launcherApps = deps.launcherApps,
                    hiddenApps = hiddenApps,
                    onBack = pop,
                )
            }
            entry<BackupsTab> {
                BackupsScreen(
                    scenarios = deps.scenarios,
                    settings = deps.settings,
                    backup = backup,
                    importUri = importUri,
                    onImportConsumed = onImportConsumed,

                    onEditScenario = { id, notice -> backStack.add(ScenarioEditor(id, notice)) },
                )
            }
        },
    )
}

@Composable
private fun rememberHaAccess(
    backStack: androidx.navigation3.runtime.NavBackStack<NavKey>,
    ha: HaServices,
): HaAccess = remember(backStack, ha) {
    HaAccess(
        tester = haActionTester(ha.gateway),
        states = { entities -> ha.states.read(entities) },
        entities = { ha.gateway.entities().entities },
        services = HaServiceInventory(
            list = { ha.gateway.services().services },
            names = { language, domains -> ha.gateway.serviceNames(language, domains) },
        ),
        onConfigure = { backStack.add(HomeAssistant) },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsTabBar(
    selected: NavKey,
    onSelect: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    ShortNavigationBar(modifier = modifier) {
        TABS.forEach { tab ->
            ShortNavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },

                icon = {
                    GlyphIcon(
                        name = tabGlyph(tab),
                        size = Tokens.Settings.glyph,
                        tint = LocalContentColor.current,
                    )
                },
                label = { Text(text = stringResource(tabTitle(tab))) },
            )
        }
    }
}

private fun tabGlyph(tab: NavKey): String = when (tab) {
    AppearanceTab -> "palette"
    BackupsTab -> "backup-restore"
    else -> "lightning-bolt"
}

private fun tabTitle(tab: NavKey): Int = when (tab) {
    AppearanceTab -> R.string.settings_tab_appearance
    BackupsTab -> R.string.settings_tab_backups
    else -> R.string.settings_tab_scenarios
}

@Suppress("LongParameterList")
@Stable
private class SettingsDeps(
    val scenarios: ScenarioRepository,
    val activations: ActivationStore,
    val systemState: SystemStateSource,
    val launcherApps: LauncherAppsRepository,
    val appActions: AppActionsRepository,
    val widgets: WidgetHosting,
    val settings: SettingsRepository,
)

@Composable
private fun ScenariosDestination(
    deps: SettingsDeps,
    backStack: androidx.navigation3.runtime.NavBackStack<NavKey>,
    selectTab: (NavKey) -> Unit,
    listNotice: ScenarioNotice?,
    onNoticeConsumed: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val aiChatState by remember(deps.settings) {
        deps.settings.settings.map { it.aiChatScenarios to it.aiChatPending }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false to false)
    val (aiChatOn, pending) = aiChatState
    val aiChat = remember(backStack, pending) {
        AiChatEntries(
            onAiChat = { backStack.pushOnce(AiChatHandoff) },
            onPaste = { backStack.pushOnce(PasteScenario) },
            pending = pending,
            onDismissPending = { scope.launch { deps.settings.setAiChatPending(false) } },
        )
    }
    ScenarioListScreen(
        repository = deps.scenarios,
        systemState = deps.systemState,
        launcherApps = deps.launcherApps,
        onOpenBackups = { selectTab(BackupsTab) },
        onHomeAssistant = { backStack.add(HomeAssistant) },
        onOpenLicenses = { backStack.add(OpenSourceLicenses) },
        onNewScenario = { backStack.add(ScenarioBuilder()) },
        onEditScenario = { backStack.add(ScenarioEditor(it.id)) },
        notice = listNotice,
        onNoticeConsumed = onNoticeConsumed,
        aiChat = aiChat.takeIf { aiChatOn },
    )
}

@Composable
private fun BuilderDestination(
    key: ScenarioBuilder,
    deps: SettingsDeps,
    haAccess: HaAccess,
    pop: () -> Unit,
    onListNotice: (ScenarioNotice?) -> Unit,
) {
    val initial = remember(key.scenarioJson) { key.scenarioJson?.let(ScenarioFormat::decodeCanonical) }
    ScenarioBuilderScreen(
        systemState = deps.systemState,
        scenarios = deps.scenarios,
        launcherApps = deps.launcherApps,
        appActions = deps.appActions,
        widgets = deps.widgets,
        ha = haAccess,
        initial = initial,
        initialStep = key.step,
        onClose = pop,
        onSaved = { scenario ->
            onListNotice(ScenarioNotice.Saved(scenario))
            pop()
        },
    )
}

@Composable
private fun EditorDestination(
    key: ScenarioEditor,
    deps: SettingsDeps,
    haAccess: HaAccess,
    pop: () -> Unit,
    onListNotice: (ScenarioNotice?) -> Unit,
) {
    ScenarioEditScreen(
        scenarioId = key.id,
        notice = key.notice,
        scenarios = deps.scenarios,
        activations = deps.activations,
        systemState = deps.systemState,
        launcherApps = deps.launcherApps,
        appActions = deps.appActions,
        widgets = deps.widgets,
        ha = haAccess,
        onClose = pop,
        onNotice = onListNotice,
    )
}

@Composable
private fun HaDestination(scenarios: ScenarioRepository, ha: HaServices, pop: () -> Unit) {
    HomeAssistantScreen(
        configs = ha.configs,
        scenarios = scenarios,
        log = ha.log,
        setup = ha.setup,
        onClose = pop,
    )
}
