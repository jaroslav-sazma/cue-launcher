package app.cue.launcher.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DrawerRowTargetsTest {

    @Test
    fun `the query is encoded into the address`() {
        assertEquals("${WEB_SEARCH_URL}kdy+jede+vlak", webSearchUrl("kdy jede vlak"))
    }

    @Test
    fun `diacritics and special characters are encoded`() {
        val url = webSearchUrl("šíp & kůň")

        assertTrue(url.startsWith(WEB_SEARCH_URL))
        assertEquals("${WEB_SEARCH_URL}%C5%A1%C3%ADp+%26+k%C5%AF%C5%88", url)
    }

    @Test
    fun `leading and trailing spaces are trimmed`() {
        assertEquals("${WEB_SEARCH_URL}mapy", webSearchUrl("  mapy  "))
    }
}
