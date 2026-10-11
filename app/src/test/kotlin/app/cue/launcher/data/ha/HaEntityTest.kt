package app.cue.launcher.data.ha

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HaEntityTest {

    private val states = Json.parseToJsonElement(
        """
        [
          {"entity_id": "switch.gate", "state": "off", "attributes": {"friendly_name": "Front gate"}},
          {"entity_id": "light.living_room", "state": "on", "attributes": {"friendly_name": "  "}},
          {"entity_id": "sensor.outdoor_temperature", "state": "12.5", "attributes": {}},
          {"entity_id": "light.gate_lamp", "state": "off", "attributes": {"friendly_name": "Lamp"}},
          {"entity_id": "Not An Id", "state": "x"},
          {"state": "orphan"},
          "not an object",
          {"entity_id": "switch.gate", "state": "on"}
        ]
        """.trimIndent(),
    )

    private val entities = haEntitiesOf(states)

    @Test
    fun `the state list becomes sorted entities with their friendly names`() {
        assertEquals(
            listOf(
                HaEntity("light.gate_lamp", "Lamp"),
                HaEntity("light.living_room", null),
                HaEntity("sensor.outdoor_temperature", null),
                HaEntity("switch.gate", "Front gate"),
            ),
            entities,
        )
    }

    @Test
    fun `the domain is the part before the dot`() {
        assertEquals(listOf("light", "light", "sensor", "switch"), entities.map(HaEntity::domain))
    }

    @Test
    fun `anything but an array lists nothing`() {
        assertEquals(emptyList<HaEntity>(), haEntitiesOf(Json.parseToJsonElement("""{"message": "x"}""")))
    }

    @Test
    fun `the supported features and the device class come along when the state reports them`() {
        val listed = haEntitiesOf(
            Json.parseToJsonElement(
                """
                [
                  {"entity_id": "cover.gate", "attributes": {"supported_features": 11, "device_class": "gate"}},
                  {"entity_id": "cover.odd", "attributes": {"supported_features": "11", "device_class": 3}}
                ]
                """.trimIndent(),
            ),
        )

        assertEquals(
            listOf(HaEntity("cover.gate", null, features = 11, deviceClass = "gate"), HaEntity("cover.odd", null)),
            listed,
        )
    }
}
