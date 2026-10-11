package app.cue.launcher.feature.home

import android.graphics.Rect
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.layout.LauncherItem
import app.cue.launcher.data.layout.LauncherLayoutRepository
import app.cue.launcher.data.layout.LayoutContainer
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.feature.widgets.WidgetHosting
import app.cue.launcher.system.LauncherAppsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Suppress("LongParameterList")
@Stable
class StaticSlotActions(
    val launch: (ComponentRef, Rect?) -> Unit,
    val remove: (LauncherItem) -> Unit,
    val addShortcut: (LayoutContainer, Int) -> Unit,

    val openSurfaceMenu: ((StaticSlot) -> Unit)? = null,

    val configureWidget: (LauncherItem.AppWidget) -> Unit = {},

    val configureLauncherWidget: (LauncherWidgetType) -> Unit = {},
    val folders: FolderSlotActions,

    val openSearch: () -> Unit = {},

    val openCalendar: () -> Unit = {},

    val openAlarms: () -> Unit = {},

    val openMedia: () -> Unit = {},
)

@Stable
class FolderSlotActions(
    val create: (LayoutContainer, Int) -> Unit,
    val open: (LauncherItem.Folder) -> Unit,
    val rename: (LauncherItem.Folder) -> Unit,

    val moveInto: (LauncherItem.Shortcut) -> Unit,
)

@Composable
fun rememberStaticSlotActions(
    launcherApps: LauncherAppsRepository,
    layoutRepository: LauncherLayoutRepository,
    overlay: HomeOverlayState,
    onOpenSearch: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenAlarms: () -> Unit = {},
    onOpenMedia: () -> Unit = {},

    onOpenSurfaceMenu: ((StaticSlot) -> Unit)? = null,
    widgets: WidgetHosting? = null,
): StaticSlotActions {
    val scope = rememberCoroutineScope()
    val launch = rememberShortcutLaunch(launcherApps)
    val defaultFolderName = stringResource(R.string.folder_default_name)
    var pickerSlot by remember { mutableStateOf<StaticSlot?>(null) }
    var renamed by remember { mutableStateOf<LauncherItem.Folder?>(null) }
    var moved by remember { mutableStateOf<LauncherItem.Shortcut?>(null) }

    val actions = remember(
        launcherApps,
        layoutRepository,
        launch,
        scope,
        overlay,
        defaultFolderName,
        onOpenSearch,
        onOpenCalendar,
        onOpenAlarms,
        onOpenMedia,
        onOpenSurfaceMenu,
        widgets,
    ) {
        StaticSlotActions(
            launch = launch,
            remove = { item -> removeItem(item, widgets, layoutRepository, scope) },
            configureWidget = { widget -> widgets?.configure(widget.appWidgetId) },
            configureLauncherWidget = { widget ->
                overlay.open(HomeOverlayTarget.WidgetSettings(widget))
            },
            addShortcut = { container, position ->
                pickerSlot = StaticSlot(container, position, item = null)
            },
            openSurfaceMenu = onOpenSurfaceMenu,
            openSearch = onOpenSearch,
            openCalendar = onOpenCalendar,
            openAlarms = onOpenAlarms,
            openMedia = onOpenMedia,
            folders = overlay.slotActions(
                layoutRepository = layoutRepository,
                defaultName = defaultFolderName,
                scope = scope,
                onRename = { folder -> renamed = folder },
                onMoveInto = { shortcut -> moved = shortcut },
            ),
        )
    }

    StaticSlotDialogs(
        pickerSlot = pickerSlot,
        renamed = renamed,
        moved = moved,
        launcherApps = launcherApps,
        layoutRepository = layoutRepository,
        onClose = {
            pickerSlot = null
            renamed = null
            moved = null
        },
    )
    return actions
}

private fun HomeOverlayState.slotActions(
    layoutRepository: LauncherLayoutRepository,
    defaultName: String,
    scope: CoroutineScope,
    onRename: (LauncherItem.Folder) -> Unit,
    onMoveInto: (LauncherItem.Shortcut) -> Unit,
): FolderSlotActions = FolderSlotActions(
    create = { container, position ->
        scope.launch { layoutRepository.createFolder(container, position, defaultName) }
    },
    open = { folder -> this@slotActions.open(HomeOverlayTarget.Folder(folder.id)) },
    rename = onRename,
    moveInto = onMoveInto,
)

@Composable
private fun StaticSlotDialogs(
    pickerSlot: StaticSlot?,
    renamed: LauncherItem.Folder?,
    moved: LauncherItem.Shortcut?,
    launcherApps: LauncherAppsRepository,
    layoutRepository: LauncherLayoutRepository,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    if (pickerSlot != null) {
        ShortcutPickerDialog(
            launcherApps = launcherApps,
            onPick = { component ->
                onClose()
                scope.launch {
                    layoutRepository.placeShortcut(pickerSlot.container, pickerSlot.position, component)
                }
            },
            onDismiss = onClose,
        )
    }
    if (renamed != null) {
        FolderRenameDialog(
            folder = renamed,
            onRename = { name ->
                onClose()
                scope.launch { layoutRepository.renameFolder(renamed, name) }
            },
            onDismiss = onClose,
        )
    }
    if (moved != null) {
        FolderPickerDialog(
            layoutRepository = layoutRepository,
            onPick = { folder ->
                onClose()
                scope.launch {
                    layoutRepository.addToFolder(folder.id, moved.component)
                    layoutRepository.removeItem(moved)
                }
            },
            onDismiss = onClose,
        )
    }
}

@Composable
fun rememberShortcutLaunch(launcherApps: LauncherAppsRepository): (ComponentRef, Rect?) -> Unit {
    val context = LocalContext.current
    return remember(launcherApps, context) {
        { component, bounds ->
            if (!launcherApps.launch(component, bounds)) {
                Toast.makeText(context, R.string.home_launch_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun removeItem(
    item: LauncherItem,
    widgets: WidgetHosting?,
    layoutRepository: LauncherLayoutRepository,
    scope: CoroutineScope,
) {
    if (item is LauncherItem.AppWidget) widgets?.unbind(item.appWidgetId)
    scope.launch { layoutRepository.removeItem(item) }
}
