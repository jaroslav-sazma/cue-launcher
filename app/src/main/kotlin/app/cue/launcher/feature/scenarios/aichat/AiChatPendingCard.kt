package app.cue.launcher.feature.scenarios.aichat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.Tokens

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AiChatPendingCard(onPaste: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.surfacePadding),
        shape = RoundedCornerShape(Tokens.Settings.highlightRadius),
        colors = CardDefaults.cardColors(
            containerColor = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(
                start = Tokens.Settings.surfacePadding,
                bottom = Tokens.Settings.surfacePadding,
            ),
            verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(name = GLYPH_AI_CHAT, size = Tokens.Settings.leadingGlyph, tint = LocalContentColor.current)
                Text(
                    text = stringResource(R.string.ai_chat_pending_title),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Tokens.Settings.highlightPadding),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                    GlyphIcon(
                        name = GLYPH_CLOSE,
                        size = Tokens.Settings.glyph,
                        tint = LocalContentColor.current,
                        contentDescription = stringResource(R.string.ai_chat_pending_dismiss),
                    )
                }
            }
            Text(
                text = stringResource(R.string.ai_chat_pending_text),
                modifier = Modifier.padding(end = Tokens.Settings.surfacePadding),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onPaste,
                modifier = Modifier.padding(top = Tokens.Settings.textSpacing),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(R.string.ai_chat_paste_answer))
            }
        }
    }
}

private const val GLYPH_AI_CHAT = "creation"
private const val GLYPH_CLOSE = "close"
