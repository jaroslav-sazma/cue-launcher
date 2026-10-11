package app.cue.launcher.data.ha

import java.security.SecureRandom

// The companion app splits intent_extras on "," and ":", so neither may appear in the secret. The
// alphabet also drops the characters that get misread when the user retypes it by hand.
private const val SECRET_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ"

private const val SECRET_ALPHABET = SECRET_LETTERS + "23456789"

const val HA_EVENT_SECRET_LENGTH = 16

// The first character is a letter so that the secret can never come out as digits only — see the
// DIGITS error below for what a companion app does with such a value.
fun haGenerateEventSecret(random: SecureRandom = SecureRandom()): String = buildString {
    append(SECRET_LETTERS[random.nextInt(SECRET_LETTERS.length)])
    repeat(HA_EVENT_SECRET_LENGTH - 1) {
        append(SECRET_ALPHABET[random.nextInt(SECRET_ALPHABET.length)])
    }
}

enum class HaSecretError {
    SHORT,

    SEPARATOR,

    // Anything outside printable ASCII either breaks the YAML the secret is uploaded in or, like U+0085,
    // gets folded into a space on the way and silently stops matching.
    CHARACTERS,

    // A value made of digits only is read as a number by the companion app, and one that does not fit
    // in an Int throws there — the whole broadcast is dropped with nothing but a toast, so not even
    // the code arrives. One letter is enough to keep the secret a string all the way.
    DIGITS,
}

// The field is editable so a reinstall or a new phone can take over the secret the user's existing
// automations already carry, instead of forcing every blueprint to be rewritten.
fun haEventSecretError(raw: String): HaSecretError? {
    val secret = raw.trim()
    return when {
        secret.any { it == ',' || it == ':' || it.isWhitespace() } -> HaSecretError.SEPARATOR
        secret.any { it !in PRINTABLE_ASCII } -> HaSecretError.CHARACTERS
        secret.length < HA_EVENT_SECRET_MIN_LENGTH -> HaSecretError.SHORT
        secret.all(Char::isDigit) -> HaSecretError.DIGITS
        else -> null
    }
}

const val HA_EVENT_SECRET_MIN_LENGTH = 8

private val PRINTABLE_ASCII = '!'..'~'
