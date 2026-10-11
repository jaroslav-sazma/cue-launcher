package app.cue.launcher.feature.drawer.search

import app.cue.launcher.data.launchlog.LaunchStats
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.system.InstalledApp
import app.cue.launcher.system.SearchableApp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.text.Collator
import java.util.Locale

class AppSearchIndexTest {

    private val index = appSearchIndexOf(
        apps = listOf(
            app(label = "Úkoly", packageName = "com.google.android.apps.tasks", english = "Tasks"),
            app(label = "Kalendář", packageName = "com.google.android.calendar", english = "Calendar"),
            app(label = "Chrome", packageName = "com.android.chrome", english = "Chrome"),
            app(label = "Google Mapy", packageName = "com.google.android.apps.maps", english = "Maps"),
            app(label = "Gmail", packageName = "com.google.android.gm", english = "Gmail"),
            app(label = "Zprávy", packageName = "com.google.android.apps.messaging", english = "Messages"),
            app(label = "Nastavení", packageName = "com.android.settings", english = "Settings"),
            app(label = "Hodiny", packageName = "com.google.android.deskclock", english = "Clock"),
        ),
        collator = Collator.getInstance(Locale.forLanguageTag("cs")),
    )

    @Nested
    @DisplayName("What the query finds")
    inner class Hits {

        @Test
        fun `a query without diacritics finds a diacritic label`() {
            assertEquals("Úkoly", bestLabel("uk"))
            assertEquals("Úkoly", bestLabel("úk"))
            assertEquals("Zprávy", bestLabel("zpra"))
        }

        @Test
        fun `an English label finds the Czech app`() {
            val best = index.bestMatch("calen")

            assertEquals("Kalendář", best?.app?.label)
            assertEquals(SearchField.ENGLISH_LABEL, best?.matchedField)
        }

        @Test
        fun `package name is the third field when labels do not match`() {
            val best = index.bestMatch("deskclock")

            assertEquals("Hodiny", best?.app?.label)
            assertEquals(SearchField.PACKAGE, best?.matchedField)
        }

        @Test
        fun `a typo in the query does not discard the result`() {
            assertEquals("Chrome", bestLabel("chrmoe"))
            assertEquals("Nastavení", bestLabel("nastvaeni"))
        }

        @Test
        fun `an empty query returns nothing, Vault is a separate screen`() {
            assertTrue(index.search("").isEmpty())
            assertTrue(index.search("   ").isEmpty())
            assertNull(index.bestMatch(""))
        }

        @Test
        fun `a nonsense query returns nothing`() {
            assertTrue(index.search("qwertz").isEmpty())
        }

        @Test
        fun `an empty index survives searching`() {
            assertTrue(AppSearchIndex.EMPTY.search("chrome").isEmpty())
        }
    }

    @Nested
    @DisplayName("Result order")
    inner class Ranking {

        @Test
        fun `a word start beats a match inside and a subsequence`() {
            val labels = index.search("ma").map { it.app.label }

            assertEquals(listOf("Google Mapy", "Gmail", "Zprávy"), labels)
        }

        @Test
        fun `a localized label takes priority over English and over package`() {
            val best = index.bestMatch("zpra")

            assertEquals(SearchField.LABEL, best?.matchedField)
            assertEquals(MatchQuality.PREFIX, best?.quality)
        }

        @Test
        fun `a match in the localized label beats the same match in English`() {
            val index = appSearchIndexOf(
                listOf(
                    app(label = "Hodiny", packageName = "com.google.android.deskclock", english = "Clock"),
                    app(label = "Clock", packageName = "cz.other.clock"),
                ),
            )

            assertEquals("Clock", index.bestMatch("clock")?.app?.label)
        }

        @Test
        fun `with an equal score, the shorter label wins`() {
            val index = appSearchIndexOf(
                listOf(
                    app(label = "Mapy Offline", packageName = "cz.other.maps"),
                    app(label = "Mapy", packageName = "cz.seznam.mapy"),
                ),
            )

            assertEquals(listOf("Mapy", "Mapy Offline"), index.search("map").map { it.app.label })
        }

        @Test
        fun `with equal score and length, alphabet decides, not list order`() {
            val index = appSearchIndexOf(
                listOf(
                    app(label = "Avíza", packageName = "cz.test.aviza"),
                    app(label = "Aukce", packageName = "cz.test.aukce"),
                ),
                collator = Collator.getInstance(Locale.forLanguageTag("cs")),
            )

            assertEquals(listOf("Aukce", "Avíza"), index.search("a").map { it.app.label })
        }

        @Test
        fun `an app appears once in results, even when multiple fields match`() {
            val hits = index.search("chrome").filter { it.app.label == "Chrome" }

            assertEquals(1, hits.size)
            assertEquals(SearchField.LABEL, hits.single().matchedField)
        }
    }

