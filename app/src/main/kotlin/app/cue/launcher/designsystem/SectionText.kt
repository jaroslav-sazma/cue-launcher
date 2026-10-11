package app.cue.launcher.designsystem

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

@Composable
fun SectionLabel(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier.padding(
            start = Tokens.Settings.screenPadding,
            end = Tokens.Settings.screenPadding,
            top = Tokens.Settings.sectionSpacing,
        ),
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
    )
}

// The default modifier carries the screen inset, so a note inside an already padded container
// (a sheet footer, a save bar) has to pass an empty Modifier or it ends up indented twice.
@Composable
fun Note(
    text: String,
    modifier: Modifier = Modifier.padding(horizontal = Tokens.Settings.screenPadding),
) {
    Text(
        text = text,
        modifier = modifier,
        style = supportingStyle,
        color = supportingInk(),
    )
}

// Feedback and blockers — why Save is off, what an action just did — are what the user has to read,
// so unlike a Note they keep the body size and the full ink.
@Composable
fun Message(
    text: String,
    modifier: Modifier = Modifier.padding(horizontal = Tokens.Settings.screenPadding),
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

val supportingStyle: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.typography.bodySmall

val codeStyle: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)

// The shade is tuned to the ground it is given, so supporting text on anything but the screen's
// surface needs that surface provided as LocalSupportingGround or it drops below 4.5:1. With the
// system contrast raised the ink is not shaded at all.
@Composable
@ReadOnlyComposable
fun supportingInk(
    ink: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    ground: Color = LocalSupportingGround.current.takeOrElse { MaterialTheme.colorScheme.surface },
): Color = if (LocalRaisedContrast.current) ink else lightestShade(ink, ground)

@Composable
@ReadOnlyComposable
fun selectedSupportingInk(): Color =
    supportingInk(MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondaryContainer)

val LocalSupportingGround = staticCompositionLocalOf { Color.Unspecified }

val LocalRaisedContrast = staticCompositionLocalOf { false }

// A translucent ground cannot be measured without what lies under it, so its ink is left unshaded.
internal fun lightestShade(ink: Color, ground: Color): Color {
    val groundLuminance = ground.luminance().toDouble()
    if (ground.alpha < 1f || contrastOn(ink, groundLuminance) <= TARGET_RATIO) return ink
    var reads = 0f
    var fails = 1f
    repeat(SEARCH_STEPS) {
        val middle = (reads + fails) / 2
        if (contrastOn(lerp(ink, ground, middle), groundLuminance) >= TARGET_RATIO) reads = middle else fails = middle
    }
    return if (reads == 0f) ink else lerp(ink, ground, reads)
}

private fun contrastOn(ink: Color, groundLuminance: Double): Double =
    contrastRatio(ink.luminance().toDouble(), groundLuminance)

private const val SEARCH_STEPS = 12
