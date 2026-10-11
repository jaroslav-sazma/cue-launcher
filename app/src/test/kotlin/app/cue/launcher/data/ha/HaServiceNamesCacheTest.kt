package app.cue.launcher.data.ha

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

class HaServiceNamesCacheTest {

    private var clock = 0L

    private var reads = 0

    private val cache = HaServiceNamesCache(now = { clock }, lifetime = 24.hours)

    private val names = mapOf("light.toggle" to "Přepnout")

    private suspend fun read(
        instance: String = "uuid|http://ha.local:8123|",
        language: String = "cs",
        domains: Set<String> = setOf("light", "cover"),
        answer: Map<String, String> = names,
    ) = cache.names(instance, language, domains) {
        reads++
        answer
    }

    @Test
    fun `a second opening of the form reuses the names`() = runTest {
        assertEquals(names, read())
        assertEquals(names, read(domains = setOf("light")))

        assertEquals(1, reads)
    }

    @Test
    fun `another instance or another language reads again`() = runTest {
        read()
        read(instance = "other|http://10.0.0.5:8123|")
        read(language = "de")

        assertEquals(3, reads)
    }

    @Test
    fun `a domain the names were not read with reads again`() = runTest {
        read()
        read(domains = setOf("light", "cover", "vacuum"))

        assertEquals(2, reads)
    }

    @Test
    fun `names older than their lifetime are read again, and so are names from the future`() = runTest {
        read()
        clock = 24.hours.inWholeMilliseconds
        read()
        clock = 0L
        read()

        assertEquals(3, reads)
    }

    @Test
    fun `a read that brought nothing is not kept`() = runTest {
        assertEquals(emptyMap<String, String>(), read(answer = emptyMap()))
        assertEquals(names, read())

        assertEquals(2, reads)
    }

    @Test
    fun `forms opened at once share one read`() = runTest {
        val results = List(3) {
            async {
                cache.names("uuid||", "cs", setOf("light")) {
                    reads++
                    delay(100)
                    names
                }
            }
        }.awaitAll()

        assertEquals(List(3) { names }, results)
        assertEquals(1, reads)
    }

    @Test
    fun `the instance key changes with the instance and with either address`() {
        val config = HaConfig(urlInternal = "http://ha.local:8123", instanceUuid = "uuid", token = "t")

        assertEquals(haInstanceKey(config), haInstanceKey(config.copy(token = "other")))
        assertNotEquals(haInstanceKey(config), haInstanceKey(config.copy(instanceUuid = "other")))
        assertNotEquals(haInstanceKey(config), haInstanceKey(config.copy(urlExternal = "https://ha.example")))
    }
}
