package app.cue.launcher.system

import app.cue.launcher.data.layout.ComponentRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class IconCacheTest {

    @Test
    fun `the cache does not grow past the cap and evicts the least recently used`() {
        val cache = IconCache<String>(maxSize = 2)
        cache.put(component("a"), "A")
        cache.put(component("b"), "B")

        cache.put(component("c"), "C")

        assertEquals(2, cache.size)
        assertNull(cache.get(component("a")))
        assertNotNull(cache.get(component("b")))
        assertNotNull(cache.get(component("c")))
    }

    @Test
    fun `reading moves the entry to the end of the queue`() {
        val cache = IconCache<String>(maxSize = 2)
        cache.put(component("a"), "A")
        cache.put(component("b"), "B")

        cache.get(component("a"))
        cache.put(component("c"), "C")

        assertNotNull(cache.get(component("a")))
        assertNull(cache.get(component("b")))
    }

    @Test
    fun `invalidating a package deletes all its activities, leaves the rest`() {
        val cache = IconCache<String>(maxSize = 8)
        cache.put(ComponentRef("com.mail", "Main", 0), "M1")
        cache.put(ComponentRef("com.mail", "Compose", 0), "M2")
        cache.put(ComponentRef("com.maps", "Main", 0), "G")

        cache.invalidatePackage("com.mail", userSerial = 0)

        assertEquals(1, cache.size)
        assertNotNull(cache.get(ComponentRef("com.maps", "Main", 0)))
    }

    @Test
    fun `invalidating one profile does not touch the copy in the other`() {
        val cache = IconCache<String>(maxSize = 8)
        val personal = ComponentRef("com.mail", "Main", 0)
        val work = ComponentRef("com.mail", "Main", 10)
        cache.put(personal, "osobní")
        cache.put(work, "pracovní")

        cache.invalidatePackage("com.mail", userSerial = 10)

        assertEquals("osobní", cache.get(personal))
        assertNull(cache.get(work))
    }

    @Test
    fun `an icon from the work profile is a different entry than the personal one`() {
        val cache = IconCache<String>(maxSize = 8)
        cache.put(ComponentRef("com.mail", "Main", 0), "osobní")
        cache.put(ComponentRef("com.mail", "Main", 10), "pracovní")

        assertEquals(2, cache.size)
        assertEquals("pracovní", cache.get(ComponentRef("com.mail", "Main", 10)))
    }

    @Test
    fun `overwriting a key does not grow the cache`() {
        val cache = IconCache<String>(maxSize = 2)
        cache.put(component("a"), "A")
        cache.put(component("a"), "A2")

        assertEquals(1, cache.size)
        assertEquals("A2", cache.get(component("a")))
    }

    private fun component(name: String) =
        ComponentRef("app.test.$name", "app.test.$name.Main", userSerial = 0)
}
