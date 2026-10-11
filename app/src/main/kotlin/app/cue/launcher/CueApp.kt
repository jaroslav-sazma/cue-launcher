package app.cue.launcher

import android.net.Uri
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import app.cue.launcher.data.drawer.HiddenAppRepository
import app.cue.launcher.data.launchlog.LaunchLogRepository
import app.cue.launcher.data.layout.LauncherLayoutRepository
import app.cue.launcher.data.scenario.ScenarioRepository
import app.cue.launcher.data.settings.LauncherSettings
import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.designsystem.MenuBackdropHost
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.WallpaperBlurRegionsLayer
import app.cue.launcher.engine.ActivationStore
import app.cue.launcher.engine.ScenarioEngine
import app.cue.launcher.engine.SystemStateSource
import app.cue.launcher.feature.dragdrop.DragPayload
import app.cue.launcher.feature.dragdrop.ShortcutPlacement
import app.cue.launcher.feature.dragdrop.rememberShortcutPlacement
import app.cue.launcher.feature.dragdrop.shortcutDropTarget
import app.cue.launcher.feature.drawer.DrawerDrag
import app.cue.launcher.feature.drawer.IntentDrawer
import app.cue.launcher.feature.home.LauncherPager
import app.cue.launcher.feature.home.WidgetSettingsAccess
import app.cue.launcher.feature.lock.LocalLayoutLocked
import app.cue.launcher.feature.onboarding.OnboardingScreen
import app.cue.launcher.feature.onboarding.onboardingVisible
import app.cue.launcher.feature.settings.SettingsNavigation
import app.cue.launcher.feature.scenarios.builder.HaAccess
import app.cue.launcher.feature.scenarios.builder.HaServiceInventory
import app.cue.launcher.feature.settings.ha.HaServices
import app.cue.launcher.feature.settings.ha.haActionTester
import app.cue.launcher.feature.widgets.WidgetHosting
import app.cue.launcher.system.ActionExecutor
import app.cue.launcher.system.AppActionsRepository
import app.cue.launcher.system.ConfigBackup
import app.cue.launcher.system.ContactsRepository
import app.cue.launcher.system.HomeRoleStatus
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.system.iconpack.IconPackRepository

@Composable
fun CueApp(
    roleStatus: HomeRoleStatus,
    launcherApps: LauncherAppsRepository,
    appActions: AppActionsRepository,
    layoutRepository: LauncherLayoutRepository,
    settingsRepository: SettingsRepository,
    launchLog: LaunchLogRepository,
    contacts: ContactsRepository,
    hiddenApps: HiddenAppRepository,
    scenarios: ScenarioRepository,
    activations: ActivationStore,
    systemState: SystemStateSource,
    engine: ScenarioEngine,
    actionExecutor: ActionExecutor,
    widgets: WidgetHosting,
    iconPacks: IconPackRepository,
    backup: ConfigBackup,
    ha: HaServices,
    onRequestHomeRole: () -> Unit,
    modifier: Modifier = Modifier,
    homeResetSignal: Int = 0,
    importUri: Uri? = null,
    onImportConsumed: () -> Unit = {},
) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    val stored = rememberStoredSettings(settingsRepository)

    CueAppEffects(homeResetSignal, importUri) { openSettings ->
        drawerOpen = false
        settingsOpen = openSettings
    }

    if (onboardingVisible(stored)) {
        FirstRun(
            roleStatus = roleStatus,
            launcherApps = launcherApps,
            layoutRepository = layoutRepository,
            settingsRepository = settingsRepository,
            onRequestHomeRole = onRequestHomeRole,
            modifier = modifier,
        )
    } else if (settingsOpen) {
        SettingsNavigation(
            onClose = { settingsOpen = false },
            scenarios = scenarios,
            activations = activations,
            systemState = systemState,
            launcherApps = launcherApps,
            appActions = appActions,
            widgets = widgets,
            settings = settingsRepository,
            iconPacks = iconPacks,
            hiddenApps = hiddenApps,
            launchLog = launchLog,
            backup = backup,
            ha = ha,
            modifier = modifier,
            importUri = importUri,
            onImportConsumed = onImportConsumed,
        )
    } else {
        HomeWithDrawer(
            roleStatus = roleStatus,
            launcherApps = launcherApps,
            layoutRepository = layoutRepository,
            settingsRepository = settingsRepository,
            launchLog = launchLog,
            contacts = contacts,
            hiddenApps = hiddenApps,
            engine = engine,
            actionExecutor = actionExecutor,
            widgets = widgets,
            appActions = appActions,
            ha = ha,
            onRequestHomeRole = onRequestHomeRole,
            onOpenSettings = { settingsOpen = true },
            drawerOpen = drawerOpen,
            onDrawerOpenChange = { drawerOpen = it },
            modifier = modifier,
            homeResetSignal = homeResetSignal,
        )
    }
}

