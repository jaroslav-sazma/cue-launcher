package app.cue.launcher.feature.home

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.cue.launcher.data.layout.LauncherLayoutRepository
import app.cue.launcher.data.layout.LayoutContainer
import app.cue.launcher.data.layout.StaticLayout
import app.cue.launcher.data.layout.firstFreeArea
import app.cue.launcher.data.settings.LauncherSettings
import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.engine.BentoLayout
import app.cue.launcher.engine.BentoQueues
import app.cue.launcher.engine.ScenarioEngine
import app.cue.launcher.engine.bentoLayoutOf
import app.cue.launcher.feature.dragdrop.ShortcutDragState
import app.cue.launcher.feature.dragdrop.SlotKey
import app.cue.launcher.feature.dragdrop.firstFreeSlot
import app.cue.launcher.feature.drawer.drawerSwipeUp
import app.cue.launcher.feature.widgets.WidgetHosting
import app.cue.launcher.system.HomeRoleStatus
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.system.homeRoleOffered
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    roleStatus: HomeRoleStatus,
    launcherApps: LauncherAppsRepository,
    layoutRepository: LauncherLayoutRepository,
    settingsRepository: SettingsRepository,
    engine: ScenarioEngine,
    tileActions: TileActions,
    widgets: WidgetHosting,
    dragState: ShortcutDragState,
    overlay: HomeOverlayState,
    onRequestHomeRole: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWallpaper: () -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    pageIndicator: @Composable () -> Unit = {},
) {

    val storedLayout by remember(layoutRepository) { layoutRepository.observeLayout() }
        .collectAsStateWithLifecycle(initialValue = null as StaticLayout?)
    ReportDrawnWhen { storedLayout != null }
    val layout = storedLayout ?: StaticLayout.EMPTY
    val queues by remember(engine) { engine.queues }.collectAsStateWithLifecycle(initialValue = BentoQueues.EMPTY)
    val bento = remember(queues) { bentoLayoutOf(queues) }
    val menu = rememberHomeMenuState()
    val actions = rememberStaticSlotActions(
        launcherApps = launcherApps,
        layoutRepository = layoutRepository,
        overlay = overlay,
        onOpenSearch = tileActions.openSearch,

        onOpenCalendar = { tileActions.openCalendar(null) },
        onOpenAlarms = { tileActions.openAlarms(null) },
        onOpenMedia = { tileActions.openMedia(null) },

        onOpenSurfaceMenu = remember(menu) { menu::openAt },
        widgets = widgets,
    )
    val stackHint = rememberStackHint(settingsRepository)
    val bands = LocalHomeBands.current

    Box(modifier = modifier.fillMaxSize().homeMenuLongPress(menu, onOpenDrawer)) {
        HomeZones(
            bands = bands,
            bento = bento,
            layout = layout,
            launcherApps = launcherApps,
            tileActions = tileActions,
            widgets = widgets,
            dragState = dragState,
            overlay = overlay,
            actions = actions,
            stackHint = stackHint,
            onOpenDrawer = onOpenDrawer,
            pageIndicator = pageIndicator,
        )
        HomeSurfaceMenu(
            state = menu,
            layout = layout,
            layoutRepository = layoutRepository,
            widgets = widgets,
            roleStatus = roleStatus,
            actions = actions,
            onOpenSettings = onOpenSettings,
            onOpenWallpaper = onOpenWallpaper,
            onRequestHomeRole = onRequestHomeRole,
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun HomeZones(
    bands: HomeBands,
    bento: BentoLayout,
    layout: StaticLayout,
    launcherApps: LauncherAppsRepository,
    tileActions: TileActions,
    widgets: WidgetHosting,
    dragState: ShortcutDragState,
    overlay: HomeOverlayState,
    actions: StaticSlotActions,
    stackHint: StackHint,
    onOpenDrawer: () -> Unit,
    pageIndicator: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .drawerSwipeUp(onOpenDrawer)
            .shadeSwipeDown(rememberShadePull())
            .safeDrawingPadding(),
    ) {
        BentoZone(
            layout = bento,
            widgets = widgets,
            launcherApps = launcherApps,
            actions = tileActions,
            overlay = overlay,
            bands = bands,
            stackHint = stackHint,
        )
        Spacer(modifier = Modifier.height(bands.bentoGap))
        StaticZone(
            layout = layout,
            launcherApps = launcherApps,
            dragState = dragState,
            widgets = widgets,
            actions = actions,
            rowGap = bands.rowGap,
            pageIndicator = pageIndicator,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HomeSurfaceMenu(
    state: HomeMenuState,
    layout: StaticLayout,
    layoutRepository: LauncherLayoutRepository,
    widgets: WidgetHosting,
    roleStatus: HomeRoleStatus,
    actions: StaticSlotActions,
    onOpenSettings: () -> Unit,
    onOpenWallpaper: () -> Unit,
    onRequestHomeRole: () -> Unit,
) {
    val freeSlot = remember(layout) { firstFreeSlot(layout) }

    val freeGridCell = remember(layout) { layout.firstFreeArea(LayoutContainer.GRID, 1, 1) }
    val picker = rememberHomeWidgetPicker(widgets, layoutRepository, layout)
    var pickerOpen by rememberSaveable { mutableStateOf(false) }

    HomeMenu(
        state = state,
        hasFreeSlot = freeSlot != null,
        hasFreeGridCell = freeGridCell != null,
        offersRole = homeRoleOffered(roleStatus),
        onSelect = { item ->

            val target = state.targetSlot?.let { SlotKey(it.container, it.position) } ?: freeSlot
            when (item) {
                HomeMenuItem.ADD_APP -> target?.let { actions.addShortcut(it.container, it.position) }
                HomeMenuItem.ADD_WIDGET -> pickerOpen = true
                HomeMenuItem.NEW_FOLDER -> target?.let { actions.folders.create(it.container, it.position) }
                HomeMenuItem.SETTINGS -> onOpenSettings()
                HomeMenuItem.WALLPAPER -> onOpenWallpaper()
                HomeMenuItem.SET_DEFAULT -> onRequestHomeRole()
            }
        },
    )
    if (pickerOpen) {
        HomeWidgetPickerSheet(widgets = widgets, picker = picker, onClose = { pickerOpen = false })
    }
}

@Composable
private fun rememberStackHint(settingsRepository: SettingsRepository): StackHint {
    val settings by remember(settingsRepository) { settingsRepository.settings }
        .collectAsStateWithLifecycle(initialValue = LauncherSettings())
    val scope = rememberCoroutineScope()
    val seen = settings.stackHintSeen
    return remember(seen, settingsRepository, scope) {

        StackHint(visible = !seen) {
            if (!seen) scope.launch { settingsRepository.setStackHintSeen(true) }
        }
    }
}
