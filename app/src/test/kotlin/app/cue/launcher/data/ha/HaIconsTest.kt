package app.cue.launcher.data.ha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HaIconsTest {

    private fun message(json: String) = Json.parseToJsonElement(json).jsonObject

    private fun step(json: String) = haIconsStep(message(json), TOKEN, setOf("lock", "cover"))

    private val resources = message(
        """
        {
          "lock": {"_": {"default": "mdi:lock", "state": {"unlocked": "mdi:lock-open-variant", "jammed": "mdi:lock-alert"}}},
          "cover": {
            "_": {"default": "mdi:window-open", "state": {"closed": "mdi:window-closed"}},
            "gate": {"default": "mdi:gate-open", "state": {"closed": "mdi:gate"}}
          },
          "light": {"_": {"default": "mdi:lightbulb", "state": {"off": "mdi:lightbulb-off"}}},
          "broken": {"_": {"default": 5, "state": ["x"]}},
          "odd": "mdi:nothing"
        }
        """,
    )

    private val table = HaIconTable(instance = "home", version = "2026.10.1", domains = haIconsOf(resources))

    @Test
    fun `the table keeps the default and the state icons of every set`() {
        val cover = table.domains.getValue("cover")

        assertEquals(HaIconSet("mdi:gate-open", mapOf("closed" to "mdi:gate")), cover["gate"])
        assertEquals("mdi:window-open", cover["_"]?.default)
        assertEquals(setOf("lock", "cover", "light"), table.domains.keys)
    }

    @Test
    fun `a state that has an icon of its own gets it, any other the default`() {
        assertEquals("mdi:lock-open-variant", haStateGlyphs(table, "lock.front_door", "unlocked", null).first())
        assertEquals("mdi:lock", haStateGlyphs(table, "lock.front_door", "locked", null).first())
        assertEquals("mdi:lock", haStateGlyphs(table, "lock.front_door", null, null).first())
    }

    @Test
    fun `the device class picks its own set`() {
        val gate = HaIconAttributes(deviceClass = "gate")

        assertEquals("mdi:gate", haStateGlyphs(table, "cover.gate", "closed", gate).first())
        assertEquals("mdi:gate-open", haStateGlyphs(table, "cover.gate", "open", gate).first())
    }

    // Home Assistant does not mix the plain set into a class it has no set for: it falls back to it.
    @Test
    fun `a device class the domain has no set for falls back to the plain one`() {
        val garage = HaIconAttributes(deviceClass = "garage")

        assertEquals("mdi:window-closed", haStateGlyphs(table, "cover.garage", "closed", garage).first())
    }

    @Test
    fun `an icon set on the entity in Home Assistant wins and does not follow the state`() {
        val custom = HaIconAttributes(deviceClass = "gate", icon = "mdi:boom-gate")

        assertEquals("mdi:boom-gate", haStateGlyphs(table, "cover.gate", "closed", custom).first())
        assertEquals("mdi:boom-gate", haStateGlyphs(table, "cover.gate", "open", custom).first())
    }

    // The tail only matters when the bundled font lacks a name further up.
    @Test
    fun `the candidates run from the most to the least specific without repeats`() {
        val gate = HaIconAttributes(deviceClass = "gate", icon = "mdi:boom-gate")

        assertEquals(
            listOf("mdi:boom-gate", "mdi:gate", "mdi:gate-open", "mdi:window-closed", "mdi:window-open"),
            haStateGlyphs(table, "cover.gate", "closed", gate),
        )
        assertEquals(listOf("mdi:lock"), haStateGlyphs(table, "lock.front_door", "locked", null))
    }

    @Test
    fun `without a table only an icon set on the entity is left`() {
        assertEquals(emptyList<String>(), haStateGlyphs(null, "lock.front_door", "unlocked", null))
        assertEquals(emptyList<String>(), haStateGlyphs(table, "scene.evening", "scening", null))
        assertEquals(
            listOf("mdi:palette"),
            haStateGlyphs(null, "scene.evening", null, HaIconAttributes(icon = "mdi:palette")),
        )
    }

    @Test
    fun `the attributes are read from a state's attributes`() {
        val attributes = message("""{"device_class":"gate","icon":"mdi:gate-arrow-right","friendly_name":"Gate"}""")

        assertEquals(HaIconAttributes("gate", "mdi:gate-arrow-right"), haIconAttributesOf(attributes))
        assertEquals(HaIconAttributes(), haIconAttributesOf(message("""{"device_class":7,"icon":""}""")))
        assertNull(haIconAttributesOf(null))
    }

    @Test
    fun `the exchange signs in, asks for the component icons of the domains and keeps the version`() {
        val required = step("""{"type":"auth_required","ha_version":"2026.10.1"}""")
        val ok = step("""{"type":"auth_ok","ha_version":"2026.10.1"}""")
        val request = message(ok.send.orEmpty())

        assertEquals(haLiveAuth(TOKEN), required.send)
        assertEquals("2026.10.1", required.version)
        assertEquals("frontend/get_icons", request["type"]?.jsonPrimitive?.content)
        assertEquals("entity_component", request["category"]?.jsonPrimitive?.content)
        assertEquals(listOf("cover", "lock"), request["integration"]?.jsonArray?.map { it.jsonPrimitive.content })
        assertFalse(ok.done)
    }

    @Test
    fun `the answer ends the exchange with the table`() {
        val step = step("""{"id":1,"type":"result","success":true,"result":{"resources":$resources}}""")

        assertTrue(step.done)
        assertEquals(table.domains, step.icons)
    }

    @Test
    fun `a refused token ends the exchange without a table`() {
        val step = step("""{"type":"auth_invalid","message":"Invalid access token"}""")

        assertTrue(step.done)
        assertNull(step.icons)
    }

    // An instance older than the command refuses it; asking again before it updates would bring the
    // same refusal, so it counts as an answer with no icons.
    @Test
    fun `a refused request or an empty answer ends the exchange with no icons`() {
        val answers = listOf(
            """{"id":1,"type":"result","success":false,"error":{"code":"unknown_command"}}""",
            """{"id":1,"type":"result","success":true,"result":{"resources":{"lock":null}}}""",
        )

        answers.forEach { json ->
            val step = step(json)
            assertTrue(step.done, json)
            assertEquals(emptyMap<String, Map<String, HaIconSet>>(), step.icons, json)
        }
    }

    @Test
    fun `an answer to another request is not the one waited for`() {
        assertFalse(step("""{"id":2,"type":"result","success":true,"result":{"resources":$resources}}""").done)
    }

    @Test
    fun `a table of the same version takes in the domains read later`() {
        val lock = table.copy(domains = table.domains.filterKeys { it == "lock" }, asked = setOf("lock"))
        val light = table.copy(domains = table.domains.filterKeys { it == "light" }, asked = setOf("light", "fan"))

        val merged = lock.with(light)

        assertEquals(setOf("lock", "light"), merged.domains.keys)
        assertTrue(merged.covers(setOf("lock", "light", "fan")))
        assertFalse(merged.covers(setOf("cover")))
        assertEquals(light.copy(version = "2026.11.0"), lock.with(light.copy(version = "2026.11.0")))
    }

    @Test
    fun `only a version the instance announced makes a table outdated`() {
        assertFalse(table.isOutdated(null))
        assertFalse(table.isOutdated("2026.10.1"))
        assertTrue(table.isOutdated("2026.11.0"))
        assertFalse(table.copy(version = "").isOutdated("2026.11.0"))
    }

    @Test
    fun `an added entity brings its attributes, a change merges and drops them`() {
        val entities = setOf("cover.gate")
        val added = haLiveAttributes(
            emptyMap(),
            message("""{"a":{"cover.gate":{"s":"closed","a":{"device_class":"gate","icon":"mdi:boom-gate"}}}}"""),
            entities,
        )
        val stateOnly = haLiveAttributes(added, message("""{"c":{"cover.gate":{"+":{"s":"open"}}}}"""), entities)
        val lost = haLiveAttributes(added, message("""{"c":{"cover.gate":{"-":{"a":["icon"]}}}}"""), entities)
        val removed = haLiveAttributes(added, message("""{"r":["cover.gate"]}"""), entities)

        assertEquals(mapOf("cover.gate" to HaIconAttributes("gate", "mdi:boom-gate")), added)
        assertEquals(added, stateOnly)
        assertEquals(mapOf("cover.gate" to HaIconAttributes("gate", null)), lost)
        assertEquals(emptyMap<String, HaIconAttributes>(), removed)
    }

    @Test
    fun `attributes of an entity no tile refers to are not kept`() {
        val other = haLiveAttributes(
            emptyMap(),
            message("""{"a":{"light.hall":{"s":"on","a":{"device_class":"x"}}}}"""),
            setOf("cover.gate"),
        )

        assertEquals(emptyMap<String, HaIconAttributes>(), other)
    }

    // The table outlives a disconnect, so what it records of the instance is a digest, not the address.
    @Test
    fun `the instance is told apart by a digest of its identity`() {
        val home = HaConfig(urlInternal = "http://192.168.1.10:8123", instanceUuid = "abc")
        val other = home.copy(urlInternal = "http://192.168.1.11:8123")

        assertEquals(haIconInstance(home), haIconInstance(home.copy(token = "another")))
        assertNotEquals(haIconInstance(home), haIconInstance(other))
        assertFalse(haIconInstance(home).contains("192.168"))
    }

    private companion object {
        const val TOKEN = "secret-token"
    }
}
