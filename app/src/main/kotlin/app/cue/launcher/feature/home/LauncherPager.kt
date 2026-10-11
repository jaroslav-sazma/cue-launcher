package app.cue.launcher.feature.home

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.layout.LauncherLayoutRepository
import app.cue.launcher.data.settings.LauncherSettings
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.designsystem.DepthPlane
import app.cue.launcher.designsystem.LocalDepthPlane
import app.cue.launcher.designsystem.PageActionCue
import app.cue.launcher.designsystem.PageIndicator
import app.cue.launcher.designsystem.homeRecede
import app.cue.launcher.designsystem.homeRecedeDepth
import app.cue.launcher.designsystem.pageIndicatorPinned
import app.cue.launcher.designsystem.panelEmerge
import app.cue.launcher.designsystem.panelEmergeDepth
import app.cue.launcher.designsystem.rememberPageIndicatorAlpha
import app.cue.launcher.designsystem.overlayBackdrop
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.WallpaperParallaxEffect
import app.cue.launcher.designsystem.backPreviewTravel
import app.cue.launcher.designsystem.rememberGlassOverscroll
import app.cue.launcher.designsystem.rememberReducedMotion
import app.cue.launcher.designsystem.spatialDefaultSpec
import app.cue.launcher.engine.ScenarioEngine
import app.cue.launcher.feature.dragdrop.ShortcutDragState
import app.cue.launcher.feature.panel.WidgetPanel
import app.cue.launcher.feature.widgets.WidgetHosting
import app.cue.launcher.system.ActionExecutor
import app.cue.launcher.system.HomeRoleStatus
import app.cue.launcher.system.LauncherAppsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun LauncherPager(
    roleStatus: HomeRoleStatus,
    launcherApps: LauncherAppsRepository,
    layoutRepository: LauncherLayoutRepository,
    settingsRepository: SettingsRepository,
    engine: ScenarioEngine,
    actionExecutor: ActionExecutor,
    widgets: WidgetHosting,
    widgetSettings: WidgetSettingsAccess,
    dragState: ShortcutDragState,
    onRequestHomeRole: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDrawer: () -> Unit,

    modifier: Modifier = Modifier,
    homeResetSignal: Int = 0,
) {
    val pageCount = LauncherPage.entries.size
    val pageLabels = LauncherPage.entries.map { page -> stringResource(page.labelRes) }
    val state = rememberPagerState(initialPage = LauncherPage.HOME.index) { pageCount }
    val scope = rememberCoroutineScope()
    val goToPage = { page: Int -> scope.launch { state.animateScrollToPage(page) } }
    val overlay = remember { HomeOverlayState() }
    val overlayProgress = rememberOverlayProgress(overlay)

    val settings by remember(settingsRepository) { settingsRepository.settings }
        .collectAsStateWithLifecycle(initialValue = LauncherSettings())
    val tileActions = rememberTileActions(
        executor = actionExecutor,
        engine = engine,
        gestures = settings.gestures,
    )

    PagerEffects(
        state = state,
        dragState = dragState,
        overlay = overlay,
        homeResetSignal = homeResetSignal,
        onGoHome = { goToPage(LauncherPage.HOME.index) },
    )
    val swipe = rememberSwipeGesture(settings.gestures.swipeLeft, tileActions, state)

    CompositionLocalProvider(LocalHomeBands provides rememberHomeBands()) {
        Box(modifier = modifier.fillMaxSize().homeActionSwipe(swipe.onHome, swipe.run)) {
            LauncherPages(
                state = state,
                overlay = overlay,
                modifier = Modifier.overlayBackdrop(overlayProgress),
                pageCount = pageCount,
                pageLabels = pageLabels,
                onSelectPage = { index -> goToPage(index) },
                roleStatus = roleStatus,
                launcherApps = launcherApps,
                layoutRepository = layoutRepository,
                settingsRepository = settingsRepository,
                engine = engine,
                tileActions = tileActions,
                widgets = widgets,
                dragState = dragState,
                onRequestHomeRole = onRequestHomeRole,
                onOpenSettings = onOpenSettings,
                onOpenWallpaper = rememberWallpaperPicker(actionExecutor),
                onOpenDrawer = onOpenDrawer,
                actionCue = swipe.cue,
                indicatorWake = homeResetSignal to rememberForegroundTick(),
            )
            HomeOverlayHost(
                state = overlay,
                layoutRepository = layoutRepository,
                launcherApps = launcherApps,
                tileActions = tileActions,
                widgetSettings = widgetSettings,
                progress = overlayProgress,
            )
        }
    }
}