    @Nested
    @DisplayName("Launched from search (launch log)")
    inner class Frequency {

        private val alfa = app(label = "Mapy Alfa", packageName = "cz.alfa.mapy")
        private val beta = app(label = "Mapy Beta", packageName = "cz.beta.mapy")

        @Test
        fun `with equal score, the app launched from search more often wins`() {
            val index = index(
                stats(alfa, searchHits = 1) + stats(beta, searchHits = 4),
            )

            assertEquals(listOf("Mapy Beta", "Mapy Alfa"), labels(index))
        }

        @Test
        fun `one launch from search beats a longer history without one`() {
            val index = index(
                stats(alfa, lastLaunchedAt = 9_000L) + stats(beta, searchHits = 1, lastLaunchedAt = 1_000L),
            )

            assertEquals(listOf("Mapy Beta", "Mapy Alfa"), labels(index))
        }

        @Test
        fun `the count promotes a match inside the word, but only from two letters up`() {
            val onenote = app(label = "OneNote", packageName = "com.microsoft.office.onenote")
            val notes = app(label = "Notes", packageName = "cz.other.notes")
            val index = appSearchIndexOf(
                apps = listOf(onenote, notes),
                stats = stats(onenote, searchHits = 12),
            )

            assertEquals(MatchQuality.SUBSTRING, index.bestMatch("no")?.quality)
            assertEquals(listOf("OneNote", "Notes"), index.search("no").map { it.app.label })
            assertEquals(listOf("Notes", "OneNote"), index.search("n").map { it.app.label })
        }

        @Test
        fun `a single letter still finds the names that only contain it`() {
            val obchod = app(label = "Obchod Play", packageName = "com.android.vending")
            val fotky = app(label = "Fotky", packageName = "com.google.android.apps.photos")
            val index = appSearchIndexOf(apps = listOf(obchod, fotky))

            assertEquals(listOf("Obchod Play", "Fotky"), index.search("o").map { it.app.label })
        }

        @Test
        fun `the count does not promote a guessed match over a literal one`() {
            val internet = app(label = "Internet", packageName = "cz.other.internet")
            val notes = app(label = "Notes", packageName = "cz.other.notes")
            val index = appSearchIndexOf(
                apps = listOf(internet, notes),
                stats = stats(notes, searchHits = 40),
            )

            assertEquals(MatchQuality.SUBSEQUENCE, index.search("nte").last().quality)
            assertEquals(listOf("Internet", "Notes"), index.search("nte").map { it.app.label })
        }

        @Test
        fun `a fully typed name wins over a more often launched app`() {
            val mapy = app(label = "Mapy", packageName = "cz.seznam.mapy")
            val offline = app(label = "Mapy Offline", packageName = "cz.other.maps")
            val index = appSearchIndexOf(
                apps = listOf(mapy, offline),
                stats = stats(offline, searchHits = 40),
            )

            assertEquals("Mapy", index.bestMatch("mapy")?.app?.label)
        }

        @Test
        fun `with an equal count, the most recently launched wins, not the alphabet`() {
            val index = index(
                stats(alfa, searchHits = 2, lastLaunchedAt = 1_000L) +
                    stats(beta, searchHits = 2, lastLaunchedAt = 5_000L),
            )

            assertEquals(listOf("Mapy Beta", "Mapy Alfa"), labels(index))
        }

        @Test
        fun `without history the alphabet decides`() {
            assertEquals(listOf("Mapy Alfa", "Mapy Beta"), labels(index()))
        }

        @Test
        fun `the result carries the stats of the app it belongs to`() {
            val results = index(stats(beta, searchHits = 2, lastLaunchedAt = 5_000L))
                .search("mapy")
                .associateBy { it.app.label }

            assertEquals(5_000L, results.getValue("Mapy Beta").lastLaunchedAt)
            assertEquals(2, results.getValue("Mapy Beta").searchHits)
            assertNull(results.getValue("Mapy Alfa").lastLaunchedAt)
            assertEquals(0, results.getValue("Mapy Alfa").searchHits)
        }

        private fun stats(
            app: SearchableApp,
            searchHits: Int = 0,
            lastLaunchedAt: Long = 0L,
        ): Map<ComponentRef, LaunchStats> =
            mapOf(app.app.component to LaunchStats(lastLaunchedAt, searchHits))

        private fun index(stats: Map<ComponentRef, LaunchStats> = emptyMap()) = appSearchIndexOf(
            apps = listOf(alfa, beta),
            stats = stats,
            collator = Collator.getInstance(Locale.forLanguageTag("cs")),
        )

        private fun labels(index: AppSearchIndex) = index.search("mapy").map { it.app.label }
    }

    @Nested
    @DisplayName("Building the index")
    inner class Building {

        @Test
        fun `without an English label the index still works`() {
            val index = appSearchIndexOf(
                listOf(app(label = "Kalendář", packageName = "com.google.android.calendar")),
            )

            assertEquals("Kalendář", index.bestMatch("calendar")?.app?.label)
        }
    }

    private fun bestLabel(query: String): String? = index.bestMatch(query)?.app?.label

    private fun app(
        label: String,
        packageName: String,
        english: String? = null,
        userSerial: Long = 0,
    ) = SearchableApp(
        app = InstalledApp(
            component = ComponentRef(packageName, "$packageName.Main", userSerial),
            label = label,
        ),
        englishLabel = english,
    )
}
