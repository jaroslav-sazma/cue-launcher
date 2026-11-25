package app.cue.launcher.feature.settings.safe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.cue.launcher.R
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.system.safe.SafeState

// Not rememberTextFieldState: that one saves the text and its undo history into the activity's saved
// state. The password leaves as a char array the caller wipes after the attempt, and the field and its
// undo history are cleared on the way out; the vault checks it off the main thread and, if it is
// wrong, comes back here with rejected set.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SafePasswordDialog(
    state: SafeState.NeedsPassword,
    onSubmit: (CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    val password = remember { TextFieldState() }
    DisposableEffect(password) { onDispose { password.wipe() } }
    var visible by rememberSaveable { mutableStateOf(false) }
    val obfuscation = if (visible) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped
    val submit = {
        val text = password.text
        if (text.isNotEmpty()) {
            onSubmit(CharArray(text.length) { text[it] })
            password.wipe()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.safe_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap)) {
                Text(text = stringResource(state.reason.message))
                OutlinedSecureTextField(
                    state = password,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.safe_password_field)) },
                    trailingIcon = { VisibilityToggle(visible = visible, onToggle = { visible = !visible }) },
                    supportingText = state.rejected.takeIf { it }?.let {
                        { Text(text = stringResource(R.string.safe_password_rejected)) }
                    },
                    isError = state.rejected,
                    textObfuscationMode = obfuscation,
                    keyboardOptions = PASSWORD_KEYBOARD,
                    onKeyboardAction = { submit() },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = password.text.isNotEmpty(), shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(R.string.safe_unlock))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(R.string.safe_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SafeForgetDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.safe_forget_title)) },
        text = { Text(text = stringResource(R.string.safe_forget_detail)) },
        confirmButton = {
            TextButton(onClick = onConfirm, shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(R.string.safe_forget))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(R.string.safe_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VisibilityToggle(visible: Boolean, onToggle: () -> Unit) {
    val description = if (visible) R.string.safe_password_hide else R.string.safe_password_show
    IconButton(onClick = onToggle, shapes = IconButtonDefaults.shapes()) {
        GlyphIcon(
            name = if (visible) GLYPH_HIDE else GLYPH_SHOW,
            size = Tokens.Settings.glyph,
            tint = LocalContentColor.current,
            contentDescription = stringResource(description),
        )
    }
}

private fun TextFieldState.wipe() {
    clearText()
    undoState.clearHistory()
}

private val PASSWORD_KEYBOARD = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)

private const val GLYPH_SHOW = "eye"

private const val GLYPH_HIDE = "eye-off"
