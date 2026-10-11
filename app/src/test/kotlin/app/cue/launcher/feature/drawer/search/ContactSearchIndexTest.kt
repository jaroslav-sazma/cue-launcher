package app.cue.launcher.feature.drawer.search

import app.cue.launcher.data.contacts.Contact
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.text.Collator
import java.util.Locale

class ContactSearchIndexTest {

    private val index = contactSearchIndexOf(
        contacts = listOf(
            contact(id = 1, name = "Jiří Novák"),
            contact(id = 2, name = "Jiřina Svobodová"),
            contact(id = 3, name = "Zdeněk Malý", starred = true),
            contact(id = 4, name = "Čeněk Dvořák"),
            contact(id = 5, name = "Marie Nováková"),
        ),
        collator = Collator.getInstance(Locale.forLanguageTag("cs")),
    )

    @Nested
    @DisplayName("What the query finds")
    inner class Hits {

        @Test
        fun `a query without diacritics finds a name with diacritics`() {
            assertEquals(listOf("Jiřina Svobodová", "Jiří Novák"), names("jir"))
        }

        @Test
        fun `the surname is the second word of the name`() {
            val results = index.search("nov")

            assertEquals(
                listOf("Jiří Novák", "Marie Nováková", "Jiřina Svobodová"),
                results.map { it.contact.displayName },
            )
            assertEquals(MatchQuality.WORD_PREFIX, results.first().quality)
            assertEquals(MatchQuality.SUBSEQUENCE, results.last().quality)
        }

        @Test
        fun `a typo is forgiven the same way as for apps`() {
            assertEquals(listOf("Zdeněk Malý"), names("zdenke"))
        }

        @Test
        fun `an empty query returns nothing`() {
            assertTrue(index.search("").isEmpty())
            assertTrue(index.search("   ").isEmpty())
        }

        @Test
        fun `what doesn't match is not found`() {
            assertTrue(index.search("xyz").isEmpty())
        }
    }

    @Nested
    @DisplayName("Result order")
    inner class Ranking {

        @Test
        fun `match quality decides`() {
            val results = index.search("jirina")

            assertEquals(listOf("Jiřina Svobodová", "Jiří Novák"), results.map { it.contact.displayName })
            assertEquals(MatchQuality.PREFIX, results.first().quality)
            assertEquals(MatchQuality.SUBSEQUENCE, results.last().quality)
        }

        @Test
        fun `on a tie, starred status decides`() {
            val tie = contactSearchIndexOf(
                contacts = listOf(
                    contact(id = 1, name = "Alena Malá"),
                    contact(id = 2, name = "Alexandr Malý", starred = true),
                ),
                collator = Collator.getInstance(Locale.forLanguageTag("cs")),
            )

            assertEquals(listOf("Alexandr Malý", "Alena Malá"), tie.search("al").map { it.contact.displayName })
        }

        @Test
        fun `finally the Czech alphabet decides`() {
            val alphabet = contactSearchIndexOf(
                contacts = listOf(
                    contact(id = 1, name = "Dana Dvořáková"),
                    contact(id = 2, name = "Čeněk Dvořák"),
                    contact(id = 3, name = "Cyril Dvořák"),
                ),
                collator = Collator.getInstance(Locale.forLanguageTag("cs")),
            )

            assertEquals(
                listOf("Cyril Dvořák", "Čeněk Dvořák", "Dana Dvořáková"),
                alphabet.search("dvor").map { it.contact.displayName },
            )
        }
    }

    private fun names(query: String): List<String> = index.search(query).map { it.contact.displayName }

    private fun contact(id: Long, name: String, starred: Boolean = false) = Contact(
        id = id,
        lookupUri = "content://com.android.contacts/contacts/lookup/$id",
        displayName = name,
        phoneNumber = "+420 776 210 118",
        starred = starred,
    )
}
