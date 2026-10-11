package app.cue.launcher.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.layout.LauncherItem
import app.cue.launcher.data.layout.LauncherLayoutRepository
import app.cue.launcher.data.layout.StaticLayout
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.designsystem.LocalMenuBackdrop
import app.cue.launcher.designsystem.MenuBackdropState
import app.cue.launcher.designsystem.OverlayScaffold
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.rememberReducedMotion
import app.cue.launcher.designsystem.spatialSlowSpec
import app.cue.launcher.engine.QueueEntry
import app.cue.launcher.system.LauncherAppsRepository
import kotlinx.coroutines.launch

@Stable
class HomeOverlayState {

    var target: HomeOverlayTarget? by mutableStateOf(null)
        private set

    private var last: HomeOverlayTarget? by mutableStateOf(null)

    fun open(target: HomeOverlayTarget) {
        this.target = target
        last = target
    }

    fun close() {
        target = null
    }

    // `leaving` is the overlay still on its way out: the target is already gone, but the last one
    // has to stay composed until its animation has finished playing it off the screen.
    fun visibleAt(leaving: Boolean): HomeOverlayTarget? = target ?: last.takeIf { leaving }
}

sealed interface HomeOverlayTarget {

    data class Folder(val folderId: Long) : HomeOverlayTarget

    data class DynamicFolder(val pages: List<QueueEntry>) : HomeOverlayTarget

    data class WidgetSettings(val widget: LauncherWidgetType) : HomeOverlayTarget
}

@Composable
fun HomeOverlayHost(
    state: HomeOverlayState,
    layoutRepository: LauncherLayoutRepository,
    launcherApps: LauncherAppsRepository,
    tileActions: TileActions,
    widgetSettings: WidgetSettingsAccess,
    progress: () -> Float,
) {
    val modifier = Modifier
        .safeDrawingPadding()
        .padding(horizontal = Tokens.Grid.ZoneBleed.start)

    val current = rememberUpdatedState(progress)
    val leaving by remember { derivedStateOf { current.value() > 0f } }

    // The open overlay is not brought back after a recreation, so what its content would save (an
    // open sheet, a pager page) would wait for, and turn up in, the next overlay opened here.
    CompositionLocalProvider(
        LocalMenuBackdrop provides MenuBackdropState.NONE,
        LocalSaveableStateRegistry provides null,
    ) {
        when (val target = state.visibleAt(leaving)) {
            null -> Unit

            is HomeOverlayTarget.Folder -> StaticFolderOverlay(
                folderId = target.folderId,
                state = state,
                layoutRepository = layoutRepository,
                launcherApps = launcherApps,
                progress = progress,
                modifier = modifier,
            )

            is HomeOverlayTarget.DynamicFolder -> DynamicFolderOverlay(
                pages = target.pages,
                state = state,
                launcherApps = launcherApps,
                tileActions = tileActions,
                progress = progress,
                modifier = modifier,
            )

            is HomeOverlayTarget.WidgetSettings -> WidgetSettingsOverlay(
                widget = target.widget,
                state = state,
                access = widgetSettings,
                progress = progress,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun StaticFolderOverlay(
    folderId: Long,
    state: HomeOverlayState,
    layoutRepository: LauncherLayoutRepository,
    launcherApps: LauncherAppsRepository,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val launch = rememberShortcutLaunch(launcherApps)
    var pickerOpen by remember(folderId) { mutableStateOf(false) }
    val layout by remember(layoutRepository) { layoutRepository.observeLayout() }
        .collectAsStateWithLifecycle(initialValue = StaticLayout.EMPTY)
    val folder = remember(layout, folderId) { folderById(layout, folderId) }

    LaunchedEffect(folder, layout) {
        if (folder == null && layout !== StaticLayout.EMPTY) state.close()
    }
    if (folder == null) return

    OverlayScaffold(
        onDismiss = state::close,
        modifier = modifier,
        title = folder.name,
        progress = progress,
        dismissLabel = stringResource(R.string.folder_close),
    ) {
        StaticFolderContent(
            folder = folder,
            launcherApps = launcherApps,
            onLaunch = launch,
            onRemove = { component ->
                scope.launch { layoutRepository.removeFromFolder(folder.id, component) }
            },
            onMove = { from, to ->
                scope.launch { layoutRepository.moveInFolder(folder.id, from, to) }
            },
            onAdd = { pickerOpen = true },
        )
    }
    if (pickerOpen) {
        ShortcutPickerDialog(
            launcherApps = launcherApps,
            onPick = { component ->
                pickerOpen = false
                scope.launch { layoutRepository.addToFolder(folder.id, component) }
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun DynamicFolderOverlay(
    pages: List<QueueEntry>,
    state: HomeOverlayState,
    launcherApps: LauncherAppsRepository,
    tileActions: TileActions,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val closeAfterAction = remember(tileActions, state) {
        TileActions(
            perform = { actions, bounds ->
                tileActions.perform(actions, bounds)
                state.close()
            },

            close = { scenarioId ->
                tileActions.close(scenarioId)
                state.close()
            },
        )
    }
    OverlayScaffold(
        onDismiss = state::close,
        modifier = modifier,
        title = stringResource(R.string.folder_dynamic_title),
        progress = progress,
        dismissLabel = stringResource(R.string.folder_close),
    ) {
        DynamicFolderContent(
            pages = pages,
            launcherApps = launcherApps,
            actions = closeAfterAction,
        )
    }
}

@Composable
private fun WidgetSettingsOverlay(
    widget: LauncherWidgetType,
    state: HomeOverlayState,
    access: WidgetSettingsAccess,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    var calendars by remember(widget) { mutableStateOf(false) }
    var players by remember(widget) { mutableStateOf(false) }
    OverlayScaffold(
        onDismiss = {
            when {
                calendars -> calendars = false
                players -> players = false
                else -> state.close()
            }
        },
        modifier = modifier,
        title = when {
            calendars -> stringResource(R.string.widget_settings_calendars)
            players -> stringResource(R.string.widget_settings_media_player)
            else -> stringResource(launcherWidgetLabelRes(widget))
        },
        progress = progress,
        dismissLabel = stringResource(R.string.widget_settings_close),
    ) {
        when {
            calendars -> CalendarSourcePicker(access = access)
            players -> MediaPlayerPicker(access = access, onPicked = { players = false })
            else -> LauncherWidgetSettings(
                widget = widget,
                access = access,
                onOpenCalendars = { calendars = true },
                onOpenPlayers = { players = true },
            )
        }
    }
}

// A function and not a value: an overlay opens over the whole launcher, and a progress read in
// composition would recompose both pages of the pager once per frame of a deliberately slow
// animation. See HomeTransitions.drawerProgress, which holds the same contract for the drawer.
@Composable
fun rememberOverlayProgress(state: HomeOverlayState): () -> Float {
    val progress = animateFloatAsState(
        targetValue = if (state.target == null) 0f else 1f,
        animationSpec = spatialSlowSpec(rememberReducedMotion()),
        label = "overlay.expand",
    )
    return remember(progress) { progress::value }
}

private fun folderById(layout: StaticLayout, folderId: Long): LauncherItem.Folder? =
    layout.allItems().filterIsInstance<LauncherItem.Folder>().firstOrNull { it.id == folderId }
