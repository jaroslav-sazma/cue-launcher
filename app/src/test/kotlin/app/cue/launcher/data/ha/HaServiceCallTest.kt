package app.cue.launcher.data.ha

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HaServiceCallTest {

    private fun data(vararg pairs: Pair<String, String>) =
        JsonObject(pairs.associate { (key, value) -> key to JsonPrimitive(value) })

    @Test
    fun `a service splits into a domain and a name`() {
        val call = haServiceCallOf(service = "light.turn_off", entity = "light.obyvak")

        assertEquals("light", call?.domain)
        assertEquals("turn_off", call?.service)
        assertEquals("/api/services/light/turn_off", call?.let(::haServicePath))
    }

    @Test
    fun `a service is normalized to lowercase`() {
        assertEquals("turn_off", haServiceCallOf(" Light.Turn_Off ")?.service)
    }

    @Test
    fun `a service without a domain is not a call`() {
        assertNull(haServiceCallOf("turn_off"))
        assertNull(haServiceCallOf(""))
        assertNull(haServiceCallOf("light.turn off"))
    }

    @Test
    fun `an empty entity does not make it into the body`() {
        val call = haServiceCallOf("script.rano", entity = "  ", data = data("promenna" to "1"))

        assertNull(call?.entityId)
        assertEquals(data("promenna" to "1"), call?.let(::haServiceBody))
    }

    @Test
    fun `the entity is attached to the data`() {
        val call = haServiceCallOf("light.turn_on", entity = "light.obyvak", data = data("brightness" to "200"))

        assertEquals(
            data("brightness" to "200", "entity_id" to "light.obyvak"),
            call?.let(::haServiceBody),
        )
    }

    @Test
    fun `the entity field overrides the entity in the data`() {
        val call = haServiceCallOf(
            service = "light.turn_on",
            entity = "light.loznice",
            data = data("entity_id" to "light.obyvak"),
        )

        assertEquals(JsonPrimitive("light.loznice"), call?.let(::haServiceBody)?.get("entity_id"))
    }

    @Test
    fun `an entity id is recognized by its shape`() {
        assertEquals("sensor.teplota", haEntityIdOrNull(" Sensor.Teplota "))
        assertEquals("binary_sensor.dvere_1", haEntityIdOrNull("binary_sensor.dvere_1"))
        assertNull(haEntityIdOrNull("teplota"))
        assertNull(haEntityIdOrNull("sensor teplota"))
        assertNull(haEntityIdOrNull("2.teplota"))
    }

    @Test
    fun `the path to an entity state`() {
        assertEquals("/api/states/sensor.teplota", haStatePath("sensor.teplota"))
    }

    @Test
    fun `an unavailable state is not usable`() {
        assertTrue(haStateUsable("21.5"))
        assertFalse(haStateUsable("unavailable"))
        assertFalse(haStateUsable("unknown"))
        assertFalse(haStateUsable(" "))
        assertFalse(haStateUsable(null))
    }
}
