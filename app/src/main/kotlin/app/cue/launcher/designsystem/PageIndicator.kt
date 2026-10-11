package app.cue.launcher.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.lerp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.ceil

@Composable
fun PageIndicator(
    pageCount: Int,
    position: () -> Float,
    pageLabel: (Int) -> String,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    actionCue: PageActionCue? = null,
    alpha: () -> Float = { 1f },
    hidden: () -> Boolean = { false },
) {
    val current = pageIndicatorPosition(position(), pageCount)
    Row(
        // The row only ever fades; it keeps its size so the zones around it never shift.
        modifier = modifier
            .height(Tokens.Indicator.Page.rowHeight)
            .graphicsLayer { this.alpha = alpha() }
            .then(if (hidden()) Modifier.clearAndSetSemantics {} else Modifier),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Indicator.Page.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { page ->
            val label = pageLabel(page)
            PageDot(
                activeness = pageDotActiveness(page, current),
                modifier = Modifier.semantics {
                    contentDescription = label
                    onClick {
                        onSelectPage(page)
                        true
                    }
                },
            )
        }
        // The cue sits last because the action it stands for lives right of the home screen.
        actionCue?.let { cue ->
            ActionCue(
                modifier = Modifier.semantics {
                    contentDescription = cue.label
                    onClick {
                        cue.onRun()
                        true
                    }
                },
            )
        }
    }
}

@Stable
class PageActionCue(val label: String, val onRun: () -> Unit)

@Stable
class PageIndicatorVisibility(val alpha: () -> Float, val hidden: () -> Boolean)

fun pageIndicatorPinned(scrollInProgress: Boolean, pageOffsetFraction: Float): Boolean =
    scrollInProgress || pageOffsetFraction != 0f

@Composable
fun rememberPageIndicatorAlpha(pinned: () -> Boolean, wake: Any?): PageIndicatorVisibility {
    val alpha = remember { Animatable(1f) }
    var hidden by remember { mutableStateOf(false) }
    val appear = effectsFastSpec()
    val vanish = effectsSlowSpec()
    LaunchedEffect(wake) {
        hidden = false
        alpha.animateTo(1f, appear)
        snapshotFlow(pinned).collectLatest { held ->
            if (held) {
                hidden = false
                alpha.animateTo(1f, appear)
            } else {
                delay(Tokens.Indicator.Page.AUTO_HIDE_DELAY_MS)
                alpha.animateTo(0f, vanish)
                hidden = true
            }
        }
    }
    return PageIndicatorVisibility(alpha = { alpha.value }, hidden = { hidden })
}

@Composable
private fun ActionCue(modifier: Modifier = Modifier) {
    val colors = LocalCueColors.current
    Spacer(
        modifier = modifier
            .size(Tokens.Indicator.ActionCue.size)
            .border(
                width = Tokens.Indicator.ActionCue.stroke,
                color = colors.labelOnWallpaper.copy(alpha = Tokens.Indicator.ON_WALLPAPER_OPACITY),
                shape = RoundedCornerShape(Tokens.Indicator.ActionCue.radius),
            ),
    )
}

fun pageDotActiveness(page: Int, position: Float): Float =
    (1f - abs(page - position)).coerceIn(0f, 1f)

// The indicator travels off screen with the home page it sits on, and home is the last page, so the
// pill has to finish morphing within the first half of the swipe away from it — past that point
// nobody is looking at it any more.
fun pageIndicatorPosition(position: Float, pageCount: Int): Float {
    val last = (pageCount - 1).toFloat()
    if (position <= 0f || position >= last) return position
    val page = ceil(position)
    return page - ((page - position) * Tokens.Indicator.Page.MORPH_GAIN).coerceAtMost(1f)
}

@Composable
private fun PageDot(activeness: Float, modifier: Modifier = Modifier) {
    val colors = LocalCueColors.current
    val shape = RoundedCornerShape(Tokens.Indicator.Page.radius)
    Spacer(
        modifier = modifier
            .width(lerp(Tokens.Indicator.Page.inactive, Tokens.Indicator.Page.activeWidth, activeness))
            .height(Tokens.Indicator.Page.activeHeight)
            .background(
                color = colors.labelOnWallpaper.copy(alpha = Tokens.Indicator.ON_WALLPAPER_OPACITY),
                shape = shape,
            ),
    )
}
