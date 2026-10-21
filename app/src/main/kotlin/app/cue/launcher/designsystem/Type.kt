package app.cue.launcher.designsystem

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

// A TextStyle built with the TextStyle(...) constructor silently opts out of the Material
// defaults the theme installs (no font padding, centred line height), so the launcher surface
// composes its token type onto the inherited style instead of starting from scratch.
//
// The launcher surface has no roles to mark emphasis by, so it takes Pixel's visible rule: what
// is set semibold or heavier is rounded.
@Composable
@ReadOnlyComposable
fun TypeSpec.toTextStyle(color: Color = Color.Unspecified): TextStyle =
    LocalTextStyle.current.merge(
        TextStyle(
            color = color,
            fontSize = size,
            fontWeight = weight,
            fontFamily = brandFontFamily(size, emphasized = weight >= FontWeight.SemiBold),
            letterSpacing = letterSpacing,
            lineHeight = lineHeight,
        ),
    )

// Pixel's emphasized roles are the baseline ones a weight step heavier and rounded. Material's own
// emphasized tokens jump to Bold instead, and Typography() resolves to the 15-style constructor,
// whose emphasized styles are the baseline ones — so each is derived from its baseline twin here.
fun brandTypography(base: Typography = Typography()): Typography = with(base) {
    copy(
        displayLarge = displayLarge.onBrand(),
        displayMedium = displayMedium.onBrand(),
        displaySmall = displaySmall.onBrand(),
        headlineLarge = headlineLarge.onBrand(),
        headlineMedium = headlineMedium.onBrand(),
        headlineSmall = headlineSmall.onBrand(),
        titleLarge = titleLarge.onBrand(),
        titleMedium = titleMedium.onBrand(),
        titleSmall = titleSmall.onBrand(),
        bodyLarge = bodyLarge.onBrand(),
        bodyMedium = bodyMedium.onBrand(),
        bodySmall = bodySmall.onBrand(),
        labelLarge = labelLarge.onBrand(),
        labelMedium = labelMedium.onBrand(),
        labelSmall = labelSmall.onBrand(),
        displayLargeEmphasized = displayLarge.emphasizedOnBrand(),
        displayMediumEmphasized = displayMedium.emphasizedOnBrand(),
        displaySmallEmphasized = displaySmall.emphasizedOnBrand(),
        headlineLargeEmphasized = headlineLarge.emphasizedOnBrand(),
        headlineMediumEmphasized = headlineMedium.emphasizedOnBrand(),
        headlineSmallEmphasized = headlineSmall.emphasizedOnBrand(),
        titleLargeEmphasized = titleLarge.emphasizedOnBrand(),
        titleMediumEmphasized = titleMedium.emphasizedOnBrand(),
        titleSmallEmphasized = titleSmall.emphasizedOnBrand(),
        bodyLargeEmphasized = bodyLarge.emphasizedOnBrand(),
        bodyMediumEmphasized = bodyMedium.emphasizedOnBrand(),
        bodySmallEmphasized = bodySmall.emphasizedOnBrand(),
        labelLargeEmphasized = labelLarge.emphasizedOnBrand(),
        labelMediumEmphasized = labelMedium.emphasizedOnBrand(),
        labelSmallEmphasized = labelSmall.emphasizedOnBrand(),
    )
}

private fun TextStyle.onBrand(): TextStyle =
    copy(fontFamily = brandFontFamily(fontSize, emphasized = false))

private fun TextStyle.emphasizedOnBrand(): TextStyle = copy(
    fontWeight = FontWeight(
        ((fontWeight ?: FontWeight.Normal).weight + EMPHASIS_WEIGHT_STEP).coerceAtMost(MAX_WEIGHT),
    ),
    fontFamily = brandFontFamily(fontSize, emphasized = true),
)

// The phone's UI is not drawn in "sans-serif" everywhere: a Pixel keeps Roboto there and draws its
// own UI in the product family google-sans-flex. Every family therefore lists that one first and
// sans-serif after it, each in all nine weights and both styles, so a weight override lands on a
// real cut and nothing is synthesised.
//
// Trap: a DeviceFontFamilyName font is OptionalLocal. Where the family is missing, the platform
// hands back the default typeface, which Compose recognises and reports as absent, and the matcher
// moves on to the sans-serif font of the same weight and style
// (PlatformTypefacesApi28.loadNamedFromTypefaceCacheOrNull, FontListFontFamilyTypefaceAdapter).
fun brandFontFamily(size: TextUnit, emphasized: Boolean): FontFamily =
    brandFamilies.computeIfAbsent(BrandKey(opticalSizeOf(size), emphasized)) { key ->
        FontFamily(
            BrandFace.entries.flatMap { face ->
                BRAND_WEIGHTS.flatMap { weight ->
                    BRAND_STYLES.map { style ->
                        Font(
                            familyName = DeviceFontFamilyName(face.familyName),
                            weight = weight,
                            style = style,
                            variationSettings = brandAxes(face, weight, style, key),
                        )
                    }
                }
            },
        )
    }

// Trap: setting any axis resets every axis left unset to the font's own default and ignores what
// fonts.xml pinned, so each font spells out all of them; an axis the font has no fvar entry for is
// dropped. The values are Pixel's fonts_customization.xml: opsz pinned to the role's size in sp,
// ROND 100 on the emphasized roles and 0 on the rest, wdth 100, GRAD 0, italic as slnt -10.
internal fun brandAxes(
    face: BrandFace,
    weight: FontWeight,
    style: FontStyle,
    key: BrandKey,
): FontVariation.Settings {
    val italic = style == FontStyle.Italic
    return FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.width(NORMAL_WIDTH),
        FontVariation.grade(0),
        FontVariation.Setting(ROUNDNESS_AXIS, if (key.emphasized) FULL_ROUNDNESS else 0f),
        FontVariation.Setting(OPTICAL_SIZE_AXIS, key.opticalSize.toFloat()),
        when (face) {
            BrandFace.Flex -> FontVariation.slant(if (italic) ITALIC_SLANT else 0f)
            BrandFace.System -> FontVariation.italic(if (italic) 1f else 0f)
        },
    )
}

internal enum class BrandFace(val familyName: String) {
    Flex("google-sans-flex"),
    System("sans-serif"),
}

internal data class BrandKey(val opticalSize: Int, val emphasized: Boolean)

// Whole sp, as Pixel pins it, which also keeps the cache small where a size is scaled. A size not
// in sp has nothing to pin and takes body large's, the size text inherits by default.
private fun opticalSizeOf(size: TextUnit): Int =
    if (size.isSp) size.value.roundToInt() else BODY_LARGE_SIZE

private val brandFamilies = ConcurrentHashMap<BrandKey, FontFamily>()

internal val BRAND_WEIGHTS: List<FontWeight> = (1..9).map { FontWeight(it * 100) }
internal val BRAND_STYLES: List<FontStyle> = listOf(FontStyle.Normal, FontStyle.Italic)

private const val ROUNDNESS_AXIS = "ROND"
private const val OPTICAL_SIZE_AXIS = "opsz"
private const val FULL_ROUNDNESS = 100f
private const val EMPHASIS_WEIGHT_STEP = 100
private const val MAX_WEIGHT = 1000
private const val NORMAL_WIDTH = 100f
private const val ITALIC_SLANT = -10f
private const val BODY_LARGE_SIZE = 16
