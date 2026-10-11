package app.cue.launcher.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import app.cue.launcher.R
import app.cue.launcher.data.scenario.SemanticStyle
import app.cue.launcher.designsystem.CueColors
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.engine.ProgressTrack

@Composable
fun TileProgressBar(
    track: ProgressTrack,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCueColors.current
    val percent = track.percent
    val description = if (percent != null) {
        stringResource(R.string.tile_progress_percent, percent)
    } else {
        stringResource(R.string.tile_progress_running)
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(Tokens.Progress.height)
            .clip(RoundedCornerShape(Tokens.Progress.radius))
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val width = size.width
        track.bands.forEach { band ->
            val start = band.start * width
            drawRect(
                color = colors.ink(band.style, accent).copy(
                    alpha = when {
                        track.fraction == null -> Tokens.Progress.INDETERMINATE_OPACITY
                        band.filled -> 1f
                        else -> Tokens.Progress.TRACK_OPACITY
                    },
                ),
                topLeft = Offset(start, 0f),
                size = Size(width = (band.end * width - start).coerceAtLeast(0f), height = size.height),
            )
        }
        val markRadius = Tokens.Progress.markDiameter.toPx() / 2
        track.marks.forEach { mark ->
            drawCircle(
                color = colors.ink(mark.style, accent),
                radius = markRadius,

                center = Offset(
                    x = (mark.at * width).coerceIn(markRadius, (width - markRadius).coerceAtLeast(markRadius)),
                    y = size.height / 2,
                ),
            )
        }
    }
}

internal fun CueColors.ink(style: SemanticStyle, accent: Color): Color = when (style) {
    SemanticStyle.UNSPECIFIED -> accent
    SemanticStyle.INFO -> semanticInfo
    SemanticStyle.SAFE -> semanticSafe
    SemanticStyle.CAUTION -> semanticCaution
    SemanticStyle.DANGER -> semanticDanger
}
