package app.cue.launcher.feature.home

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.round
import app.cue.launcher.R
import app.cue.launcher.designsystem.CueMenu
import app.cue.launcher.designsystem.CueMenuItem
import app.cue.launcher.designsystem.GLYPH_CHEVRON
import app.cue.launcher.designsystem.MenuAnchor
import app.cue.launcher.designsystem.MenuDecoration
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.lock.LauncherAction
import app.cue.launcher.feature.lock.LocalLayoutLocked
import app.cue.launcher.system.ActionExecutor
import app.cue.launcher.system.ActionOutcome

enum class HomeMenuItem(
    val action: LauncherAction,
    @param:StringRes val label: Int,
    val glyph: String,
    val opensScreen: Boolean,
) {
    ADD_APP(LauncherAction.ADD_SHORTCUT, R.string.home_menu_add_app, "apps", opensScreen = false),
    ADD_WIDGET(LauncherAction.ADD_WIDGET, R.string.home_menu_add_widget, "widgets", opensScreen = false),
    NEW_FOLDER(LauncherAction.NEW_FOLDER, R.string.folder_new, "folder-plus-outline", opensScreen = false),
    SETTINGS(LauncherAction.OPEN_SETTINGS, R.string.home_menu_settings, "cog-outline", opensScreen = true),
    WALLPAPER(LauncherAction.OPEN_WALLPAPER, R.string.home_menu_wallpaper, "wallpaper", opensScreen = true),
    SET_DEFAULT(
        LauncherAction.SET_DEFAULT_HOME,
        R.string.home_role_request,
        "home-switch-outline",
        opensScreen = true,
    ),
}

fun homeMenuItems(offersRole: Boolean, layoutLocked: Boolean = false): List<HomeMenuItem> =
    HomeMenuItem.entries.filter {
        (it != HomeMenuItem.SET_DEFAULT || offersRole) && it.action.availableWhen(layoutLocked)
    }

fun homeMenuEnabled(
    item: HomeMenuItem,
    hasFreeSlot: Boolean,
    hasFreeGridCell: Boolean = hasFreeSlot,
    onEmptySlot: Boolean = false,
): Boolean = when (item) {
    HomeMenuItem.ADD_APP, HomeMenuItem.NEW_FOLDER -> onEmptySlot || hasFreeSlot
    HomeMenuItem.ADD_WIDGET -> hasFreeGridCell
    HomeMenuItem.SETTINGS, HomeMenuItem.WALLPAPER, HomeMenuItem.SET_DEFAULT -> true
}

@Stable
class HomeMenuState {
    var expanded by mutableStateOf(false)

    var pressPosition by mutableStateOf(Offset.Zero)

    var rootOrigin by mutableStateOf(Offset.Zero)

    val pressInWindow: IntOffset get() = (rootOrigin + pressPosition).round()

    var targetSlot by mutableStateOf<StaticSlot?>(null)

    fun openAt(slot: StaticSlot) {
        targetSlot = slot
        expanded = true
    }
}

@Composable
fun rememberHomeMenuState(): HomeMenuState = remember { HomeMenuState() }

@Composable
fun Modifier.homeMenuLongPress(state: HomeMenuState, onOpenDrawer: () -> Unit): Modifier {
    val menuLabel = stringResource(R.string.home_menu)
    val surfaceLabel = stringResource(R.string.home_surface)
    val drawerLabel = stringResource(R.string.home_drawer_action)
    return this
        .onGloballyPositioned { coordinates ->
            state.rootOrigin = coordinates.boundsInWindow().topLeft
        }
        .pointerInput(state) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                state.pressPosition = down.position

                state.targetSlot = null

                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
            }
        }
        .combinedClickable(
            interactionSource = null,
            indication = null,
            onLongClickLabel = menuLabel,
            onLongClick = { state.expanded = true },

            onClick = {},
        )
        .semantics {
            contentDescription = surfaceLabel
            customActions = listOf(CustomAccessibilityAction(drawerLabel) { onOpenDrawer(); true })
        }
}

@Composable
fun HomeMenu(
    state: HomeMenuState,
    hasFreeSlot: Boolean,
    hasFreeGridCell: Boolean,
    offersRole: Boolean,
    onSelect: (HomeMenuItem) -> Unit,
) {
    val press = state.pressInWindow

    val radius = with(LocalDensity.current) { (Tokens.Menu.pressRipple / 2).roundToPx() }
    CueMenu(
        expanded = state.expanded,
        onDismissRequest = { state.expanded = false },
        anchor = MenuAnchor(
            bounds = IntRect(
                left = press.x - radius,
                top = press.y - radius,
                right = press.x + radius,
                bottom = press.y + radius,
            ),
            press = press,
            decoration = MenuDecoration.Press,
        ),
    ) {
        val onEmptySlot = state.targetSlot != null
        homeMenuItems(offersRole, LocalLayoutLocked.current).forEach { item ->
            CueMenuItem(
                label = stringResource(item.label),
                glyph = item.glyph,
                trailingGlyph = GLYPH_CHEVRON.takeIf { item.opensScreen },
                enabled = homeMenuEnabled(item, hasFreeSlot, hasFreeGridCell, onEmptySlot),
                onClick = {
                    state.expanded = false
                    onSelect(item)
                },
            )
        }
    }
}

@Composable
fun rememberWallpaperPicker(executor: ActionExecutor): () -> Unit {
    val context = LocalContext.current
    return remember(executor, context) {
        {
            if (executor.openWallpaper() != ActionOutcome.DONE) {
                Toast.makeText(context, R.string.action_target_missing, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
