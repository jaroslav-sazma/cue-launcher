package app.cue.launcher.feature.drawer.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class FuzzyMatchTest {

    @Nested
    @DisplayName("Kvalita shody")
    inner class Quality {

        @Test
        fun `the whole label is an exact match`() {
            assertEquals(MatchQuality.EXACT, quality("Chrome", "chrome"))
        }

        @Test
        fun `the start of the label`() {
            assertEquals(MatchQuality.PREFIX, quality("Chrome", "chro"))
        }

        @Test
        fun `the start of the next word`() {
            assertEquals(MatchQuality.WORD_PREFIX, quality("Google Mapy", "mapy"))
        }

        @Test
        fun `query inside a word`() {
            assertEquals(MatchQuality.SUBSTRING, quality("Gmail", "mail"))
        }

        @Test
        fun `letters scattered but in the correct order`() {
            assertEquals(MatchQuality.SUBSEQUENCE, quality("Google Mapy", "gmp"))
        }

        @Test
        fun `what is not in the list does not match`() {
            assertNull(quality("Chrome", "firefox"))
        }

        @Test
        fun `an empty query matches nothing`() {
            assertNull(quality("Chrome", ""))
        }

        @Test
        fun `a single letter matches inside a label too, just less well`() {
            assertEquals(MatchQuality.PREFIX, quality("Chrome", "c"))
            assertEquals(MatchQuality.WORD_PREFIX, quality("Google Mapy", "m"))
            assertEquals(MatchQuality.SUBSTRING, quality("Gmail", "a"))
            assertNull(quality("Gmail", "x"))
        }

        @Test
        fun `fuzzy matching is disabled for package names so it does not match anything`() {
            val packageName = SearchableText.of("com.google.android.calendar")

            assertEquals(
                MatchQuality.WORD_PREFIX,
                FuzzyMatch.quality(packageName, "calen", allowFuzzy = false),
            )

            assertNull(FuzzyMatch.quality(packageName, "cgac", allowFuzzy = false))
            assertEquals(
                MatchQuality.SUBSEQUENCE,
                FuzzyMatch.quality(packageName, "cgac", allowFuzzy = true),
            )
        }
    }

    @Nested
    @DisplayName("Typo tolerance")
    inner class Typos {

        @Test
        fun `swapped letters are forgiven`() {
            assertEquals(MatchQuality.TYPO, quality("Chrome", "chrmoe"))
        }

        @Test
        fun `a missing or an extra letter is forgiven`() {
            assertEquals(MatchQuality.TYPO, quality("Kalendář", "kalndar"))
            assertEquals(MatchQuality.TYPO, quality("Kalendář", "kalenndar"))
        }

        @Test
        fun `a typo in a partial query still matches a longer label`() {
            assertEquals(MatchQuality.TYPO, quality("Chrome Beta", "chrmo"))
        }

        @Test
        fun `a typo is tolerated even in a word in the middle of a label`() {
            assertEquals(MatchQuality.TYPO, quality("Google Mapy", "mpay"))
        }

        @Test
        fun `a query shorter than four characters is not tolerated`() {
            assertEquals(0, FuzzyMatch.typoAllowance("nap".length))
            assertNull(quality("Mapy", "nap"))
        }

        @Test
        fun `a long query allows two typos, a shorter one only one`() {
            assertEquals(1, FuzzyMatch.typoAllowance(SHORT_QUERY))
            assertEquals(2, FuzzyMatch.typoAllowance(SHORT_QUERY + 1))
            assertEquals(MatchQuality.TYPO, quality("Kalendář", "kalxndxr"))
            assertNull(quality("Kalendář", "kalxndx"))
        }
    }

    @Nested
    @DisplayName("Edit distance from the start of the candidate")
    inner class Distance {

        @Test
        fun `a matching start costs zero, the rest of the candidate does not matter`() {
            assertEquals(0, FuzzyMatch.prefixDistance("chrome", "chrome", limit = 2))
            assertEquals(0, FuzzyMatch.prefixDistance("chrome", "chrome beta", limit = 2))
        }

        @Test
        fun `swapping neighbors is one edit, not two`() {
            assertEquals(1, FuzzyMatch.prefixDistance("chrmoe", "chrome", limit = 2))
        }

        @Test
        fun `one insertion, one deletion, one substitution`() {
            assertEquals(1, FuzzyMatch.prefixDistance("mappy", "mapy", limit = 2))
            assertEquals(1, FuzzyMatch.prefixDistance("may", "mapy", limit = 2))
            assertEquals(1, FuzzyMatch.prefixDistance("mepy", "mapy", limit = 2))
        }

        @Test
        fun `an empty query matches an empty prefix`() {
            assertEquals(0, FuzzyMatch.prefixDistance("", "mapy", limit = 2))
        }

        @Test
        fun `a candidate shorter than the query costs the missing characters`() {
            assertEquals(2, FuzzyMatch.prefixDistance("mapy", "ma", limit = 2))
        }

        @Test
        fun `going over the limit returns limit plus one`() {
            assertEquals(3, FuzzyMatch.prefixDistance("chrome", "firefox", limit = 2))
            assertEquals(1, FuzzyMatch.prefixDistance("chrome", "firefox", limit = 0))
        }
    }

    @Nested
    @DisplayName("Podposloupnost")
    inner class Subsequence {

        @Test
        fun `letters in the correct order pass`() {
            assertTrue(FuzzyMatch.isSubsequence("gmp", "google mapy"))
        }

        @Test
        fun `wrong order does not pass`() {
            assertFalse(FuzzyMatch.isSubsequence("pmg", "google mapy"))
        }

        @Test
        fun `a missing letter does not pass`() {
            assertFalse(FuzzyMatch.isSubsequence("gmx", "google mapy"))
        }

        @Test
        fun `an empty query is a subsequence of anything`() {
            assertTrue(FuzzyMatch.isSubsequence("", "google mapy"))
        }
    }

    private fun quality(label: String, query: String): MatchQuality? =
        FuzzyMatch.quality(SearchableText.of(label), query)

    private companion object {

        const val SHORT_QUERY = 7
    }
}
