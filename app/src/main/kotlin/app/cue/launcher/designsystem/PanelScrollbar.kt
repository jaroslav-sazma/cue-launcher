package app.cue.launcher.designsystem

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity

@Composable
fun PanelScrollbar(state: ScrollState, modifier: Modifier = Modifier) {
    val ink = LocalCueColors.current.textPrimary
    val density = LocalDensity.current
    val minLengthPx = with(density) { Tokens.Panel.Scrollbar.minThumbLength.toPx() }
    val corner = with(density) { CornerRadius(Tokens.Panel.Scrollbar.radius.toPx()) }
    Spacer(
        modifier = modifier
            .width(Tokens.Panel.Scrollbar.width)
            .drawBehind {
                val thumb = scrollbarThumb(
                    trackLengthPx = size.height,
                    viewportPx = state.viewportSize.toFloat(),

                    contentPx = state.viewportSize.toFloat() + state.maxValue.toFloat(),
                    scrollOffsetPx = state.value.toFloat(),
                    minLengthPx = minLengthPx,
                ) ?: return@drawBehind
                drawRoundRect(
                    color = ink.copy(alpha = Tokens.Panel.Scrollbar.TRACK_OPACITY),
                    cornerRadius = corner,
                )
                drawRoundRect(
                    color = ink.copy(alpha = Tokens.Panel.Scrollbar.THUMB_OPACITY),
                    topLeft = Offset(0f, thumb.offsetPx),
                    size = Size(size.width, thumb.lengthPx),
                    cornerRadius = corner,
                )
            },
    )
}