@Composable
private fun rememberWidgetSettingsAccess(
    settings: SettingsRepository,
    launcherApps: LauncherAppsRepository,
    appActions: AppActionsRepository,
    ha: HaServices,
    onConfigureHa: () -> Unit,
): WidgetSettingsAccess {
    val configureHa by rememberUpdatedState(onConfigureHa)
    return remember(settings, launcherApps, appActions, ha) {
        WidgetSettingsAccess(
            settings = settings,
            launcherApps = launcherApps,
            appActions = appActions,
            ha = HaAccess(
                tester = haActionTester(ha.gateway),
                states = { entities -> ha.states.read(entities) },
                entities = { ha.gateway.entities().entities },
                services = HaServiceInventory(
                    list = { ha.gateway.services().services },
                    names = { language, domains -> ha.gateway.serviceNames(language, domains) },
                ),
                onConfigure = { configureHa() },
            ),
        )
    }
}

@Composable
private fun rememberStoredSettings(settingsRepository: SettingsRepository): LauncherSettings? {
    val stored by remember(settingsRepository) { settingsRepository.settings }
        .collectAsStateWithLifecycle(initialValue = null as LauncherSettings?)

    ReportDrawnWhen { stored != null }
    return stored
}

@Composable
private fun CueAppEffects(
    homeResetSignal: Int,
    importUri: Uri?,
    onCloseOverlays: (openSettings: Boolean) -> Unit,
) {
    LaunchedEffect(homeResetSignal) { if (homeResetSignal > 0) onCloseOverlays(false) }
    LaunchedEffect(importUri) { if (importUri != null) onCloseOverlays(true) }
}

@Composable
private fun FirstRun(
    roleStatus: HomeRoleStatus,
    launcherApps: LauncherAppsRepository,
    layoutRepository: LauncherLayoutRepository,
    settingsRepository: SettingsRepository,
    onRequestHomeRole: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    OnboardingScreen(
        roleStatus = roleStatus,
        launcherApps = launcherApps,
        layoutRepository = layoutRepository,
        onRequestHomeRole = onRequestHomeRole,
        onDone = { scope.launch { settingsRepository.setOnboardingSeen(true) } },
        modifier = modifier,
    )
}

