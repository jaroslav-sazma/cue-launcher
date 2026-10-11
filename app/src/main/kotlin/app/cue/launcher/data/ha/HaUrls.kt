package app.cue.launcher.data.ha

import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

fun haIsCleartext(url: String): Boolean = url.trim().startsWith("http://", ignoreCase = true)

fun normalizeHaUrl(raw: String): String = raw.trim().trimEnd('/')

// Cleartext is tolerated for one address only: a local one, where the whole protection is that the
// launcher can tell it is on the right network. A public http:// host cannot be protected that way —
// the credential would cross the internet in the clear even from the home network — so it is refused
// in both fields, including when an address arrives from the mDNS record of a discovered instance.
fun haUrlError(raw: String, external: Boolean): HaUrlError? {

    val url = raw.trim()
    val parts = haUrlParts(url)
    return when {
        url.isEmpty() -> null
        parts == null && HA_SCHEME_PATTERN.containsMatchIn(url) -> HaUrlError.HOST
        parts == null -> HaUrlError.SCHEME
        parts.secure -> null
        external -> HaUrlError.INSECURE_EXTERNAL
        haUrlIsLocal(url) -> null
        else -> HaUrlError.INSECURE_HOME
    }
}

// An IPv6 literal keeps its brackets, so it can never be mistaken for a single-label name.
fun haUrlHost(url: String): String? = haUrlParts(url.trim())?.host

// Android 17 blocks the local network unless ACCESS_LOCAL_NETWORK is granted or the instance was
// approved through the system picker. The host alone cannot prove where an address leads — split DNS
// resolves a public name to a LAN address — so this only answers "is it obviously local", which is
// enough to offer the permission before the first call instead of after a mysterious timeout.
//
// It is also what lets a cleartext address through, so a host only counts as local in a form the
// resolver reads the same way: the resolver takes 134744072, 0x8080808 or 010.010.010.010 for
// 8.8.8.8, so a single label has to start with a letter and an IPv4 address has to be canonical.
fun haUrlIsLocal(url: String): Boolean {
    val host = haUrlHost(url) ?: return false
    return when {
        host.startsWith('[') -> haIpIsLocal(host.removeSurrounding("[", "]"))
        HA_IPV4_PATTERN.matches(host) -> haIpIsLocal(host)
        host.endsWith(HA_MDNS_SUFFIX) -> true
        HA_LABEL_PATTERN.matches(host) -> true
        else -> false
    }
}

fun haWebsocketUrl(url: String): String = when {
    url.startsWith("https://", ignoreCase = true) -> "wss://" + url.substring("https://".length)
    url.startsWith("http://", ignoreCase = true) -> "ws://" + url.substring("http://".length)
    else -> url
} + HA_WEBSOCKET_PATH

// The JDK already knows the RFC1918, loopback and link-local ranges; carrier-grade NAT (which a
// Tailscale address lives in) and IPv6 unique local addresses have to be spelled out. Only ever called
// with a literal, so InetAddress parses it instead of asking DNS.
private fun haIpIsLocal(host: String): Boolean = runCatching {
    val address = InetAddress.getByName(host)
    val uniqueLocal = address is Inet6Address && address.address[0].toInt() and ULA_MASK == ULA_PREFIX
    address.isSiteLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
        uniqueLocal || haIsCarrierGrade(host)
}.getOrDefault(false)

private class HaUrlParts(val secure: Boolean, val host: String)

// Parsed the way the HTTP client will parse it, and refused wherever the two could disagree: user
// info, a query or a fragment would each let "http://evil.example?.local" read as one host here and
// connect to another, and OkHttp takes a backslash for a slash where java.net.URI refuses it. An empty
// port passes java.net.URI but not OkHttp, so it is refused here too.
private fun haUrlParts(url: String): HaUrlParts? {
    val uri = url.takeIf { text -> text.none { it == '\\' || it.isWhitespace() } }
        ?.let { text -> runCatching { URI(text) }.getOrNull() }
    val scheme = uri?.scheme?.lowercase()
    val host = uri?.let(::haUriHost)
    val port = uri?.port ?: NO_PORT
    return when {
        scheme != HTTP && scheme != HTTPS -> null
        host.isNullOrEmpty() -> null
        port != NO_PORT && port !in 1..MAX_PORT -> null
        else -> HaUrlParts(secure = scheme == HTTPS, host = host)
    }
}

// java.net.URI gives no host for a name it does not take for a server — one with an underscore, or a
// non-ASCII one — while OkHttp connects to both. Such an authority is read here by hand, with the
// name in the ASCII form the resolver will see, and anything else in it (user info, an empty or a
// non-numeric port) is refused.
private fun haUriHost(uri: URI): String? {
    val authority = uri.rawAuthority?.takeUnless { it.endsWith(':') }
    val clean = uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null
    val registry = authority?.takeIf { clean && uri.host == null }?.let(HA_REGISTRY_AUTHORITY::matchEntire)
    val name = registry?.let { runCatching { IDN.toASCII(it.groupValues[1]).lowercase() }.getOrNull() }
    val port = registry?.groupValues?.get(2).orEmpty()
    return when {
        !clean || authority == null -> null
        uri.host != null -> uri.host.lowercase()
        name == null || !HA_REGISTRY_HOST.matches(name) -> null
        port.isNotEmpty() && port.toInt() !in 1..MAX_PORT -> null
        else -> name
    }
}

private fun haIsCarrierGrade(host: String): Boolean {
    val parts = host.split('.').mapNotNull(String::toIntOrNull)
    return parts.size == IPV4_PARTS && parts[0] == CGNAT_PREFIX && parts[1] in CGNAT_FIRST..CGNAT_LAST
}

private const val HA_MDNS_SUFFIX = ".local"

private const val IPV4_PARTS = 4

private const val CGNAT_PREFIX = 100

private const val CGNAT_FIRST = 64

private const val CGNAT_LAST = 127

private const val HTTP = "http"

private const val HTTPS = "https"

private const val NO_PORT = -1

private const val MAX_PORT = 65_535

private const val ULA_MASK = 0xFE

private const val ULA_PREFIX = 0xFC

private const val IPV4_OCTET = """(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)"""

private val HA_IPV4_PATTERN = Regex("""(?:$IPV4_OCTET\.){3}$IPV4_OCTET""")

private val HA_LABEL_PATTERN = Regex("""[a-z][a-z0-9_-]*""")

private val HA_REGISTRY_AUTHORITY = Regex("""([^:@\[\]]+)(?::(\d{1,5}))?""")

private val HA_REGISTRY_HOST = Regex("""[a-z0-9_-]+(?:\.[a-z0-9_-]+)*""")

private val HA_SCHEME_PATTERN = Regex("""^https?://""", RegexOption.IGNORE_CASE)
