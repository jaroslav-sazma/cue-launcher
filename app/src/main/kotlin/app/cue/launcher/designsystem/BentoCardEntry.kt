package app.cue.launcher.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp

// Card `index` of `count` waits its turn before it starts travelling, so the bento hands itself
// over from the top down. Every card still lands exactly when the swipe settles.
fun cardStaggerProgress(progress: Float, index: Int, count: Int): Float {
    val stagger = Tokens.Motion.Depth.CARD_STAGGER
    val start = if (count <= 1) 0f else stagger * index.coerceIn(0, count - 1) / (count - 1)
    return ((progress.coerceIn(0f, 1f) - start) / (1f - stagger)).coerceIn(0f, 1f)
}

// A card arrives as out of focus as the backdrop and pulls into focus as it lands. It does that by
// holding its own pane back rather than by widening it: the page's deeply blurred region is what
// shows through until the card takes over, and a radius that changes every frame makes the platform
// rebuild its blur for the same picture.
fun cardArrivalDepth(progress: Float, index: Int, count: Int) = BlurRegionDepth(
    alpha = cardPaneReveal(cardStaggerProgress(progress, index, count)),
)

fun cardPaneReveal(arrival: Float): Float {
    val start = Tokens.Motion.Depth.CARD_PANE_REVEAL
    return ((arrival.coerceIn(0f, 1f) - start) / (1f - start)).coerceIn(0f, 1f)
}

fun Modifier.bentoCardEnter(
    reducedMotion: Boolean,
    index: Int,
    count: Int,
    progress: () -> Float,
): Modifier = graphicsLayer {
    if (reducedMotion) return@graphicsLayer
    val amount = cardStaggerProgress(progress(), index, count)
    val scale = lerp(Tokens.Motion.Depth.CARD_SCALE, 1f, amount)
    scaleX = scale
    scaleY = scale
    translationY = Tokens.Motion.Depth.cardRise.toPx() * (1f - amount)
}
