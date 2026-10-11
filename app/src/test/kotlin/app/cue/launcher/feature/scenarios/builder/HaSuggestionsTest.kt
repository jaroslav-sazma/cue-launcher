package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.ha.HaEntity
import app.cue.launcher.data.ha.HaEntityFilter
import app.cue.launcher.data.ha.HaService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HaSuggestionsTest {

    private val entities = listOf(
        HaEntity("cover.gate", "Brána", features = 3),
        HaEntity("light.gate_lamp", "Lamp"),
        HaEntity("light.kitchen", "Světlo kuchyň"),
        HaEntity("light.porch", "Porch gate"),
        HaEntity("sensor.outdoor_temperature", null),
        HaEntity("switch.gate", "Front gate"),
    )

    private val entityRows = haEntityRows(entities)

    private val services = listOf(
        service("cover.open_cover", "Open", "Otevřít", domains = setOf("cover"), features = listOf(1)),
        service("cover.set_cover_position", "Set position", null, domains = setOf("cover"), features = listOf(4)),
        service("homeassistant.toggle", "Generic toggle", "Obecné přepnutí", domains = null),
        service("light.toggle", "Toggle", "Přepnout", domains = setOf("light")),
        service("light.turn_on", "Turn on", "Zapnout", domains = setOf("light")),
        HaService("script.goodnight", name = "Goodnight"),
    )

    private val byId = entities.associateBy(HaEntity::id)

    private fun service(id: String, name: String?, local: String?, domains: Set<String>?, features: List<Int>? = null) =
        HaService(id, name, local, listOf(HaEntityFilter(domains = domains, features = features)))

    private fun suggested(rows: List<HaSearchRow>, query: String, limit: Int = 10) =
        haSuggestions(rows, query, limit).rows.map(HaSearchRow::value)

    @Test
    fun `an entity row shows the friendly name over the id`() {
        val row = entityRows.single { it.value == "switch.gate" }
        assertEquals("Front gate", row.title)
        assertEquals("switch.gate", row.detail)

        val unnamed = entityRows.single { it.value == "sensor.outdoor_temperature" }
        assertEquals("sensor.outdoor_temperature", unnamed.title)
        assertEquals(null, unnamed.detail)
    }

    @Test
    fun `a match at the start of the id or of a word ranks first`() {
        val rows = haEntityRows(
            listOf(
                HaEntity("light.tailgate", "Tailgate"),
                HaEntity("light.porch", "Porch gate"),
                HaEntity("switch.gate", null),
            ),
        )

        assertEquals(listOf("light.porch", "switch.gate", "light.tailgate"), suggested(rows, "gate"))
    }

    @Test
    fun `a word of the id counts as a start too, for an entity without a name`() {
        val rows = haEntityRows(listOf(HaEntity("light.aceiling", null), HaEntity("light.kitchen_ceiling", null)))

        assertEquals(listOf("light.kitchen_ceiling", "light.aceiling"), suggested(rows, "ceil"))
    }

    @Test
    fun `the search ignores case and diacritics on both sides`() {
        assertEquals(listOf("light.kitchen"), suggested(entityRows, "KUCHYN"))
        assertEquals(listOf("light.kitchen"), suggested(entityRows, "kuchyň"))
        assertEquals(listOf("cover.gate"), suggested(entityRows, "brana"))
        assertEquals(listOf("cover.gate"), suggested(entityRows, "Brána"))
    }

    @Test
    fun `an empty field offers the whole list and says how much did not fit`() {
        val suggestions = haSuggestions(entityRows, " ", limit = 4)

        assertEquals(entities.take(4).map(HaEntity::id), suggestions.rows.map(HaSearchRow::value))
        assertEquals(2, suggestions.more)
    }

    @Test
    fun `a value already chosen brings the whole list back, that value first`() {
        assertEquals(
            listOf("light.kitchen") + entities.map(HaEntity::id).filterNot { it == "light.kitchen" },
            suggested(entityRows, "light.kitchen"),
        )

        val cut = haSuggestions(entityRows, "switch.gate", limit = 2)
        assertEquals(listOf("switch.gate", "cover.gate"), cut.rows.map(HaSearchRow::value))
        assertEquals(entities.size - 2, cut.more)
    }

    @Test
    fun `nothing matching suggests nothing`() {
        assertEquals(HaSuggestions.NONE, haSuggestions(entityRows, "garage", limit = 5))
    }

    @Test
    fun `a listed entity gets the services its state allows, its own domain first`() {
        assertEquals(
            listOf("cover.open_cover", "homeassistant.toggle"),
            haServicesFor(services, byId, "cover.gate").map(HaService::id),
        )
        assertEquals(
            listOf("light.toggle", "light.turn_on", "homeassistant.toggle"),
            haServicesFor(services, byId, " light.kitchen ").map(HaService::id),
        )
    }

    @Test
    fun `an entity typed by hand is matched by its domain alone`() {
        assertEquals(
            listOf("cover.open_cover", "cover.set_cover_position", "homeassistant.toggle"),
            haServicesFor(services, byId, "cover.unknown").map(HaService::id),
        )
    }

    @Test
    fun `without an entity id every service is offered`() {
        assertEquals(services.map(HaService::id), haServicesFor(services, byId, "").map(HaService::id))
        assertEquals(services.map(HaService::id), haServicesFor(services, byId, "kuch").map(HaService::id))
        assertEquals(
            services.map(HaService::id),
            haServicesFor(services, byId, "{{PLACEHOLDER:gate}}").map(HaService::id),
        )
    }

    @Test
    fun `the choices hand out the rows of the services that fit`() {
        val choices = HaServiceChoices(services)
        val entityChoices = HaEntityChoices(entities)

        assertEquals(
            listOf("cover.open_cover", "homeassistant.toggle"),
            choices.rowsFor("cover.gate", entityChoices).map(HaSearchRow::value),
        )
        assertEquals("Otevřít", choices.rowsFor("cover.gate", entityChoices).first().title)
        assertEquals(entityChoices.rows.map(HaSearchRow::value), entities.map(HaEntity::id))
    }

    @Test
    fun `a service row shows the local name and is found by it, the English name or the id`() {
        val rows = haServiceRows(services)
        val toggle = rows.single { it.value == "light.toggle" }
        assertEquals("Přepnout", toggle.title)
        assertEquals("light.toggle", toggle.detail)
        assertEquals("Set position", rows.single { it.value == "cover.set_cover_position" }.title)

        assertEquals(listOf("light.toggle"), suggested(rows, "přepnout"))
        assertEquals(listOf("light.turn_on"), suggested(rows, "turn on"))
        assertEquals(listOf("cover.open_cover", "cover.set_cover_position"), suggested(rows, "cover."))
    }
}
