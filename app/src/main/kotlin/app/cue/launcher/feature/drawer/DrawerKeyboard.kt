package app.cue.launcher.feature.drawer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.SoftwareKeyboardController

@Composable
internal fun DrawerKeyboard(
    focusRequester: FocusRequester,
    keyboard: SoftwareKeyboardController?,
    drag: DrawerDrag,
) {
    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    DisposableEffect(keyboard, drag) {
        onDispose {
            keyboard?.hide()

            drag.menuFor = null
        }
    }
}
