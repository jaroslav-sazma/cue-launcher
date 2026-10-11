package app.cue.launcher.data.scenario

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScenarioEntityTest {

    private val scenario = Scenario(
        id = "evening-home",
        name = "Večer doma",
        priority = 60,
        size = TileSize.GRID_2X2,
        conditions = listOf(
            Condition.TimeWindow(from = "18:00", to = "23:00"),
            Condition.Wifi(mode = WifiMode.SSID, ssid = "Domov"),
        ),
        content = TileContent.Template(
            layout = TemplateLayout.FOUR_ICONS,
            text = "Domov",
            items = List(4) {
                TemplateItem(
                    label = "Světla",
                    icon = "mdi:lightbulb-off",
                    actions = listOf(TapAction.HaAction(service = "light.turn_off", entity = "light.x")),
                )
            },
        ),
    )

    @Test
    fun `a scenario converts to a row and back`() {
        assertEquals(scenario, scenario.toEntity().toScenario())
    }

    @Test
    fun `columns match the JSON content`() {
        val row = scenario.toEntity()

        assertEquals("evening-home", row.id)
        assertEquals("Večer doma", row.name)
        assertTrue(row.enabled)
        assertEquals(60, row.priority)
        assertEquals("2x2", row.size)
        assertEquals(ScenarioFormat.canonicalJson(scenario), row.json)
    }

    @Test
    fun `a scenario without conditions is a fallback`() {
        val fallback = scenario.copy(conditions = emptyList())

        assertTrue(fallback.isFallback)
    }

    @Test
    fun `an unreadable row reads as null`() {
        val broken = scenario.toEntity().copy(json = """{"id":"x","size":"8x2"}""")

        assertNull(broken.toScenario())
    }

    @Test
    fun `an activation converts to a row and back`() {
        val instant = Activation(scenarioId = "ha-gate", since = 1_758_000_000_000, expiresAt = 1_758_000_060_000)
        val state = Activation(scenarioId = "evening-home", since = 1_758_000_000_000)

        assertEquals(instant, instant.toEntity().toActivation())
        assertEquals(state, state.toEntity().toActivation())
        assertNull(state.toEntity().expiresAt)
    }

    @Test
    fun `an activation payload converts to a row and back`() {
        val activation = Activation(
            scenarioId = "ha-washer",
            since = 1_758_000_000_000,
            expiresAt = 1_758_000_120_000,
            payload = TriggerPayload(icon = "mdi:washing-machine", fields = mapOf("ha:teplota" to "38")),
        )

        assertEquals(activation, activation.toEntity().toActivation())
    }

    @Test
    fun `progress and semantics convert to a row and back`() {
        val activation = Activation(
            scenarioId = "notification-ride-progress",
            since = 1_758_000_000_000,
            expiresAt = 1_758_001_800_000,
            payload = TriggerPayload(
                fields = mapOf("notif:title" to "Řidič je na cestě"),
                progress = TileProgress(
                    value = 30,
                    max = 100,
                    segments = listOf(
                        TileProgressSegment(50, SemanticStyle.SAFE),
                        TileProgressSegment(50, SemanticStyle.CAUTION),
                    ),
                    points = listOf(TileProgressPoint(50, SemanticStyle.INFO)),
                ),
                semantic = SemanticStyle.CAUTION,
            ),
        )

        assertEquals(activation, activation.toEntity().toActivation())
    }

    @Test
    fun `the reply option converts to a row and back`() {
        val activation = Activation(
            scenarioId = "notification-message",
            since = 1_758_000_000_000,
            expiresAt = 1_758_000_900_000,
            payload = TriggerPayload(
                fields = mapOf("notif:title" to "Honza"),
                reply = NotificationReply(key = "0|com.example.chat|7|null|10123", hint = "Odpovědět Honzovi"),
            ),
        )

        assertEquals(activation, activation.toEntity().toActivation())
    }

    @Test
    fun `an empty payload is not written to the row`() {
        val activation = Activation(scenarioId = "evening-home", since = 1_758_000_000_000)

        assertNull(activation.toEntity().payload)
        assertEquals(TriggerPayload.EMPTY, activation.toEntity().toActivation().payload)
    }

    @Test
    fun `an unreadable payload reads as empty`() {
        val row = Activation(scenarioId = "ha-washer", since = 1).toEntity().copy(payload = "{tohle není JSON")

        assertEquals(TriggerPayload.EMPTY, row.toActivation().payload)
    }

    @Test
    fun `an activation expires only at its expiry time`() {
        val activation = Activation(scenarioId = "ha-gate", since = 1_000, expiresAt = 2_000)

        assertFalse(activation.isExpired(now = 1_999))
        assertTrue(activation.isExpired(now = 2_000))
        assertTrue(activation.isExpired(now = 5_000))
    }

    @Test
    fun `a stateful activation never expires`() {
        val activation = Activation(scenarioId = "evening-home", since = 1_000)

        assertFalse(activation.isExpired(now = Long.MAX_VALUE))
    }
}
