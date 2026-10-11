package app.cue.launcher.feature.scenarios

import java.net.URLEncoder

internal const val REPOSITORY_URL = "https://github.com/jaroslav-sazma/cue-launcher"

internal enum class InstallSource { GOOGLE_PLAY, GITHUB, DEBUG_BUILD }

internal fun installSource(installer: String?, debug: Boolean): InstallSource = when {
    debug -> InstallSource.DEBUG_BUILD
    installer == PLAY_STORE_PACKAGE -> InstallSource.GOOGLE_PLAY
    else -> InstallSource.GITHUB
}

// The issue form is in English and these values are data for the maintainer, so they are not
// localized.
internal data class BugReportFacts(val device: String, val android: String, val version: String)

internal fun deviceName(manufacturer: String, model: String): String =
    if (model.startsWith(manufacturer.trim(), ignoreCase = true)) model else "${manufacturer.trim()} $model"

internal fun androidBuild(release: String, buildNumber: String): String = "Android $release, $buildNumber"

internal fun appVersion(versionName: String, versionCode: Int, source: InstallSource): String {
    val origin = when (source) {
        InstallSource.GOOGLE_PLAY -> "from Google Play"
        InstallSource.GITHUB -> "from GitHub"
        InstallSource.DEBUG_BUILD -> "built from source"
    }
    return "$versionName ($versionCode) $origin"
}

// The names are the field ids of .github/ISSUE_TEMPLATE/bug_report.yml; GitHub fills a form field
// from the query parameter of the same name.
internal fun bugReportParameters(facts: BugReportFacts): List<Pair<String, String>> = listOf(
    TEMPLATE_PARAMETER to BUG_REPORT_TEMPLATE,
    "device" to facts.device,
    "android" to facts.android,
    "version" to facts.version,
)

internal fun bugReportUrl(facts: BugReportFacts): String = bugReportParameters(facts)
    .joinToString(separator = "&", prefix = "$REPOSITORY_URL/issues/new?") { (name, value) ->
        "$name=${queryEncode(value)}"
    }

// URLEncoder writes a space as '+', which only form decoding reads back as a space.
private fun queryEncode(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

internal const val TEMPLATE_PARAMETER = "template"

internal const val BUG_REPORT_TEMPLATE = "bug_report.yml"

private const val PLAY_STORE_PACKAGE = "com.android.vending"
