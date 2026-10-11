package app.cue.launcher.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.cue.launcher.R
import app.cue.launcher.designsystem.specularSheen
import app.cue.launcher.designsystem.CueColors
import app.cue.launcher.designsystem.GlassLevel
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.TileSurface
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.toTextStyle
import app.cue.launcher.designsystem.rememberReducedMotion
import app.cue.launcher.designsystem.spatialFastSpec
import app.cue.launcher.designsystem.wallpaperBlurRegion

@Composable
fun SearchbarTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    // Null where the searchbar stands on a pane that blurs already — a dock or a panel card — which
    // also casts the shadow, so the pill casts none of its own there.
    blurRegionKey: String? = null,
) {
    val colors = LocalCueColors.current
    val label = stringResource(R.string.searchbar_hint)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) Tokens.Searchbar.PRESS_SCALE else 1f,
        animationSpec = spatialFastSpec(rememberReducedMotion()),
        label = "searchbarPress",
    )
    val press = colors.textPrimary.copy(
        alpha = if (colors.isDark) Tokens.Menu.PRESS_OPACITY_DARK else Tokens.Menu.PRESS_OPACITY_LIGHT,
    )
    TileSurface(
        modifier = modifier
            .height(Tokens.Size.searchbarHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (blurRegionKey == null) {
                    Modifier
                } else {
                    Modifier.wallpaperBlurRegion(key = blurRegionKey, cornerRadius = Tokens.Radius.searchbar)
                },
            )
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onLongClickLabel = longClickLabel.takeIf { onLongClick != null },
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        level = GlassLevel.THIN,
        radius = Tokens.Radius.searchbar,
        contentPadding = Tokens.Searchbar.discInset,
        shadow = Tokens.Elevation.zone.takeIf { blurRegionKey != null },
        surface = if (pressed) press.compositeOver(colors.glass(GlassLevel.THIN)) else null,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Searchbar.gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchPlaceholder(label, Modifier.weight(1f))
            SearchDisc(press)
        }
    }
}

@Composable
private fun SearchPlaceholder(label: String, modifier: Modifier) {
    Text(
        text = label,
        modifier = modifier.padding(start = Tokens.Searchbar.labelInset - Tokens.Searchbar.discInset),
        color = LocalCueColors.current.textSecondary,
        style = Tokens.Searchbar.label.toTextStyle(),
        maxLines = Tokens.Type.LABEL_LINES,
        overflow = Tokens.Type.labelOverflow,
    )
}

@Composable
private fun SearchDisc(press: Color) {
    val colors = LocalCueColors.current
    val sheen = remember(colors) { specularSheen(colors) }
    TileSurface(
        modifier = Modifier.size(Tokens.Searchbar.disc),
        level = GlassLevel.THICK,
        contentPadding = 0.dp,
        shadow = null,
        shape = CircleShape,
        // Opaque glass has no depth to stack, so there the disc is a pressed-in step.
        surface = press.takeIf { colors.reducedTransparency },
        sheen = sheen,
    ) {
        GlyphIcon(
            name = GLYPH_SEARCH,
            size = Tokens.Searchbar.glyph,
            tint = colors.textPrimary,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

private fun specularSheen(colors: CueColors): Brush = specularSheen(
    colors = colors,
    opacityLight = Tokens.Searchbar.SPECULAR_OPACITY_LIGHT,
    opacityDark = Tokens.Searchbar.SPECULAR_OPACITY_DARK,
    extent = Tokens.Searchbar.SPECULAR_EXTENT,
)

private const val GLYPH_SEARCH = "magnify"
