package app.cue.launcher.designsystem

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cue.launcher.R

@Composable
fun SheetHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    @StringRes backDescription: Int = R.string.builder_sheet_back,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.highlightPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                GlyphIcon(
                    name = GLYPH_BACK,
                    size = Tokens.Settings.glyph,
                    tint = LocalContentColor.current,
                    contentDescription = stringResource(backDescription),
                )
            }
        }
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack == null) Tokens.Settings.highlightPadding else 0.dp),
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        IconButton(onClick = onClose) {
            GlyphIcon(
                name = GLYPH_CLOSE,
                size = Tokens.Settings.glyph,
                tint = LocalContentColor.current,
                contentDescription = stringResource(R.string.builder_sheet_close),
            )
        }
    }
}

@Composable
fun SheetSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    SearchField(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = placeholder,
        container = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.surfacePadding),
    )
}

@Composable
fun SheetNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.screenPadding),
        style = supportingStyle,
        color = supportingInk(),
    )
}

@Composable
fun SheetBottomFade(modifier: Modifier = Modifier) {
    val sheet = MaterialTheme.colorScheme.surfaceContainerLow
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Tokens.Builder.sheetFadeHeight)
            .background(Brush.verticalGradient(listOf(Color.Transparent, sheet))),
    )
}

private const val GLYPH_CLOSE = "close"
