package app.cue.launcher.feature.lock

import androidx.compose.runtime.staticCompositionLocalOf

enum class LauncherAction(private val changesLayout: Boolean) {

    ADD_SHORTCUT(changesLayout = true),

    ADD_WIDGET(changesLayout = true),

    NEW_FOLDER(changesLayout = true),

    ADD_TO_FOLDER(changesLayout = true),

    REORDER_FOLDER(changesLayout = true),

    REMOVE_ITEM(changesLayout = true),

    ADD_TO_HOME(changesLayout = true),

    REORDER_PANEL(changesLayout = true),

    ADD_PANEL_WIDGET(changesLayout = true),

    OVERRIDE_ICON(changesLayout = true),

    CONFIGURE_WIDGET(changesLayout = false),

    OPEN_SETTINGS(changesLayout = false),

    OPEN_WALLPAPER(changesLayout = false),

    SET_DEFAULT_HOME(changesLayout = false),
    ;

    fun availableWhen(layoutLocked: Boolean): Boolean = !layoutLocked || !changesLayout
}

val LocalLayoutLocked = staticCompositionLocalOf { false }