@Composable
private fun HomeWithDrawer(
    roleStatus: HomeRoleStatus,
    launcherApps: LauncherAppsRepository,
    layoutRepository: LauncherLayoutRepository,
    settingsRepository: SettingsRepository,
    launchLog: LaunchLogRepository,
    contacts: ContactsRepository,
    hiddenApps: HiddenAppRepository,
    engine: ScenarioEngine,
    actionExecutor: ActionExecutor,
    widgets: WidgetHosting,
    appActions: AppActionsRepository,
    ha: HaServices,
    onRequestHomeRole: () -> Unit,
    onOpenSettings: () -> Unit,
    drawerOpen: Boolean,
    onDrawerOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    homeResetSignal: Int = 0,
) {
    val widgetSettings = rememberWidgetSettingsAccess(
        settings = settingsRepository,
        launcherApps = launcherApps,
        appActions = appActions,
        ha = ha,
        onConfigureHa = onOpenSettings,
    )
    val stored by remember(settingsRepository) { settingsRepository.settings }
        .collectAsStateWithLifecycle(initialValue = LauncherSettings())
    val transitions = rememberHomeTransitions(drawerOpen)
    val reducedMotion = transitions.reducedMotion
    val progress = transitions::drawerProgress
    val menuBackdrop = transitions.menuBackdrop
    // The launcher underneath stays composed whatever the drawer does — rebuilding a home screen,
    // a widget page and every host view on them is not something that belongs in the frame the
    // drawer closes on. Only the drawer itself comes and goes, and this flips at the ends of its
    // travel rather than on every frame of it.
    val drawerLeaving by remember(transitions) { derivedStateOf { transitions.drawerProgress() > 0f } }

    val closeDrawer = { onDrawerOpenChange(false) }
    val placement = rememberShortcutPlacement(layoutRepository, onPlaced = closeDrawer)
    val drag = rememberDrawerDrag(placement, hiddenApps)

    CompositionLocalProvider(LocalLayoutLocked provides stored.layoutLocked) {
        Box(modifier = modifier.fillMaxSize().homeDropTarget(placement, drag, closeDrawer)) {
            WallpaperBlurRegionsLayer {
                MenuBackdropHost(state = menuBackdrop, modifier = Modifier.fillMaxSize()) {

                    LauncherPager(
                        roleStatus = roleStatus,
                        launcherApps = launcherApps,
                        layoutRepository = layoutRepository,
                        settingsRepository = settingsRepository,
                        engine = engine,
                        actionExecutor = actionExecutor,
                        widgets = widgets,
                        widgetSettings = widgetSettings,
                        dragState = placement.dragState,
                        onRequestHomeRole = onRequestHomeRole,
                        onOpenSettings = onOpenSettings,
                        onOpenDrawer = { onDrawerOpenChange(true) },
                        homeResetSignal = homeResetSignal,
                        modifier = Modifier.homeExit(progress, reducedMotion),
                    )
                    if (drawerOpen || drawerLeaving) {
                        IntentDrawer(
                            launcherApps = launcherApps,
                            launchLog = launchLog,
                            contacts = contacts,
                            hiddenApps = hiddenApps,
                            settings = settingsRepository,
                            actions = actionExecutor,
                            drag = drag,
                            roleStatus = roleStatus,
                            onOpenSettings = onOpenSettings,
                            onClose = { onDrawerOpenChange(false) },
                            modifier = Modifier.drawerEnter(progress, reducedMotion),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.homeDropTarget(
    placement: ShortcutPlacement,
    drag: DrawerDrag,
    closeDrawer: () -> Unit,
): Modifier = shortcutDropTarget(
    state = placement.dragState,
    onLift = closeDrawer,
    onDrop = placement.drop,
    onPressWithoutDrag = { payload ->
        when (payload) {
            is DragPayload.NewShortcut -> drag.menuFor = payload.component
            is DragPayload.MoveItem -> placement.dragState.menuItemId = payload.itemId
        }
    },
)

@Composable
private fun rememberDrawerDrag(
    placement: ShortcutPlacement,
    hiddenApps: HiddenAppRepository,
): DrawerDrag {
    val scope = rememberCoroutineScope()
    return remember(placement, hiddenApps, scope) {
        DrawerDrag(
            state = placement.dragState,
            onAddToHome = placement.placeFirstFree,
            onHide = { component -> scope.launch { hiddenApps.setHidden(component, hidden = true) } },
        )
    }
}

// Both read the progress inside the layer block, so the drawer's travel invalidates drawing and
// never composition — the same contract the page transitions hold, see Modifier.homeRecede.
private fun Modifier.homeExit(progress: () -> Float, reducedMotion: Boolean): Modifier = graphicsLayer {
    val amount = progress()
    alpha = (1f - amount).coerceIn(0f, 1f)
    if (!reducedMotion) {
        val scale = lerp(1f, Tokens.Motion.HOME_EXIT_SCALE, amount)
        scaleX = scale
        scaleY = scale
    }
}

private fun Modifier.drawerEnter(progress: () -> Float, reducedMotion: Boolean): Modifier = graphicsLayer {
    val amount = progress()
    alpha = amount.coerceIn(0f, 1f)
    if (!reducedMotion) {
        translationY = size.height * (1f - amount).coerceAtLeast(0f)
    }
}
