package app.cue.launcher.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// Narrower than the M3 default so that the row text lands on the same 24 dp line as a section
// label once the row surface is inset by rowMargin. The vertical half stays at the M3 value.
val rowContentPadding: PaddingValues = PaddingValues(
    start = Tokens.Settings.rowContentPadding,
    top = ListItemDefaults.ContentPadding.calculateTopPadding(),
    end = Tokens.Settings.rowContentPadding,
    bottom = ListItemDefaults.ContentPadding.calculateBottomPadding(),
)

// The design puts a check in the thumb of an on switch; off stays empty.
@Composable
fun RowSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        thumbContent = if (checked) {
            {
                GlyphIcon(
                    name = GLYPH_CHECK,
                    size = SwitchDefaults.IconSize,
                    tint = LocalContentColor.current,
                )
            }
        } else {
            null
        },
    )
}

@Composable
fun LeadingIconBox(
    glyph: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(Tokens.Settings.leadingBox)
            .background(
                color = if (enabled) scheme.secondaryContainer else scheme.surfaceContainerHigh,
                shape = RoundedCornerShape(Tokens.Settings.leadingRadius),
            ),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            name = glyph,
            size = Tokens.Settings.leadingGlyph,
            tint = if (enabled) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        )
    }
}
