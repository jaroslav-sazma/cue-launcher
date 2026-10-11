package app.cue.launcher.system.iconpack

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AppFilterTest {

    @Test
    fun `a component wrapped in ComponentInfo translates to a package and class`() {
        val key = appFilterKey("ComponentInfo{com.mail/com.mail.MainActivity}")

        assertEquals(AppFilterKey("com.mail", "com.mail.MainActivity"), key)
    }

    @Test
    fun `a component without the wrapper works too`() {
        val key = appFilterKey("com.mail/com.mail.MainActivity")

        assertEquals(AppFilterKey("com.mail", "com.mail.MainActivity"), key)
    }

    @Test
    fun `a shortened class name is completed with the package name`() {
        val key = appFilterKey("ComponentInfo{com.mail/.MainActivity}")

        assertEquals(AppFilterKey("com.mail", "com.mail.MainActivity"), key)
    }

    @Test
    fun `whitespace around the entry does not matter`() {
        val key = appFilterKey("  ComponentInfo{ com.mail/com.mail.Main }  ")

        assertEquals(AppFilterKey("com.mail", "com.mail.Main"), key)
    }

    @Test
    fun `a component without a slash or with an empty part is discarded`() {
        assertNull(appFilterKey("ComponentInfo{com.mail}"))
        assertNull(appFilterKey("com.mail/"))
        assertNull(appFilterKey("/com.mail.Main"))
        assertNull(appFilterKey(""))
    }

    @Test
    fun `the mapping finds a drawable by exact activity`() {
        val filter = appFilterOf(
            sequenceOf(
                "ComponentInfo{com.mail/com.mail.Main}" to "mail",
                "ComponentInfo{com.maps/com.maps.Main}" to "maps",
            ),
        )

        assertEquals(2, filter.size)
        assertEquals("mail", filter.drawableFor("com.mail", "com.mail.Main"))
        assertEquals("maps", filter.drawableFor("com.maps", "com.maps.Main"))
    }

    @Test
    fun `a different activity of the same package does not get an icon from the pack`() {
        val filter = appFilterOf(sequenceOf("ComponentInfo{com.mail/com.mail.Main}" to "mail"))

        assertNull(filter.drawableFor("com.mail", "com.mail.Compose"))
    }

    @Test
    fun `a component listed twice keeps its first entry`() {
        val filter = appFilterOf(
            sequenceOf(
                "ComponentInfo{com.mail/com.mail.Main}" to "mail",
                "ComponentInfo{com.mail/com.mail.Main}" to "mail_alt",
            ),
        )

        assertEquals(1, filter.size)
        assertEquals("mail", filter.drawableFor("com.mail", "com.mail.Main"))
    }

    @Test
    fun `rows without a component or without a drawable are skipped, the rest of the pack applies`() {
        val filter = appFilterOf(
            sequenceOf(
                null to "sirotek",
                "ComponentInfo{com.mail/com.mail.Main}" to null,
                "ComponentInfo{com.maps/com.maps.Main}" to "   ",
                "nesmysl" to "nesmysl",
                "ComponentInfo{com.notes/com.notes.Main}" to " notes ",
            ),
        )

        assertEquals(1, filter.size)
        assertEquals("notes", filter.drawableFor("com.notes", "com.notes.Main"))
    }

    @Test
    fun `an empty mapping produces nothing`() {
        assertEquals(0, AppFilter.EMPTY.size)
        assertNull(AppFilter.EMPTY.drawableFor("com.mail", "com.mail.Main"))
    }
}
