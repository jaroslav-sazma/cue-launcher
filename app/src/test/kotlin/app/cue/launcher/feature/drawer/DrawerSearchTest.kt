package app.cue.launcher.feature.drawer

import app.cue.launcher.data.contacts.Contact
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.feature.drawer.search.AppSearchResult
import app.cue.launcher.feature.drawer.search.ContactSearchResult
import app.cue.launcher.feature.drawer.search.MatchQuality
import app.cue.launcher.feature.drawer.search.SearchField
import app.cue.launcher.system.InstalledApp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class DrawerSearchTest {

    private val camera = app("Camera")
    private val calendar = app("Calendar")
    private val cyril = contact(id = 1, name = "Cyril Dvořák")
    private val cecilie = contact(id = 2, name = "Cecilie Nováková")

    @Test
    fun `Enter launches the first app even when contacts matched too`() {
        val search = DrawerSearch(apps = listOf(camera, calendar), contacts = listOf(cyril, cecilie))

        assertSame(camera, search.enterApp)
        assertNull(search.enterContact, "a contact must not compete with a matched app for Enter")
    }

    @Test
    fun `Enter falls through to the first contact only when no app matched`() {
        val search = DrawerSearch(apps = emptyList(), contacts = listOf(cyril, cecilie))

        assertNull(search.enterApp)
        assertSame(cyril, search.enterContact)
    }

    @Test
    fun `no results leave Enter without a target`() {
        assertNull(DrawerSearch.EMPTY.enterApp)
        assertNull(DrawerSearch.EMPTY.enterContact)
    }

    @Test
    fun `the same results found again compare equal so the list keeps its scroll position`() {
        val first = DrawerSearch(apps = listOf(app("Camera")), contacts = listOf(contact(1, "Cyril Dvořák")))
        val again = DrawerSearch(apps = listOf(app("Camera")), contacts = listOf(contact(1, "Cyril Dvořák")))

        assertEquals(first, again)
    }

    private fun app(label: String) = AppSearchResult(
        app = InstalledApp(ComponentRef("test.$label", "test.$label.Main", userSerial = 0), label),
        matchedField = SearchField.LABEL,
        quality = MatchQuality.PREFIX,
    )

    private fun contact(id: Long, name: String) = ContactSearchResult(
        contact = Contact(id = id, lookupUri = "lookup/$id", displayName = name, phoneNumber = "+420600000$id"),
        quality = MatchQuality.PREFIX,
    )
}
