package app.cue.launcher.system

import android.content.Intent
import android.net.Uri
import android.util.Log
import java.net.URISyntaxException

// Parsing a serialized Intent means starting a target a string the user pasted decided, so what the
// intent can reach past the app it names is taken away first: permission grants, a selector, and a
// target inside the launcher. The action and the extras are left alone — they are the point of a
// deep link, and they reach no further than any other app could reach the same component.
fun deepLinkIntent(uri: String, ownPackage: String): Intent? {
    val parsed = parsedDeepLink(uri) ?: return null
    val allowed = deepLinkAllowed(parsed, ownPackage)
    if (!allowed) Log.w(DEEP_LINK_TAG, "The link points back into the launcher or at a file")
    return parsed.takeIf { allowed }?.apply {
        // A selector replaces the whole target after the checks above, so it never survives.
        selector = null
        flags = sanitizedDeepLinkFlags(flags)
    }
}

data class DeepLinkTarget(
    val component: String?,
    val packageName: String?,
    val action: String?,
    val dataScheme: String?,
    val dataHost: String?,
    val fallbackHost: String?,
    val allowed: Boolean,
)

// What deepLinkIntent would start, read through the same parser and the same checks, for showing
// a link before it is trusted. Starts nothing.
fun deepLinkTargetOf(uri: String, ownPackage: String): DeepLinkTarget? {
    val parsed = parsedDeepLink(uri) ?: return null
    return DeepLinkTarget(
        component = parsed.component?.flattenToShortString(),
        packageName = parsed.`package`,
        action = parsed.action,
        dataScheme = parsed.data?.scheme,
        dataHost = parsed.data?.host,
        fallbackHost = deepLinkFallbackUrl(parsed)?.let { url -> Uri.parse(url).host ?: url },
        allowed = deepLinkAllowed(parsed, ownPackage),
    )
}

private fun deepLinkAllowed(parsed: Intent, ownPackage: String): Boolean = deepLinkTargetAllowed(
    packageName = parsed.`package`,
    componentPackage = parsed.component?.packageName,
    dataScheme = parsed.data?.scheme,
    ownPackage = ownPackage,
)

// parseUri promises URISyntaxException, but a typed extra it cannot parse — `i.count=abc` — comes
// out of Integer.parseInt as a NumberFormatException that the parser never wraps. Catching only the
// documented one turns a mistyped link into a crashed launcher.
private fun parsedDeepLink(uri: String): Intent? = try {
    Intent.parseUri(uri, Intent.URI_INTENT_SCHEME or Intent.URI_ANDROID_APP_SCHEME)
} catch (malformed: URISyntaxException) {
    // Only the class: both messages quote the link back, and a link can carry anything.
    Log.w(DEEP_LINK_TAG, "The link is not a serialized intent: ${malformed.javaClass.simpleName}")
    null
} catch (unparsable: IllegalArgumentException) {
    Log.w(DEEP_LINK_TAG, "The link carries a value the intent parser refuses: ${unparsable.javaClass.simpleName}")
    null
}

// The convention of the intent: scheme: a link may name a web address to fall back on when the app
// it targets is not installed.
fun deepLinkFallbackUrl(intent: Intent): String? {
    val url = intent.getStringExtra(BROWSER_FALLBACK_URL) ?: return null
    val scheme = url.substringBefore(':', "").lowercase()
    return url.takeIf { scheme in WEB_SCHEMES }
}

internal fun deepLinkTargetAllowed(
    packageName: String?,
    componentPackage: String?,
    dataScheme: String?,
    ownPackage: String,
): Boolean = !dataScheme.equals(FILE_SCHEME, ignoreCase = true) &&
    packageName != ownPackage &&
    componentPackage != ownPackage

internal fun sanitizedDeepLinkFlags(flags: Int): Int = flags and DENIED_FLAGS.inv()

// Granting a URI permission would hand the target app access to data the launcher can read and the
// app cannot; no deep link has a reason to ask for that.
private const val DENIED_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

private const val BROWSER_FALLBACK_URL = "browser_fallback_url"

private val WEB_SCHEMES = setOf("http", "https")

private const val DEEP_LINK_TAG = "DeepLinkIntent"
