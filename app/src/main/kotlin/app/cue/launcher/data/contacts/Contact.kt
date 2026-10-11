package app.cue.launcher.data.contacts

import java.util.Locale

data class Contact(
    val id: Long,
    val lookupUri: String,
    val displayName: String,
    val phoneNumber: String,
    val starred: Boolean = false,
)

fun contactInitials(displayName: String): String {
    val words = displayName.split(WHITESPACE)
        .mapNotNull { word -> word.firstOrNull()?.takeIf(Char::isLetterOrDigit) }
    return when {
        words.isEmpty() -> ""
        words.size == 1 -> words.first().toString()
        else -> "${words.first()}${words.last()}"
    }.uppercase(Locale.ROOT)
}

private val WHITESPACE = Regex("\\s+")
