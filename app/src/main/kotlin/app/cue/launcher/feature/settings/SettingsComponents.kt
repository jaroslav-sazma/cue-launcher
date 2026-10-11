package app.cue.launcher.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import app.cue.launcher.designsystem.GLYPH_CHEVRON
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.IconTint
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.RowSwitch
import app.cue.launcher.designsystem.rowContentPadding
import app.cue.launcher.designsystem.selectedSupportingInk
import app.cue.launcher.designsystem.supportingInk
import app.cue.launcher.designsystem.supportingStyle

@Composable
internal fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    choices: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (choices) Modifier.selectableGroup() else Modifier),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(
                start = Tokens.Settings.screenPadding,
                end = Tokens.Settings.screenPadding,
                top = Tokens.Settings.sectionSpacing,
            ),
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
internal fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    minHeight: Dp = Tokens.Settings.choiceRowHeight,
    onClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    badge: @Composable (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val rowModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = Tokens.Settings.rowMargin)
        .clip(RoundedCornerShape(Tokens.Settings.highlightRadius))
        .heightIn(min = minHeight)
        .alpha(if (enabled) 1f else Tokens.Scenarios.DISABLED_ALPHA)
    val headline: @Composable () -> Unit = { RowHeadline(label = label, selected = selected, badge = badge) }
    val supporting: @Composable (() -> Unit)? = detail?.let {
        {
            Text(
                text = it,
                style = supportingStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    val colors = ListItemDefaults.colors(
        containerColor = if (selected) scheme.secondaryContainer else Color.Transparent,
        headlineColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
        supportingColor = if (selected) selectedSupportingInk() else supportingInk(),
    )

    if (onClick == null) {
        ListItem(
            modifier = rowModifier,
            enabled = enabled,
            supportingContent = supporting,
            leadingContent = leading,
            trailingContent = trailing,
            colors = colors,
            contentPadding = rowContentPadding,
            content = headline,
        )
    } else {
        ListItem(
            onClick = onClick,
            modifier = rowModifier,
            enabled = enabled,
            supportingContent = supporting,
            leadingContent = leading,
            trailingContent = trailing,
            colors = colors,
            contentPadding = rowContentPadding,
            content = headline,
        )
    }
}

@Composable
private fun RowHeadline(label: String, selected: Boolean, badge: @Composable (() -> Unit)?) {
    val style = with(MaterialTheme.typography) { if (selected) bodyLargeEmphasized else bodyLarge }
    if (badge == null) {
        Text(text = label, style = style)
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.textSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, modifier = Modifier.weight(1f, fill = false), style = style)
            badge()
        }
    }
}

@Composable
internal fun SettingsSwitchRow(
    label: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    badge: @Composable (() -> Unit)? = null,
) {
    SettingsRow(
        label = label,
        modifier = modifier,
        detail = detail,
        onClick = { onCheckedChange(!checked) },
        leading = leading,
        badge = badge,
        trailing = {
            RowSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.semantics { contentDescription = label },
            )
        },
    )
}

@Composable
internal fun SettingsChoiceRow(
    label: String,
    detail: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    SettingsRow(
        label = label,
        modifier = modifier,
        detail = detail,
        selected = selected,
        enabled = enabled,
        onClick = onSelect,
        leading = {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = label },
            )
        },
        trailing = trailing,
    )
}

@Composable
internal fun SettingsLinkRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    value: String? = null,
) {
    SettingsRow(
        label = label,
        modifier = modifier,
        detail = detail,
        minHeight = Tokens.Settings.linkRowHeight,
        onClick = onClick,
        trailing = { LinkTrailing(value = value) },
    )
}

@Composable
internal fun SettingsActionRow(
    label: String,
    detail: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = false,
) {
    SettingsRow(
        label = label,
        modifier = modifier,
        detail = detail,
        onClick = onClick,
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onAction) { Text(text = action) }
                if (chevron) LinkTrailing(value = null)
            }
        },
    )
}

@Composable
internal fun SettingsFixedRow(label: String, detail: String, modifier: Modifier = Modifier) {
    SettingsRow(label = label, modifier = modifier, detail = detail, enabled = false)
}

@Composable
internal fun rememberSettingsIconTint(): IconTint {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme.surfaceContainerHighest, scheme.onSurface) {
        IconTint(
            background = scheme.surfaceContainerHighest.toArgb(),
            foreground = scheme.onSurface.toArgb(),
        )
    }
}

@Composable
private fun LinkTrailing(value: String?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        value?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GlyphIcon(
            name = GLYPH_CHEVRON,
            size = Tokens.Settings.glyph,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