@Composable
private fun PagerEffects(
    state: PagerState,
    dragState: ShortcutDragState,
    overlay: HomeOverlayState,
    homeResetSignal: Int,
    onGoHome: () -> Unit,
) {
    DragPageSwitchEffect(state, dragState)
    WallpaperParallaxEffect(progress = state::panelProgress)

    LaunchedEffect(homeResetSignal) {
        if (homeResetSignal > 0) {
            overlay.close()
            state.animateScrollToPage(LauncherPage.HOME.index)
        }
    }

    PanelBackEffect(state, onGoHome)
}

@Composable
private fun PanelBackEffect(state: PagerState, onGoHome: () -> Unit) {
    val goHome by rememberUpdatedState(onGoHome)

    val scope = rememberCoroutineScope()
    PredictiveBackHandler(enabled = state.currentPage != LauncherPage.HOME.index) { events ->
        try {
            events.collect { event ->
                val travel = backPreviewTravel(event.progress) * PANEL_BACK_PREVIEW
                // Home is the page after the panel, so the preview drags the offset forwards.
                state.scrollToPage(LauncherPage.PANEL.index, travel)
            }
            goHome()
        } catch (cancellation: CancellationException) {
            scope.launch { state.animateScrollToPage(LauncherPage.PANEL.index) }
            throw cancellation
        }
    }
}

private const val PANEL_BACK_PREVIEW = 0.2f

@Stable
private class SwipeGesture(
    val cue: PageActionCue?,
    val run: () -> Unit,
    val onHome: () -> Boolean,
)

@Composable
private fun rememberSwipeGesture(
    action: TapAction?,
    tileActions: TileActions,
    state: PagerState,
): SwipeGesture {

    val label = stringResource(R.string.page_action_cue)
    return remember(action, label, tileActions, state) {
        val run = { action?.let { tileActions.perform(listOf(it), null) } ?: Unit }
        val cue = action?.let { PageActionCue(label = label, onRun = run) }
        SwipeGesture(
            cue = cue,
            run = run,
            onHome = { cue != null && state.currentPage == LauncherPage.HOME.index },
        )
    }
}

@Composable
private fun LauncherPages(
    state: PagerState,
    overlay: HomeOverlayState,
    pageCount: Int,
    pageLabels: List<String>,
    onSelectPage: (Int) -> Unit,
    roleStatus: HomeRoleStatus,
    launcherApps: LauncherAppsRepository,
    layoutRepository: LauncherLayoutRepository,
    settingsRepository: SettingsRepository,
    engine: ScenarioEngine,
    tileActions: TileActions,
    widgets: WidgetHosting,
    dragState: ShortcutDragState,
    onRequestHomeRole: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWallpaper: () -> Unit,
    onOpenDrawer: () -> Unit,
    actionCue: PageActionCue?,
    indicatorWake: Any,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val depth = rememberDepthPlanes(state)
    HorizontalPager(
        state = state,
        modifier = modifier.fillMaxSize().pageSwipeEdgeGuard(),

        // The panel hosts AppWidgetHostViews, and building one is a binder call plus a RemoteViews
        // inflation on the main thread. Composed on demand, that bill arrives in the first frames
        // of the very swipe the panel is being uncovered by, once per widget.
        beyondViewportPageCount = 1,
        flingBehavior = PagerDefaults.flingBehavior(
            state = state,
            snapAnimationSpec = spatialDefaultSpec(reducedMotion),
            snapPositionalThreshold = Tokens.Motion.PAGE_SNAP_THRESHOLD,
        ),
        overscrollEffect = rememberGlassOverscroll(Orientation.Horizontal),
    ) { page ->
        when (LauncherPage.entries[page]) {
            LauncherPage.HOME -> CompositionLocalProvider(LocalDepthPlane provides depth.home) {
                HomeScreen(
                    modifier = Modifier.homeRecede(reducedMotion, state::panelProgress),
                    roleStatus = roleStatus,
                    launcherApps = launcherApps,
                    layoutRepository = layoutRepository,
                    settingsRepository = settingsRepository,
                    engine = engine,
                    tileActions = tileActions,
                    widgets = widgets,
                    dragState = dragState,
                    overlay = overlay,
                    onRequestHomeRole = onRequestHomeRole,
                    onOpenSettings = onOpenSettings,
                    onOpenWallpaper = onOpenWallpaper,
                    onOpenDrawer = onOpenDrawer,
                    pageIndicator = {
                        PagerIndicator(
                            state = state,
                            pageCount = pageCount,
                            pageLabels = pageLabels,
                            onSelectPage = onSelectPage,
                            actionCue = actionCue,
                            indicatorWake = indicatorWake,
                        )
                    },
                )
            }

            LauncherPage.PANEL -> PanelPage(state, depth.panel) {
                WidgetPanel(
                    modifier = Modifier.panelEmerge(reducedMotion, state::panelProgress),
                    widgets = widgets,
                    launcherApps = launcherApps,
                    layoutRepository = layoutRepository,
                    dragState = dragState,
                    overlay = overlay,
                    tileActions = tileActions,
                    progress = state::panelProgress,
                )
            }
        }
    }
}

