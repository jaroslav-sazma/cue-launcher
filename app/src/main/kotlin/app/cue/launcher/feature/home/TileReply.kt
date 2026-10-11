package app.cue.launcher.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import app.cue.launcher.R
import app.cue.launcher.data.scenario.NotificationReply
import app.cue.launcher.data.scenario.TriggerPayload
import app.cue.launcher.designsystem.GlassLevel
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.TileSurface
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.toTextStyle
import app.cue.launcher.engine.NOTIFICATION_FIELD_TITLE
import app.cue.launcher.engine.NOTIFICATION_VALUE_NAMESPACE
import app.cue.launcher.engine.QueueEntry

@Composable
private fun NotificationReplyDialog(
    reply: NotificationReply,
    title: String,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable(reply.key) { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    LaunchedEffect(reply.key) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },

                label = { Text(text = reply.hint ?: stringResource(R.string.tile_reply_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSend(text) },

                enabled = text.isNotBlank(),
            ) {
                Text(text = stringResource(R.string.tile_reply_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.home_shortcut_add_cancel))
            }
        },
    )
}

@Composable
internal fun TileReplyDialog(
    entry: QueueEntry,
    actions: TileActions,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
) {
    val reply = entry.payload.reply
    if (reply == null || !open) return
    NotificationReplyDialog(
        reply = reply,
        title = replyTitleOf(entry.payload, entry.scenario.name),

        onSend = { text ->
            onOpenChange(false)
            actions.reply(reply, text)
        },
        onDismiss = { onOpenChange(false) },
    )
}

@Composable
fun TileReplyPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalCueColors.current
    val label = stringResource(R.string.tile_reply)
    TileSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(Tokens.Reply.height)

            .clip(RoundedCornerShape(Tokens.Reply.radius))
            .clickable(onClick = onClick)
            .semantics { role = Role.Button },
        level = GlassLevel.THICK,
        radius = Tokens.Reply.radius,
        contentPadding = Tokens.Padding.chip,
        shadow = null,
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(name = REPLY_GLYPH, size = Tokens.Reply.glyph, tint = colors.textPrimary)
            Spacer(modifier = Modifier.width(Tokens.Reply.gapAfterGlyph))
            Text(
                text = label,
                color = colors.textPrimary,
                style = Tokens.Type.tileBody.toTextStyle(),
                maxLines = Tokens.Type.LABEL_LINES,
                overflow = Tokens.Type.labelOverflow,
            )
        }
    }
}

fun replyTitleOf(payload: TriggerPayload, scenarioName: String): String =
    payload["$NOTIFICATION_VALUE_NAMESPACE:$NOTIFICATION_FIELD_TITLE"]?.takeIf(String::isNotBlank)
        ?: scenarioName

private const val REPLY_GLYPH = "reply"
