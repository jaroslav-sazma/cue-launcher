package app.cue.launcher.designsystem

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

// The launcher never names a typeface. Material's type scale resolves to FontFamily.SansSerif,
// which is the "sans-serif" alias of the device, so the launcher renders in whatever the system
// brand font is (Google Sans Flex where the device ships it, Roboto on AOSP) and follows it when
// the user changes it. Weights are variable-font axes on that family, so the whole scale is one
// typeface at different cuts — there is no separate display face to reach for.
//
// A TextStyle built with the TextStyle(...) constructor silently opts out of the Material
// defaults the theme installs (no font padding, centred line height), so the launcher surface
// composes its token type onto the inherited style instead of starting from scratch.
@Composable
@ReadOnlyComposable
fun TypeSpec.toTextStyle(color: Color = Color.Unspecified): TextStyle =
    LocalTextStyle.current.merge(
        TextStyle(
            color = color,
            fontSize = size,
            fontWeight = weight,
            letterSpacing = letterSpacing,
            lineHeight = lineHeight,
        ),
    )

// Where a single glyph carries a whole surface, Android stops treating the system font as a type
// scale and drives its variable axes by hand: SystemUI's lock screen clock exposes exactly Glyph
// Weight, Width, Roundness and Slant ('wght', 'wdth', 'ROND', 'slnt') and rounds the clock while
// the date line under it stays plain. The launcher has one glyph of that kind — the date numeral
// on the clock widget — and follows the same treatment.
//
// Naming the family by its device name is what makes the axes reachable at all; "sans-serif"
// resolves to the very font FontFamily.SansSerif does, and an axis the device font has no fvar
// entry for is dropped, so this is a plain cut everywhere except where Google Sans Flex is.
//
// Trap: setting any axis resets every axis left unset to the font's own default and ignores what
// fonts.xml pinned, so the weight and the width have to be spelled out beside the roundness even
// though neither of them is what this is for.
@Composable
fun rememberRoundedDisplayFamily(weight: FontWeight, roundness: Float): FontFamily =
    remember(weight, roundness) {
        FontFamily(
            Font(
                familyName = DeviceFontFamilyName(SYSTEM_SANS_FAMILY),
                weight = weight,
                variationSettings = FontVariation.Settings(
                    weight = weight,
                    style = FontStyle.Normal,
                    FontVariation.width(NORMAL_WIDTH),
                    FontVariation.Setting(ROUNDNESS_AXIS, roundness),
                ),
            ),
        )
    }

private const val SYSTEM_SANS_FAMILY = "sans-serif"
private const val ROUNDNESS_AXIS = "ROND"
private const val NORMAL_WIDTH = 100f
