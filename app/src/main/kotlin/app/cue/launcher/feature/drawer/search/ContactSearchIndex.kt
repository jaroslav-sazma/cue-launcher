package app.cue.launcher.feature.drawer.search

import app.cue.launcher.data.contacts.Contact
import java.text.Collator

data class ContactSearchResult(val contact: Contact, val quality: MatchQuality) {

    val score: Double get() = quality.score
}

class ContactSearchIndex internal constructor(
    private val entries: List<Entry>,
    collator: Collator,
) {

    private val ranking: Comparator<ContactSearchResult> =
        compareByDescending<ContactSearchResult> { it.score }
            .thenByDescending { it.contact.starred }
            .then(
                Comparator { first, second ->
                    collator.compare(first.contact.displayName, second.contact.displayName)
                },
            )

    fun search(query: String): List<ContactSearchResult> {
        val normalized = normalizeForSearch(query)
        if (normalized.isEmpty()) return emptyList()
        return entries.mapNotNull { it.match(normalized) }.sortedWith(ranking)
    }

    internal class Entry(private val contact: Contact, private val name: SearchableText) {

        fun match(query: String): ContactSearchResult? =
            FuzzyMatch.quality(name, query)?.let { ContactSearchResult(contact, it) }
    }

    companion object {
        val EMPTY = ContactSearchIndex(entries = emptyList(), collator = Collator.getInstance())
    }
}

fun contactSearchIndexOf(
    contacts: List<Contact>,
    collator: Collator = Collator.getInstance(),
): ContactSearchIndex = ContactSearchIndex(
    entries = contacts.map { ContactSearchIndex.Entry(it, SearchableText.of(it.displayName)) },
    collator = collator,
)
