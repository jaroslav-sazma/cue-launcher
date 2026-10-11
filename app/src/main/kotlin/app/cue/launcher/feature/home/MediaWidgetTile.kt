package app.cue.launcher.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cue.launcher.R
import app.cue.launcher.designsystem.specularSheen
import app.cue.launcher.designsystem.ContrastAccents
import app.cue.launcher.designsystem.ContrastEngine
import app.cue.launcher.designsystem.CueColors
import app.cue.launcher.designsystem.GlassLevel
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.toTextStyle
import app.cue.launcher.system.media.MediaButtons
import app.cue.launcher.system.media.MediaControls
import app.cue.launcher.system.media.MediaWidgetRead
import app.cue.launcher.system.media.NowPlaying

private typealias Media = Tokens.CustomWidget.Media

@Composable
internal fun MediaWidget(modifier: Modifier = Modifier) {
    val feed = LocalMediaFeed.current
    val state by remember(feed) { feed.state() }.collectAsStateWithLifecycle(initialValue = null)
    val read = state?.read
    val track = (read as? MediaWidgetRead.Playing)?.track
    val colors = LocalCueColors.current
    val art = track?.art?.takeUnless { colors.reducedTransparency }
    val shape = RoundedCornerShape(Tokens.Radius.widgetInner)

    // Clipped ahead of the caller's modifier: a click it carries draws its ripple there, and the
    // ripple has to keep to the corners of the glass.
    Box(modifier = Modifier.clip(shape).then(modifier).fillMaxSize()) {
        if (art != null) {
            val image = remember(art) { art.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                colorFilter = GRAYSCALE,
            )
        }
        // The cover stands in for the wallpaper, so the glass over it and the ink on that glass are
        // worked out by the same engine, only from the cover's luminance.
        val ground = remember(colors, track?.artLuminance, art != null) {
            track?.artLuminance?.takeIf { art != null }?.let { colors.overGround(it) } ?: colors
        }
        CompositionLocalProvider(LocalCueColors provides ground) {
            MediaGlass(shape = shape)
            MediaContent(state = state, track = track, controls = (read as? MediaWidgetRead.Playing)?.controls)
        }
    }
}

private val GRAYSCALE = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

private fun CueColors.overGround(luminance: Double): CueColors = copy(
    scheme = ContrastEngine.compute(
        wallpaperLuminance = luminance,
        isDark = isDark,
        accents = ContrastAccents(
            accent = scheme.accent,
            palette = scheme.accentPalette,
            glassReference = scheme.glassReference,
        ),
    ),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaGlass(shape: RoundedCornerShape) {
    val colors = LocalCueColors.current
    val glass by animateColorAsState(
        targetValue = colors.glass(GlassLevel.THICK),
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "mediaGlass",
    )
    val sheen = remember(colors) { mediaSheen(colors) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(glass)
            .then(if (colors.reducedTransparency) Modifier else Modifier.background(sheen))
            .border(Tokens.Material.edgeWidth, colors.edge(GlassLevel.THICK), shape),
    )
}

private fun mediaSheen(colors: CueColors): Brush = specularSheen(
    colors = colors,
    opacityLight = Media.SPECULAR_OPACITY_LIGHT,
    opacityDark = Media.SPECULAR_OPACITY_DARK,
    extent = Media.SPECULAR_EXTENT,
)

@Composable
private fun MediaContent(state: MediaWidgetState?, track: NowPlaying?, controls: MediaControls?) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(Media.halfGap),
    ) {
        MediaText(
            lines = mediaLines(state, track),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        MediaControlRow(
            buttons = track?.buttons.takeIf { controls != null } ?: DISABLED,
            controls = controls,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

private val DISABLED = MediaButtons(playing = false, canPlayPause = false, canPrevious = false, canNext = false)

private data class MediaLines(val title: String, val detail: String?)

@Composable
private fun mediaLines(state: MediaWidgetState?, track: NowPlaying?): MediaLines {
    val label = state?.appLabel
    val nothing = stringResource(R.string.media_widget_idle)
    return when {
        track != null -> MediaLines(track.title, track.artist)
        state?.read == MediaWidgetRead.NoAccess -> MediaLines(
            title = stringResource(R.string.media_widget_denied),
            detail = stringResource(R.string.media_widget_denied_action),
        )

        label != null -> MediaLines(label, nothing)
        else -> MediaLines(nothing, detail = null)
    }
}

@Composable
private fun MediaText(lines: MediaLines, modifier: Modifier = Modifier) {
    val colors = LocalCueColors.current
    Column(
        modifier = modifier.padding(start = Media.metaInsetStart, end = Media.metaInsetEnd),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = lines.title,
            color = colors.textPrimary,
            style = Media.title.toTextStyle(),
            lineHeight = Media.title.lineHeight,
            maxLines = Media.TITLE_LINES,
            overflow = Tokens.Type.labelOverflow,
        )
        lines.detail?.let { detail ->
            Text(
                text = detail,
                modifier = Modifier.padding(top = Media.artistGap),
                color = colors.textSecondary,
                style = Media.artist.toTextStyle(),
                lineHeight = Media.artist.lineHeight,
                maxLines = Tokens.Type.LABEL_LINES,
                overflow = Tokens.Type.labelOverflow,
            )
        }
    }
}
