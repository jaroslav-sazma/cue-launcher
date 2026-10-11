package app.cue.launcher.designsystem.glyph

class MdiGlyphs(private val codePoints: Map<String, Int>) {

    val names: Set<String> get() = codePoints.keys

    val isEmpty: Boolean get() = codePoints.isEmpty()

    fun codePointOf(name: String?): Int? = mdiGlyphName(name)?.let(codePoints::get)

    fun glyphOf(name: String?): String? = codePointOf(name)?.let { String(Character.toChars(it)) }

    companion object {

        val EMPTY = MdiGlyphs(emptyMap())
    }
}

fun mdiGlyphName(raw: String?): String? {
    val name = raw?.trim()?.lowercase()?.removePrefix(MDI_NAME_PREFIX)?.removePrefix(MDI_CLASS_PREFIX)
    return name?.takeIf { it.isNotEmpty() }
}

fun mdiIconReference(name: String): String = MDI_NAME_PREFIX + name

fun parseMdiGlyphs(lines: Sequence<String>): MdiGlyphs {
    val codePoints = HashMap<String, Int>(EXPECTED_GLYPH_COUNT)
    lines.forEach { line ->
        val separator = line.indexOf(' ')
        if (line.startsWith(COMMENT_PREFIX) || separator <= 0) return@forEach
        val name = mdiGlyphName(line.substring(0, separator)) ?: return@forEach
        val codePoint = line.substring(separator + 1).trim().toIntOrNull(HEX_RADIX) ?: return@forEach
        codePoints[name] = codePoint
    }
    return MdiGlyphs(codePoints)
}

private const val MDI_NAME_PREFIX = "mdi:"
private const val MDI_CLASS_PREFIX = "mdi-"
private const val COMMENT_PREFIX = "#"
private const val HEX_RADIX = 16

private const val EXPECTED_GLYPH_COUNT = 8192
