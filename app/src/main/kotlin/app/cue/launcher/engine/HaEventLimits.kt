package app.cue.launcher.engine

// The broadcast is open to every app on the device, so nothing it carries may cost more than these:
// a code ends up in the log and in the database, a field in the tile template.
const val HA_CODE_MAX_LENGTH = 64

const val HA_FIELDS_MAX = 32

const val HA_VALUE_MAX_LENGTH = 1024

private const val HA_KEY_MAX_LENGTH = 64

private const val HA_ICON_MAX_LENGTH = 64

fun haEventCode(raw: String?): String? =
    raw?.trim()?.take(HA_CODE_MAX_LENGTH)?.trim()?.takeIf(String::isNotEmpty)

// The keys worth reading once the secret has been accepted, in a stable order so that a sender with
// too many fields loses the same ones every time. The secret itself is read on its own, first.
fun haEventKeys(keys: Collection<String>): List<String> {
    val reserved = keys.filter { it in HA_RESERVED_EXTRAS && it != HA_EXTRA_SECRET }
    return reserved.sorted() + keys.filter(::haFieldKey).sorted().take(HA_FIELDS_MAX)
}

internal fun haFieldKey(key: String): Boolean =
    key.isNotBlank() && key.length <= HA_KEY_MAX_LENGTH && key !in HA_RESERVED_EXTRAS

// An overlong icon is dropped rather than cut: the first 64 characters of a name are another glyph.
internal fun haIconFits(icon: String): Boolean = icon.isNotEmpty() && icon.length <= HA_ICON_MAX_LENGTH
