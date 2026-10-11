package app.cue.launcher.data.ha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HaServiceTest {

    // The shape /api/services answers in, trimmed to what the launcher reads.
    private val services = haServicesOf(
        Json.parseToJsonElement(
            """
            [
              {"domain": "light", "services": {
                "turn_on": {"name": "Turn on", "fields": {}, "target": {"entity": [{"domain": ["light"]}]}},
                "toggle": {"name": " Toggle ", "target": {"entity": [{"domain": ["light"]}]}}
              }},
              {"domain": "cover", "services": {
                "open_cover": {"name": "Open", "target": {"entity": [{"domain": ["cover"], "supported_features": [1]}]}},
                "set_cover_position": {"name": "Set position",
                  "target": {"entity": [{"domain": ["cover"], "supported_features": [4]}]}},
                "toggle": {"name": "Toggle", "target": {"entity": {"domain": "cover", "supported_features": [1, 2]}}}
              }},
              {"domain": "homeassistant", "services": {
                "toggle": {"name": "Generic toggle", "target": {"entity": {}}},
                "restart": {"name": "Restart"}
              }},
              {"domain": "weather", "services": {
                "get_forecasts": {"name": "Get forecasts", "target": {"entity": [{"domain": ["weather"]}]},
                  "response": {"optional": false}}
              }},
              {"domain": "script", "services": {
                "goodnight": {"name": "Goodnight", "response": {"optional": true}},
                "Bad Id": {"name": "x"}
              }},
              {"domain": "button", "services": {
                "press": {"target": {"entity": [{"domain": ["button"], "device_class": ["restart"]}]}}
              }},
              {"domain": "notify", "services": {
                "persistent": {"name": "Persistent", "target": null},
                "device_only": {"name": "Device only", "target": {"device": {"integration": "mobile_app"}}},
                "nothing": {"name": "Nothing", "target": {"entity": [{"domain": ["notify"], "supported_features": []}]}}
              }},
              {"domain": "script", "services": {
                "refresh": {"name": "Refresh", "fields": {"entity_id": {"selector": {"entity": {"domain": "sensor"}}}}},
                "dim": {"name": "Dim", "fields": {"entity_id": {"selector": {"entity": {"multiple": true,
                  "filter": [{"domain": ["light"], "supported_features": [32]}]}}}}},
                "anything": {"name": "Anything", "fields": {"entity_id": {"selector": {"entity": {}}}}},
                "text_only": {"name": "Text", "fields": {"entity_id": {"selector": {"text": {}}}}}
              }},
              {"domain": "vacuum", "services": {
                "start": {"name": "Start", "target": {"entity": [{"domain": ["vacuum"], "supported_features": ["vacuum.VacuumEntityFeature.START"]}]}}
              }},
              "not an object",
              {"services": {"orphan": {}}}
            ]
            """.trimIndent(),
        ),
    )

    private fun service(id: String) = services.single { it.id == id }

    @Test
    fun `every described service is listed by its id and English name, sorted`() {
        assertEquals(
            listOf(
                "button.press", "cover.open_cover", "cover.set_cover_position", "cover.toggle",
                "homeassistant.restart", "homeassistant.toggle", "light.toggle", "light.turn_on",
                "notify.device_only", "notify.nothing", "notify.persistent",
                "script.anything", "script.dim", "script.goodnight", "script.refresh", "script.text_only",
                "vacuum.start",
            ),
            services.map(HaService::id),
        )
        assertEquals("Toggle", service("light.toggle").name)
        assertEquals(null, service("button.press").name)
    }

    @Test
    fun `a service that must return a response is left out, one that may is kept`() {
        assertFalse(services.any { it.id == "weather.get_forecasts" })
        assertTrue(services.any { it.id == "script.goodnight" })
    }

    @Test
    fun `a service without a target fits no entity`() {
        val light = HaEntity("light.kitchen", "Kitchen")

        assertEquals(null, service("homeassistant.restart").targets)
        assertFalse(service("homeassistant.restart").fits(light))
        assertFalse(service("script.goodnight").fitsDomain("light"))
    }

    @Test
    fun `the domain of the target decides, and an empty target takes any entity`() {
        val light = HaEntity("light.kitchen", "Kitchen")

        assertTrue(service("light.toggle").fits(light))
        assertFalse(service("cover.open_cover").fits(light))
        assertTrue(service("homeassistant.toggle").fits(light))
        assertTrue(service("homeassistant.toggle").fitsDomain("sensor"))
    }

    @Test
    fun `a feature filter needs any one of its bits, as in Home Assistant`() {
        val gate = HaEntity("cover.gate", "Gate", features = 1 or 2)

        assertTrue(service("cover.open_cover").fits(gate))
        assertFalse(service("cover.set_cover_position").fits(gate))
        assertTrue(service("cover.toggle").fits(HaEntity("cover.blind", null, features = 2)))
    }

    @Test
    fun `an entity that reports no features is not held to a feature filter`() {
        assertTrue(service("cover.set_cover_position").fits(HaEntity("cover.gate", null)))
    }

    @Test
    fun `a feature filter Home Assistant did not resolve to a mask lets everything through`() {
        assertTrue(service("vacuum.start").fits(HaEntity("vacuum.robot", null, features = 0)))
    }

    @Test
    fun `a device class filter needs the class, and a missing one does not pass`() {
        assertTrue(service("button.press").fits(HaEntity("button.router", null, deviceClass = "restart")))
        assertFalse(service("button.press").fits(HaEntity("button.doorbell", null)))
        assertTrue(service("button.press").fitsDomain("button"))
    }

    @Test
    fun `a target left null or naming only devices takes any entity`() {
        val sensor = HaEntity("sensor.outdoor", null)

        assertTrue(service("notify.persistent").fits(sensor))
        assertTrue(service("notify.device_only").fits(sensor))
    }

    @Test
    fun `an empty feature list lets no entity through, as in Home Assistant`() {
        assertFalse(service("notify.nothing").fits(HaEntity("notify.phone", null, features = 7)))
    }

    @Test
    fun `without a target, the entity selector of an entity_id field filters instead`() {
        assertTrue(service("script.refresh").fits(HaEntity("sensor.outdoor", null)))
        assertFalse(service("script.refresh").fits(HaEntity("light.kitchen", null)))
        assertTrue(service("script.dim").fits(HaEntity("light.kitchen", null, features = 32)))
        assertFalse(service("script.dim").fits(HaEntity("light.kitchen", null, features = 4)))
        assertTrue(service("script.anything").fits(HaEntity("lock.door", null)))
        assertFalse(service("script.text_only").fits(HaEntity("lock.door", null)))
    }

    @Test
    fun `local names replace nothing but fill localName`() {
        val named = haServicesNamed(services, mapOf("light.toggle" to "Přepnout"))

        assertEquals("Přepnout", named.single { it.id == "light.toggle" }.localName)
        assertEquals("Toggle", named.single { it.id == "light.toggle" }.name)
        assertEquals(null, named.single { it.id == "light.turn_on" }.localName)
    }

    @Test
    fun `anything but an array lists nothing`() {
        assertEquals(emptyList<HaService>(), haServicesOf(Json.parseToJsonElement("""{"message": "x"}""").jsonObject))
    }
}
