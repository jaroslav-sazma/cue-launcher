package app.cue.launcher.data.ha

// What a Home Assistant instance publishes over mDNS (_home-assistant._tcp): the uuid identifies it
// across networks, and internal_url / external_url are the addresses the user already configured in
// Home Assistant itself, so both fields of the connection can be filled in without typing.
data class HaInstance(
    val serviceName: String = "",
    val uuid: String = "",
    val name: String = "",
    val version: String = "",
    val internalUrl: String = "",
    val externalUrl: String = "",
    val host: String = "",
    val port: Int = 0,
) {

    // Every field here comes out of an mDNS record, which anything on the network can publish. Both
    // addresses therefore go through the same validation as an address the user types in: a discovered
    // instance cannot offer one that would not be allowed to be saved — cleartext to a public host
    // above all, which no amount of being at home could protect.
    val homeUrl: String get() = offered(internalUrl, external = false) ?: mdnsUrl.orEmpty()

    val awayUrl: String get() = offered(externalUrl, external = true).orEmpty()

    val label: String get() = name.ifBlank { serviceName }

    // An IPv6 literal is left alone rather than assembled into an address the rest of the launcher
    // cannot parse: with no internal_url in the record the user types the address in.
    private val mdnsUrl: String?
        get() = host.takeIf { it.isNotBlank() && port > 0 && !it.contains(':') }
            ?.let { offered("http://$it:$port", external = false) }

    private fun offered(raw: String, external: Boolean): String? =
        normalizeHaUrl(raw).takeIf { it.isNotEmpty() && haUrlError(it, external) == null }
}

const val HA_SERVICE_TYPE = "_home-assistant._tcp"

const val HA_TXT_UUID = "uuid"

const val HA_TXT_NAME = "location_name"

const val HA_TXT_VERSION = "version"

const val HA_TXT_INTERNAL_URL = "internal_url"

const val HA_TXT_EXTERNAL_URL = "external_url"

fun haInstanceOf(
    serviceName: String,
    attributes: Map<String, ByteArray?>,
    host: String,
    port: Int,
): HaInstance {
    val text = attributes.mapValues { (_, value) -> value?.toString(Charsets.UTF_8)?.trim().orEmpty() }
    return HaInstance(
        serviceName = serviceName.trim(),
        uuid = text[HA_TXT_UUID].orEmpty(),
        name = text[HA_TXT_NAME].orEmpty(),
        version = text[HA_TXT_VERSION].orEmpty(),
        internalUrl = text[HA_TXT_INTERNAL_URL].orEmpty(),
        externalUrl = text[HA_TXT_EXTERNAL_URL].orEmpty(),
        host = host.trim().trimEnd('.'),
        port = port,
    )
}
