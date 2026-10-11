package app.cue.launcher.feature.widgets

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WidgetPreviewCacheTest {

    @Test
    fun `a second query for the same widget does not hit the system again`() = runTest {
        val cache = WidgetPreviewCache<String>(maxSize = 4)
        var loads = 0

        val first = cache.getOrLoad("a") { loads++; "A" }
        val second = cache.getOrLoad("a") { loads++; "A" }

        assertEquals("A", first)
        assertEquals("A", second)
        assertEquals(1, loads)
    }

    @Test
    fun `even a failed load is remembered`() = runTest {
        val cache = WidgetPreviewCache<String>(maxSize = 4)
        var loads = 0

        assertNull(cache.getOrLoad("a") { loads++; null })
        assertNull(cache.getOrLoad("a") { loads++; null })

        assertEquals(1, loads)
        assertEquals(1, cache.size)
    }

    @Test
    fun `cache does not exceed its cap and evicts the least recently used`() = runTest {
        val cache = WidgetPreviewCache<String>(maxSize = 2)
        cache.getOrLoad("a") { "A" }
        cache.getOrLoad("b") { "B" }

        cache.getOrLoad("c") { "C" }

        assertEquals(2, cache.size)
        assertNull(cache.peek("a"))
        assertEquals("B", cache.peek("b"))
        assertEquals("C", cache.peek("c"))
    }

    @Test
    fun `reading moves an item to the end of the queue`() = runTest {
        val cache = WidgetPreviewCache<String>(maxSize = 2)
        cache.getOrLoad("a") { "A" }
        cache.getOrLoad("b") { "B" }

        cache.peek("a")
        cache.getOrLoad("c") { "C" }

        assertEquals("A", cache.peek("a"))
        assertNull(cache.peek("b"))
    }

    @Test
    fun `an unloaded widget is not in the cache`() = runTest {
        val cache = WidgetPreviewCache<String>(maxSize = 2)

        assertNull(cache.peek("a"))
        assertEquals(0, cache.size)
    }

    @Test
    fun `an evicted entry is loaded again`() = runTest {
        val cache = WidgetPreviewCache<String>(maxSize = 1)
        var loads = 0
        cache.getOrLoad("a") { loads++; "A" }
        cache.getOrLoad("b") { loads++; "B" }

        cache.getOrLoad("a") { loads++; "A" }

        assertEquals(3, loads)
    }
}
