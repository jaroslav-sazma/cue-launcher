package app.cue.launcher.data.scenario

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

@Serializable
enum class AccentSource {
    @SerialName("primary")
    PRIMARY,

    @SerialName("tertiary")
    TERTIARY,

    @SerialName("custom")
    CUSTOM,
}

private const val RGB_MASK = 0xFFFFFF
private const val HEX_RADIX = 16
private const val HEX_DIGITS = 6
private val ACCENT_HEX = Regex("#?([0-9A-Fa-f]{$HEX_DIGITS})")

fun parseAccentHex(text: String): Int? =
    ACCENT_HEX.matchEntire(text.trim())?.groupValues?.get(1)?.toInt(HEX_RADIX)

fun accentHexOf(rgb: Int): String =
    "#" + accentRgbOf(rgb).toString(HEX_RADIX).padStart(HEX_DIGITS, '0').uppercase()

fun accentRgbOf(argb: Int): Int = argb and RGB_MASK

// A custom accent without a colour has nothing to show, so it counts as the system's.
fun effectiveAccentOf(source: AccentSource, customRgb: Int?): AccentSource =
    if (source == AccentSource.CUSTOM && customRgb == null) AccentSource.PRIMARY else source

// A value this build does not know, say one a newer build added, reads as the system accent
// instead of making the whole configuration unreadable.
object AccentSources : JsonTransformingSerializer<AccentSource>(AccentSource.serializer()) {

    private val KNOWN = AccentSource.entries.map { AccentSource.serializer().descriptor.getElementName(it.ordinal) }
    private val FALLBACK = JsonPrimitive(KNOWN[AccentSource.PRIMARY.ordinal])

    override fun transformDeserialize(element: JsonElement): JsonElement {
        val name = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
        return if (name in KNOWN) element else FALLBACK
    }
}

// Anything but a string reads as no colour instead of making the whole configuration unreadable.
object AccentHexes : JsonTransformingSerializer<String?>(String.serializer().nullable) {

    override fun transformDeserialize(element: JsonElement): JsonElement =
        if ((element as? JsonPrimitive)?.isString == true) element else JsonNull
}
