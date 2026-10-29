package app.cue.launcher.feature.settings

import android.content.res.AssetManager
import android.util.Log
import androidx.annotation.StringRes
import app.cue.launcher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

// The assets come from LICENSE, NOTICE and LICENSES/ in the repository root (the licenseAssets
// task in app/build.gradle.kts); LicenseNoticesTest keeps this list and those files in step.
internal enum class LicenseNotice(val asset: String, @StringRes val title: Int, @StringRes val covers: Int) {
    NOTICE("NOTICE", R.string.licenses_notice_title, R.string.licenses_notice_covers),
    GPL("LICENSE", R.string.licenses_gpl_title, R.string.licenses_gpl_covers),
    PICTOGRAMMERS(
        "LicenseRef-Pictogrammers-Free-License.txt",
        R.string.licenses_pictogrammers_title,
        R.string.licenses_pictogrammers_covers,
    ),
    APACHE("Apache-2.0.txt", R.string.licenses_apache_title, R.string.licenses_apache_covers),
    MIT("MIT.txt", R.string.licenses_mit_title, R.string.licenses_mit_covers),
    MPL("MPL-2.0.txt", R.string.licenses_mpl_title, R.string.licenses_mpl_covers),
    CC0("CC0-1.0.txt", R.string.licenses_cc0_title, R.string.licenses_cc0_covers),
}

internal const val LICENSE_ASSET_DIR = "licenses"

internal suspend fun readLicenseNotice(assets: AssetManager, notice: LicenseNotice): List<String>? =
    withContext(Dispatchers.IO) {
        try {
            reflowLicense(assets.open("$LICENSE_ASSET_DIR/${notice.asset}").bufferedReader().use { it.readText() })
        } catch (failure: IOException) {
            Log.w(TAG, "The license text ${notice.asset} cannot be read", failure)
            null
        }
    }

// The texts are hard-wrapped for an 80-column terminal, which a phone renders as ragged half lines.
// Lines of a paragraph are joined again and the dashes that underline a heading are dropped. A
// wrapped line can begin with "7. " or "(1) " mid-sentence, so a marker opens a list item only after
// a finished sentence or as a sibling in a hanging-indent list.
internal fun reflowLicense(text: String): List<String> =
    text.replace("\r\n", "\n").lines().joinToString("\n", transform = ::unbox)
        .split(BLANK_LINE)
        .mapNotNull(::reflowBlock)

// MPL-2.0 sets its warranty and liability sections in a box of asterisks.
private fun unbox(line: String): String = when {
    BOX_RULE.matches(line) -> ""
    BOX_LINE.matches(line) -> " " + line.substring(1, line.lastIndexOf('*')).trimEnd()
    else -> line
}

private fun reflowBlock(block: String): String? {
    val lines = block.lines().filter { line -> line.isNotBlank() && !line.trim().all { it == '-' || it == '=' } }
    if (lines.isEmpty()) return null
    val paragraph = StringBuilder(lines.first().trim())
    var itemStart = 0
    for (index in 1 until lines.size) {
        val ownLine = startsOwnLine(lines[index - 1], lines[index]) ||
            isSiblingItem(lines, itemStart, index)
        if (ownLine) itemStart = index
        paragraph.append(if (ownLine) '\n' else ' ').append(lines[index].trim())
    }
    return paragraph.toString()
}

private fun startsOwnLine(previous: String, line: String): Boolean {
    val before = previous.trim()
    val text = line.trim()
    return when {
        STANDALONE.containsMatchIn(before) -> true
        STANDALONE.containsMatchIn(text) -> !before.endsWith(',')
        text.startsWith("All rights reserved") -> true
        else -> LIST_ITEM.containsMatchIn(text) && SENTENCE_END.containsMatchIn(before)
    }
}

private fun isSiblingItem(lines: List<String>, itemStart: Int, index: Int): Boolean {
    val start = lines[itemStart]
    val hanging = index - 1 == itemStart || lines[index - 1].indent > start.indent
    return hanging && lines[index].indent == start.indent &&
        LIST_ITEM.containsMatchIn(start.trim()) && LIST_ITEM.containsMatchIn(lines[index].trim())
}

private val String.indent: Int get() = indexOfFirst { !it.isWhitespace() }

private val BLANK_LINE = Regex("\n\\s*\n")
private val BOX_RULE = Regex("\\s*\\*[*\\s]*")
private val BOX_LINE = Regex("\\*\\s.*\\s\\*\\s*")
private val LIST_ITEM = Regex("^([-·*#•]\\s|\\(?([0-9]{1,2}|[a-z]|[ivx]{2,4})[.)]\\s)")
private val SENTENCE_END = Regex("[.;!?][\"')\\]]*$")
private val STANDALONE = Regex("^(https?://|Copyright\\s|#\\s)")
private const val TAG = "LicenseNotices"