@Composable
private fun PagerIndicator(
    state: PagerState,
    pageCount: Int,
    pageLabels: List<String>,
    onSelectPage: (Int) -> Unit,
    actionCue: PageActionCue?,
    indicatorWake: Any,
) {
    val visibility = rememberPageIndicatorAlpha(
        pinned = {
            pageIndicatorPinned(
                scrollInProgress = state.isScrollInProgress,
                pageOffsetFraction = state.currentPageOffsetFraction,
            )
        },
        wake = indicatorWake,
    )
    PageIndicator(
        pageCount = pageCount,
        position = { state.currentPage + state.currentPageOffsetFraction },
        pageLabel = { index -> pageLabels[index] },
        onSelectPage = onSelectPage,

        actionCue = actionCue,
        alpha = visibility.alpha,
        hidden = visibility.hidden,
    )
}

// The pager keeps the panel composed behind the home page, and everything on it collects with the
// lifecycle, so capping it below STARTED stops its feeds while it is hidden. It wakes on its first
// uncovered pixel, and after every return to the home screen it is let read once, so a swipe opens
// on current content rather than on what it held when it went to sleep.
@Composable
private fun PanelPage(state: PagerState, depth: DepthPlane, content: @Composable () -> Unit) {
    val inView by remember(state) { derivedStateOf { panelInView(state.panelProgress()) } }
    var warming by remember { mutableStateOf(false) }
    val screen = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(screen) {
        screen.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                warming = true
                delay(PANEL_WARM_UP_MS)
            } finally {
                warming = false
            }
        }
    }
    val awake = inView || warming
    val owner = rememberLifecycleOwner(maxLifecycle = if (awake) Lifecycle.State.RESUMED else Lifecycle.State.CREATED)
    CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalDepthPlane provides depth, content = content)
}

private const val PANEL_WARM_UP_MS = 2_000L

@Stable
private class DepthPlanes(val home: DepthPlane, val panel: DepthPlane)

// The planes mirror the graphics layers on the pages themselves: a wallpaper blur region is placed
// from layout coordinates and would otherwise stay put while the glass above it recedes.
@Composable
private fun rememberDepthPlanes(state: PagerState): DepthPlanes = remember(state) {
    DepthPlanes(
        home = DepthPlane { homeRecedeDepth(state.panelProgress()) },
        panel = DepthPlane { panelEmergeDepth(state.panelProgress()) },
    )
}

@Composable
private fun DragPageSwitchEffect(state: PagerState, dragState: ShortcutDragState) {
    val widthPx = LocalWindowInfo.current.containerSize.width.toFloat()
    val edgeZonePx = with(LocalDensity.current) { Tokens.Drag.pageEdgeZone.toPx() }
    LaunchedEffect(state, dragState, widthPx, edgeZonePx) {
        snapshotFlow {
            val pointer = dragState.pointer
            if (pointer == null || dragState.target != null) {
                0
            } else {
                PageGesture.dragPageStep(pointer.x, widthPx, edgeZonePx)
            }
        }.collectLatest { step ->
            val page = state.currentPage + step
            if (step == 0 || page !in 0 until state.pageCount) return@collectLatest
            delay(Tokens.Drag.PAGE_SWITCH_DELAY_MS)
            state.animateScrollToPage(page)
        }
    }
}

private fun PagerState.panelProgress(): Float = panelProgress(
    page = currentPage,
    pageOffsetFraction = currentPageOffsetFraction,
    pageCount = pageCount,
)


