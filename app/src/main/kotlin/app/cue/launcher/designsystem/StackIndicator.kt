package app.cue.launcher.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription

@Composable
fun StackIndicator(
    dots: IntRange,
    activeIndex: Int,
    plusCount: Int,
    overWidget: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCueColors.current
    val tint = if (overWidget) {
        colors.tone.copy(
            alpha = if (colors.isDark) {
                Tokens.Indicator.Stack.TINT_OVER_WIDGET_DARK
            } else {
                Tokens.Indicator.Stack.TINT_OVER_WIDGET_LIGHT
            },
        )
    } else {
        colors.glass(GlassLevel.THICK)
    }
    Column(
        modifier = modifier
            .width(Tokens.Indicator.Stack.pillWidth)
            .background(color = tint, shape = RoundedCornerShape(Tokens.Indicator.Stack.pillRadius))
            .padding(vertical = Tokens.Indicator.Stack.pillPadding)

            .clearAndSetSemantics { this.contentDescription = contentDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Tokens.Indicator.Stack.gap),
    ) {
        dots.forEach { page -> StackDot(active = page == activeIndex, ink = colors.textPrimary) }
        if (plusCount > 0) {
            Text(
                text = "+$plusCount",
                color = colors.textPrimary,
                style = Tokens.Type.stackPlus.toTextStyle(),
                modifier = Modifier.padding(top = Tokens.Indicator.Stack.plusGap),
            )
        }
    }
}

@Composable
private fun StackDot(active: Boolean, ink: Color, modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()
    val width by animateDpAsState(
        targetValue = if (active) Tokens.Indicator.Stack.activeDotWidth else Tokens.Indicator.Stack.dot,
        animationSpec = spatialFastSpec(reducedMotion),
        label = "stack.dot.width",
    )
    val height by animateDpAsState(
        targetValue = if (active) Tokens.Indicator.Stack.activeDotHeight else Tokens.Indicator.Stack.dot,
        animationSpec = spatialFastSpec(reducedMotion),
        label = "stack.dot.height",
    )
    val color by animateColorAsState(
        targetValue = ink.copy(
            alpha = if (active) {
                Tokens.Indicator.Stack.ACTIVE_OPACITY
            } else {
                Tokens.Indicator.Stack.INACTIVE_OPACITY
            },
        ),
        animationSpec = spatialFastSpec(reducedMotion),
        label = "stack.dot.ink",
    )
    Spacer(
        modifier = modifier
            .width(width)
            .height(height)
            .background(color = color, shape = RoundedCornerShape(Tokens.Indicator.Stack.dotRadius)),
    )
}
