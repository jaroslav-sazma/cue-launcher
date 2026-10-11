package app.cue.launcher.data.contacts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ContactTest {

    @Test
    fun `first and last name give two letters`() {
        assertEquals("ZM", contactInitials("Zdeněk Malý"))
    }

    @Test
    fun `a single word gives one letter`() {
        assertEquals("J", contactInitials("Jiří"))
    }

    @Test
    fun `the first and last words are taken`() {
        assertEquals("JS", contactInitials("Jan Novák Starší"))
    }

    @Test
    fun `lowercase letters are capitalized and diacritics stay`() {
        assertEquals("ŽK", contactInitials("žofie křížová"))
    }

    @Test
    fun `words without letters are skipped`() {
        assertEquals("N", contactInitials("\"Pepa\" Novák"))
        assertEquals("", contactInitials("+++"))
        assertEquals("", contactInitials("   "))
    }

    @Test
    fun `a number as a name gives digits`() {
        assertEquals("71", contactInitials("776 210 118"))
    }
}
