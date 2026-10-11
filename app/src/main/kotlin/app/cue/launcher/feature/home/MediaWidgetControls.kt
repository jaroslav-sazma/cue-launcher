package app.cue.launcher.feature.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import app.cue.launcher.R
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.cueShadow
import app.cue.launcher.designsystem.rememberReducedMotion
import app.cue.launcher.designsystem.spatialFastSpec
import app.cue.launcher.system.media.MediaButtons
import app.cue.launcher.system.media.MediaControls
import kotlinx.coroutines.delay

private typealias Controls = Tokens.CustomWidget.Media

@Composable
internal fun MediaControlRow(buttons: MediaButtons, controls: MediaControls?, modifier: Modifier = Modifier) {
    // The tap shows straight away; any report from the session, or the hold running out, takes over.
    var optimistic by remember(buttons) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(optimistic) {
        if (optimistic != null) {
            delay(Controls.OPTIMISTIC_HOLD_MS)
            optimistic = null
        }
    }
    val playing = optimistic ?: buttons.playing
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Controls.controlGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkipButton(
            glyph = GLYPH_PREVIOUS,
            label = stringResource(R.string.media_widget_previous),
            onClick = controls?.let { it::previous }.takeIf { buttons.canPrevious },
        )
        PlayButton(
            playing = playing,
            onClick = controls?.takeIf { buttons.canPlayPause }?.let { player ->
                {
                    if (playing) player.pause() else player.play()
                    optimistic = !playing
                }
            },
        )
        SkipButton(
            glyph = GLYPH_NEXT,
            label = stringResource(R.string.media_widget_next),
            onClick = controls?.let { it::next }.takeIf { buttons.canNext },
        )
    }
}

// A disabled control takes no input at all, so a tap on it falls through to the widget and opens
// the player, as a tap anywhere else on it does.
private fun Modifier.mediaButton(
    onClick: (() -> Unit)?,
    label: String,
    interaction: MutableInteractionSource,
): Modifier = if (onClick == null) {
    this
} else {
    clickable(interactionSource = interaction, indication = null, onClickLabel = label, onClick = onClick)
        .semantics { role = Role.Button }
}

@Composable
private fun SkipButton(glyph: String, label: String, onClick: (() -> Unit)?) {
    val colors = LocalCueColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) Controls.SKIP_PRESS_SCALE else 1f,
        animationSpec = spatialFastSpec(rememberReducedMotion()),
        label = "mediaSkipPress",
    )
    val press = colors.textPrimary.copy(
        alpha = if (colors.isDark) Controls.PRESS_OPACITY_DARK else Controls.PRESS_OPACITY_LIGHT,
    )
    Box(
        modifier = Modifier
            .size(Controls.skip)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (pressed) press else Color.Transparent)
            .mediaButton(onClick, label, interaction),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            name = glyph,
            size = Controls.skipGlyph,
            tint = colors.textPrimary.copy(alpha = if (onClick == null) Controls.DISABLED_CONTENT_OPACITY else 1f),
            contentDescription = label,
        )
    }
}

@Composable
private fun PlayButton(playing: Boolean, onClick: (() -> Unit)?) {
    val colors = LocalCueColors.current
    val reducedMotion = rememberReducedMotion()
    val enabled = onClick != null
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) Controls.PLAY_PRESS_SCALE else 1f,
        animationSpec = spatialFastSpec(reducedMotion),
        label = "mediaPlayPress",
    )
    val radius by animateDpAsState(
        targetValue = if (playing && enabled) Controls.playRadiusPlaying else Controls.playRadiusPaused,
        animationSpec = spatialFastSpec(reducedMotion),
        label = "mediaPlayShape",
    )
    val shape = RoundedCornerShape(radius)
    val label = stringResource(if (playing) R.string.media_widget_pause else R.string.media_widget_play)
    val shadow = when {
        !enabled || colors.reducedTransparency -> null
        colors.isDark -> Controls.playShadowDark
        else -> Controls.playShadowLight
    }
    Box(
        modifier = Modifier
            .size(Controls.play)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (shadow == null) Modifier else Modifier.cueShadow(shape, shadow))
            .clip(shape)
            .background(
                if (enabled) {
                    colors.accent
                } else {
                    colors.textPrimary.copy(alpha = Controls.DISABLED_CONTAINER_OPACITY)
                },
            )
            .mediaButton(onClick, label, interaction),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            name = if (playing && enabled) GLYPH_PAUSE else GLYPH_PLAY,
            size = Controls.playGlyph,
            tint = if (enabled) {
                colors.onAccent()
            } else {
                colors.textPrimary.copy(alpha = Controls.DISABLED_CONTENT_OPACITY)
            },
            contentDescription = label,
        )
    }
}

private const val GLYPH_PREVIOUS = "skip-previous"
private const val GLYPH_NEXT = "skip-next"
private const val GLYPH_PLAY = "play"
private const val GLYPH_PAUSE = "pause"
