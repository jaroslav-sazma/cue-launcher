package app.cue.launcher.feature.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import app.cue.launcher.R
import app.cue.launcher.designsystem.CueMenu
import app.cue.launcher.designsystem.CueMenuItem
import app.cue.launcher.designsystem.MenuAnchor

@Composable
internal fun TileMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    bounds: IntRect,
    onClose: (() -> Unit)?,
    onReply: (() -> Unit)? = null,
    onConfigureWidget: (() -> Unit)? = null,
) {
    CueMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,

        anchor = MenuAnchor(bounds = bounds),
    ) {
        if (onReply != null) {
            TileMenuItem(R.string.tile_reply, GLYPH_REPLY, onDismiss, onReply)
        }
        if (onConfigureWidget != null) {
            TileMenuItem(R.string.home_widget_configure, GLYPH_CONFIGURE, onDismiss, onConfigureWidget)
        }
        if (onClose != null) {
            TileMenuItem(R.string.tile_close, GLYPH_CLOSE, onDismiss, onClose)
        }
    }
}

@Composable
private fun TileMenuItem(
    @StringRes text: Int,
    glyph: String,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
) {
    CueMenuItem(
        label = stringResource(text),
        glyph = glyph,
        onClick = {
            onDismiss()
            onClick()
        },
    )
}

private const val GLYPH_REPLY = "reply"
private const val GLYPH_CLOSE = "close"
private const val GLYPH_CONFIGURE = "cog-outline"
