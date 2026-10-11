package app.cue.launcher.feature.drawer.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SearchTextTest {

    @Test
    fun `diacritics disappear from a Czech label`() {
        assertEquals("ukoly", normalizeForSearch("Úkoly"))
        assertEquals("zpravy", normalizeForSearch("Zprávy"))
        assertEquals("nastaveni", normalizeForSearch("Nastavení"))
    }

    @Test
    fun `compound marks (r with a caron, u with a ring) are decomposed too`() {
        assertEquals("prehravac hudby", normalizeForSearch("Přehrávač hudby"))
        assertEquals("uctenky", normalizeForSearch("Účtenky"))
    }

    @Test
    fun `letter case and surrounding spaces don't matter`() {
        assertEquals("chrome", normalizeForSearch("  CHRoMe "))
    }

    @Test
    fun `already normalized text passes through unchanged`() {
        val once = normalizeForSearch("Kalendář Google")
        assertEquals(once, normalizeForSearch(once))
    }

    @Test
    fun `a package name splits into words, the dot is a separator`() {
        assertEquals(
            listOf("com", "google", "android", "calendar"),
            searchWords("com.google.android.calendar"),
        )
    }

    @Test
    fun `a hyphen, underscore, and multiple spaces split words too`() {
        assertEquals(listOf("my", "app", "2", "beta"), searchWords("my-app_2   beta"))
    }
}
