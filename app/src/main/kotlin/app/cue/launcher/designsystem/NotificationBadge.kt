package app.cue.launcher.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.cue.launcher.R

// The badge reaches past the icon's corner, so any clip the caller puts on the icon has to stay
// inside [icon] rather than on [modifier]. A null [count] means badges are off.
@Composable
fun BadgedIcon(count: (() -> Int)?, modifier: Modifier = Modifier, icon: @Composable () -> Unit) {
    Box(modifier = modifier) {
        icon()
        if (count != null) {
            BadgeSlot(
                count = count,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = Tokens.Badge.overhang, y = -Tokens.Badge.overhang),
            )
        }
    }
}

// A count-less mark for an icon that stands for several apps. Its slot is a single-digit badge's
// size at the default text size, so the dot is centred where that badge would be.
@Composable
fun DottedIcon(count: (() -> Int)?, modifier: Modifier = Modifier, icon: @Composable () -> Unit) {
    Box(modifier = modifier) {
        icon()
        if (count != null) {
            DotSlot(
                count = count,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = Tokens.Badge.overhang, y = -Tokens.Badge.overhang)
                    .size(Tokens.Badge.minSize),
            )
        }
    }
}

@Composable
private fun DotSlot(count: () -> Int, modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()
    val fade = effectsFastSpec()
    val pop = spatialFastSpec<Float>(reducedMotion)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = count() > 0,
            enter = if (reducedMotion) fadeIn(fade) else scaleIn(pop) + fadeIn(fade),
            exit = if (reducedMotion) fadeOut(fade) else scaleOut(pop) + fadeOut(fade),
            label = "badge.dot",
        ) {
            NotificationDot()
        }
    }
}

@Composable
private fun NotificationDot() {
    val colors = LocalCueColors.current
    val shadow = Tokens.Badge.shadow.takeUnless { colors.reducedTransparency }
    Box(
        modifier = Modifier
            .size(Tokens.Badge.dotSize)
            .then(if (shadow == null) Modifier else Modifier.cueShadow(CircleShape, shadow))
            .clip(CircleShape)
            .background(color = colors.accent),
    )
}

// Its own function so that reading the count recomposes only the badge.
@Composable
private fun BadgeSlot(count: () -> Int, modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()
    val fade = effectsFastSpec()
    val pop = spatialFastSpec<Float>(reducedMotion)
    // Keyed on visibility alone, so the badge that leaves keeps showing its last count. TopEnd on
    // both sides pins it to the icon's corner even while the container is empty.
    AnimatedContent(
        targetState = count(),
        modifier = modifier,
        contentKey = { it > 0 },
        contentAlignment = Alignment.TopEnd,
        transitionSpec = {
            val enter = if (reducedMotion) fadeIn(fade) else scaleIn(pop) + fadeIn(fade)
            val exit = if (reducedMotion) fadeOut(fade) else scaleOut(pop) + fadeOut(fade)
            (enter togetherWith exit).using(null)
        },
        label = "badge.visibility",
    ) { shown ->
        if (shown > 0) NotificationBadge(count = shown, reducedMotion = reducedMotion)
    }
}

@Composable
private fun NotificationBadge(count: Int, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalCueColors.current
    val fade = effectsFastSpec()
    val slide = spatialFastSpec<IntOffset>(reducedMotion)
    val resize = spatialFastSpec<IntSize>(reducedMotion)
    val shadow = Tokens.Badge.shadow.takeUnless { colors.reducedTransparency }
    Box(
        modifier = modifier
            .noNarrowerThanTall()
            .then(if (shadow == null) Modifier else Modifier.cueShadow(CircleShape, shadow))
            .clip(CircleShape)
            .background(color = colors.accent)
            .defaultMinSize(minWidth = Tokens.Badge.minSize, minHeight = Tokens.Badge.minSize)
            .padding(horizontal = Tokens.Badge.paddingHorizontal)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = count,
            contentKey = { if (badgeOverflows(it)) OVERFLOW_KEY else it },
            contentAlignment = Alignment.Center,
            transitionSpec = {
                val rising = if (targetState > initialState) 1 else -1
                val swap = if (reducedMotion) {
                    fadeIn(fade) togetherWith fadeOut(fade)
                } else {
                    (slideInVertically(slide) { height -> rising * height } + fadeIn(fade))
                        .togetherWith(slideOutVertically(slide) { height -> -rising * height } + fadeOut(fade))
                }
                swap.using(SizeTransform(clip = false) { _, _ -> resize })
            },
            label = "badge.count",
        ) { shown ->
            Text(
                text = if (badgeOverflows(shown)) {
                    stringResource(R.string.notification_badge_overflow, Tokens.Badge.OVERFLOW_THRESHOLD)
                } else {
                    stringResource(R.string.notification_badge_number, shown)
                },
                color = colors.onAccent(),
                style = Tokens.Type.badge.toTextStyle(),
                maxLines = 1,
            )
        }
    }
}

// Keeps a single digit a circle at any font size, while "9+" may still widen into a pill.
private fun Modifier.noNarrowerThanTall(): Modifier = layout { measurable, constraints ->
    val height = measurable.maxIntrinsicHeight(constraints.maxWidth)
    val placeable = measurable.measure(
        constraints.copy(minWidth = height.coerceIn(constraints.minWidth, constraints.maxWidth)),
    )
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

fun badgeOverflows(count: Int): Boolean = count > Tokens.Badge.OVERFLOW_THRESHOLD

private const val OVERFLOW_KEY = -1
