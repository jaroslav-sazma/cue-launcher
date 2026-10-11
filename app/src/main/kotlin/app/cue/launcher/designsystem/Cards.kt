package app.cue.launcher.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun InfoCard(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.surfacePadding),
        shape = RoundedCornerShape(Tokens.Settings.highlightRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(Tokens.Settings.surfacePadding),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.surfacePadding),
        ) {
            GlyphIcon(
                name = GLYPH_INFO,
                size = Tokens.Settings.leadingGlyph,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// Same selection language as a highlighted row, but for a card-sized choice.
@Composable
fun ChoiceCard(
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onSelect,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.surfacePadding),
        enabled = enabled,
        shape = RoundedCornerShape(Tokens.Settings.highlightRadius),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) scheme.secondaryContainer else Color.Transparent,
            contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = scheme.onSurface,
        ),
        border = BorderStroke(
            width = if (selected) Tokens.Builder.selectedBorder else Tokens.Builder.cardBorder,
            color = if (selected) scheme.primary else scheme.outlineVariant,
        ),
    ) {
        RowDetailPlate(
            container = if (selected && enabled) scheme.secondaryContainer else Color.Transparent,
            ink = scheme.onSecondaryContainer,
            error = false,
        ) {
            Box(modifier = Modifier.padding(Tokens.Settings.surfacePadding)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.surfacePadding),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }
    }
}

private const val GLYPH_INFO = "information-outline"
