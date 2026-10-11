package app.cue.launcher.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R

// The row every settings surface is built from: a tonal plate inset by rowMargin, carrying its
// content on the same 24 dp line as the section label above it.
@Composable
fun TonalRow(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    error: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val container = when {
        error -> scheme.errorContainer
        selected -> scheme.secondaryContainer
        else -> Color.Transparent
    }
    val ink = when {
        error -> scheme.onErrorContainer
        selected -> scheme.onSecondaryContainer
        else -> scheme.onSurface
    }
    Surface(
        onClick = onClick ?: {},
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.rowMargin),
        enabled = onClick != null && enabled,
        shape = RoundedCornerShape(Tokens.Settings.highlightRadius),
        color = container,
        contentColor = ink,
    ) {
        RowDetailPlate(container = container, ink = ink, error = error) {
            Row(
                modifier = Modifier
                    .heightIn(min = Tokens.Settings.choiceRowHeight)
                    .padding(
                        horizontal = Tokens.Settings.rowContentPadding,
                        vertical = Tokens.Settings.rowGap,
                    ),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.surfacePadding),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
fun RowText(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    badge: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {
        if (badge == null) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.textSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyLarge,
                )
                badge()
            }
        }
        detail?.let {
            val ink = LocalRowDetailInk.current.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant }
            if (LocalRowDetailIsMessage.current) {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = ink)
            } else {
                Text(text = it, style = supportingStyle, color = supportingInk(ink))
            }
        }
    }
}

@Composable
fun ExperimentalLabel(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Tokens.Scenarios.badgeRadius),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            text = stringResource(R.string.experimental_label),
            modifier = Modifier.padding(horizontal = Tokens.Scenarios.badgePadding, vertical = Tokens.Settings.rowGap),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

// A filled plate is the ground its detail line is shaded against. On an error plate the detail is the
// message itself, so it keeps the full ink and the body size.
@Composable
internal fun RowDetailPlate(
    container: Color,
    ink: Color,
    error: Boolean,
    content: @Composable () -> Unit,
) {
    if (container == Color.Transparent) {
        content()
    } else {
        CompositionLocalProvider(
            LocalSupportingGround provides container,
            LocalRowDetailInk provides ink,
            LocalRowDetailIsMessage provides error,
            content = content,
        )
    }
}

private val LocalRowDetailInk = staticCompositionLocalOf { Color.Unspecified }

private val LocalRowDetailIsMessage = staticCompositionLocalOf { false }
