package app.cue.launcher.system

import app.cue.launcher.data.scenario.TapAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SearchTargetTest {

    @Test
    fun `first the default browser search activity, then Chrome, then the browser`() {
        val targets = searchTargets("org.mozilla.firefox")

        assertEquals(
            listOf(
                SearchTarget.SearchActivity("org.mozilla.firefox"),
                SearchTarget.SearchActivity(CHROME_PACKAGE),
                SearchTarget.DefaultBrowser,
            ),
            targets,
        )
    }

    @Test
    fun `chrome as the default browser is not tried twice`() {
        assertEquals(
            listOf(SearchTarget.SearchActivity(CHROME_PACKAGE), SearchTarget.DefaultBrowser),
            searchTargets(CHROME_PACKAGE),
        )
    }

    @Test
    fun `without a default browser Chrome and the system shortcut remain`() {
        assertEquals(
            listOf(SearchTarget.SearchActivity(CHROME_PACKAGE), SearchTarget.DefaultBrowser),
            searchTargets(browserPackage = null),
        )
    }

    @Test
    fun `it looks for the activity named by the design`() {
        assertEquals("org.chromium.chrome.browser.searchwidget.SearchActivity", CHROMIUM_SEARCH_ACTIVITY)
    }

    @Test
    fun `a configured action is the only attempt`() {
        val action = TapAction.App("com.spotify.music")

        assertEquals(
            listOf(SearchTarget.Action(action)),
            searchbarTargets(action, browserPackage = "org.mozilla.firefox"),
        )
    }

    @Test
    fun `without a configured action the default queue applies`() {
        assertEquals(
            searchTargets("org.mozilla.firefox"),
            searchbarTargets(action = null, browserPackage = "org.mozilla.firefox"),
        )
    }
}
