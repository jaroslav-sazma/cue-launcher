package app.cue.launcher.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import app.cue.launcher.designsystem.glyph.LocalMdiGlyphs
import app.cue.launcher.designsystem.glyph.MdiFontFamily

@Composable
fun GlyphIcon(
    name: String?,
    modifier: Modifier = Modifier,
    size: Dp = Tokens.Size.glyphInTile,
    tint: Color = LocalCueColors.current.textPrimary,
    contentDescription: String? = null,
) {
    val glyph = LocalMdiGlyphs.current.glyphOf(name)
    if (glyph == null) {
        Spacer(modifier = modifier.size(size))
        return
    }
    val fontSize = with(LocalDensity.current) { size.toSp() }
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (contentDescription == null) {
                    Modifier
                } else {
                    Modifier.semantics { this.contentDescription = contentDescription }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = glyph,
            style = TextStyle(
                color = tint,
                fontSize = fontSize,
                fontFamily = MdiFontFamily,
                lineHeight = fontSize,
                textAlign = TextAlign.Center,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
        )
    }
}
