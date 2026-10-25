package app.cue.launcher.data.ha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HaLiveTest {

    private val entities = setOf("sensor.a", "sensor.b")

    private fun message(json: String) = Json.parseToJsonElement(json).jsonObject

    private fun step(state: HaLiveState, json: String) = haLiveStep(state, message(json), entities, TOKEN)

    @Test
    fun `the token is sent only when Home Assistant asks for it`() {
        val step = step(HaLiveState(), """{"type":"auth_required","ha_version":"2026.9.1"}""")

        assertEquals(haLiveAuth(TOKEN), step.send)
        assertTrue(step.send?.contains(TOKEN) == true)
        assertFalse(step.state.authorized)
        assertFalse(step.emit)
    }

    // The subscription names the entities: an instance that sent everything it knows would be a tile's
    // worth of data turned into the whole state machine of the house.
    @Test
    fun `an accepted token subscribes to the entities the tiles refer to`() {
        val step = step(HaLiveState(), """{"type":"auth_ok"}""")

        assertTrue(step.state.authorized)
        assertEquals(haLiveSubscribe(entities), step.send)
        assertTrue(step.send?.contains("sensor.a") == true)
        assertTrue(step.send?.contains("subscribe_entities") == true)
    }

    @Test
    fun `a refused token ends the channel instead of being retried`() {
        val step = step(HaLiveState(authorized = false), """{"type":"auth_invalid","message":"Invalid access token"}""")

        assertTrue(step.refused)
        assertNull(step.send)
    }

    @Test
    fun `the handshake announces the version of the instance`() {
        val required = step(HaLiveState(), """{"type":"auth_required","ha_version":"2026.9.1"}""").state
        val ok = step(required, """{"type":"auth_ok"}""").state

        assertEquals("2026.9.1", required.version)
        assertEquals("2026.9.1", ok.version)
    }

    @Test
    fun `an event carries the attributes that choose the icon`() {
        val step = step(
            HaLiveState(),
            """{"id":1,"type":"event","event":{"a":{"sensor.a":{"s":"21.5","a":{"device_class":"temperature"}}}}}""",
        )

        assertEquals(mapOf("sensor.a" to HaIconAttributes(deviceClass = "temperature")), step.state.attributes)
    }

    // Authenticating proves nothing on its own: the caller has to keep polling until an event arrives,
    // or a channel that dies right after auth_ok would silently freeze every value on the screen.
    @Test
    fun `only an event counts as a channel that works`() {
        val authorized = step(HaLiveState(), """{"type":"auth_ok"}""").state

        assertFalse(authorized.streamed)

        val streamed = step(authorized, """{"id":1,"type":"event","event":{"a":{"sensor.a":{"s":"21.5"}}}}""")

        assertTrue(streamed.state.streamed)
        assertTrue(streamed.emit)
        assertEquals(mapOf("sensor.a" to "21.5"), streamed.state.values)
    }

    // An event the parser cannot read proves no more than authenticating did. Counting it would keep
    // the caller from falling back to REST, and every tile would freeze on the value it opened with.
    @Test
    fun `an event with nothing the launcher understands does not count as a working channel`() {
        val step = step(HaLiveState(), """{"id":1,"type":"event"}""")

        assertFalse(step.state.streamed)
        assertFalse(step.emit)
    }

    @Test
    fun `an event that resolves to no value still counts, the entities are simply unavailable`() {
        val step = step(
            HaLiveState(),
            """{"id":1,"type":"event","event":{"a":{"sensor.a":{"s":"unavailable"}}}}""",
        )

        assertTrue(step.state.streamed)
        assertEquals(emptyMap<String, String>(), step.state.values)
    }

    @Test
    fun `an entity nobody asked about is not kept`() {
        val step = step(
            HaLiveState(),
            """{"id":1,"type":"event","event":{"a":{"sensor.a":{"s":"1"},"sensor.z":{"s":"2"}}}}""",
        )

        assertEquals(mapOf("sensor.a" to "1"), step.state.values)
    }

    @Test
    fun `a difference replaces the value the entity had`() {
        val first = step(HaLiveState(), """{"id":1,"type":"event","event":{"a":{"sensor.a":{"s":"21.5"}}}}""")
        val second = step(first.state, """{"id":1,"type":"event","event":{"c":{"sensor.a":{"+":{"s":"22.0"}}}}}""")

        assertEquals(mapOf("sensor.a" to "22.0"), second.state.values)
    }

    // The same rule as on the REST path: a state that cannot be shown takes the value away rather than
    // leaving the tile with the one from a minute ago.
    @Test
    fun `an entity that goes unavailable loses its value`() {
        val known = step(HaLiveState(), """{"id":1,"type":"event","event":{"a":{"sensor.a":{"s":"21.5"}}}}""").state
        val gone = step(known, """{"id":1,"type":"event","event":{"c":{"sensor.a":{"+":{"s":"unavailable"}}}}}""")

        assertEquals(emptyMap<String, String>(), gone.state.values)
    }

    @Test
    fun `a removed entity is dropped`() {
        val known = step(HaLiveState(), """{"id":1,"type":"event","event":{"a":{"sensor.a":{"s":"21.5"}}}}""").state
        val gone = step(known, """{"id":1,"type":"event","event":{"r":["sensor.a"]}}""")

        assertEquals(emptyMap<String, String>(), gone.state.values)
    }

    // Everything here arrives over a socket, so a frame shaped differently than expected must not be
    // able to throw: the channel would fall back to polling for the rest of the session.
    @Test
    fun `a frame with the wrong shape changes nothing`() {
        val known = step(HaLiveState(), """{"id":1,"type":"event","event":{"a":{"sensor.a":{"s":"21.5"}}}}""").state

        val shapes = listOf(
            """{"type":{"nested":true}}""",
            """{"id":1,"type":"event"}""",
            """{"id":1,"type":"event","event":{"a":{"sensor.a":"21.5"}}}""",
            """{"id":1,"type":"event","event":{"c":{"sensor.a":[1,2]}}}""",
            """{"id":1,"type":"event","event":{"r":[{"sensor.a":1}]}}""",
            """{"id":1,"type":"result","success":true}""",
        )

        shapes.forEach { json ->
            val step = step(known, json)
            assertEquals(known.values, step.state.values, json)
            assertNull(step.send, json)
            assertFalse(step.refused, json)
        }
    }

    // A channel that keeps dropping right after it opened must not be reconnected every five seconds
    // for as long as the screen is on — that is a socket, a subscription and the whole state set again.
    @Test
    fun `a channel that keeps dropping is reconnected ever more slowly`() {
        val first = haLiveWait(HA_LIVE_RECONNECT_MILLIS, settled = false)
        val second = haLiveWait(first, settled = false)

        assertTrue(first > HA_LIVE_RECONNECT_MILLIS)
        assertTrue(second > first)
        assertEquals(HA_LIVE_POLL_MILLIS, haLiveWait(HA_LIVE_POLL_MILLIS, settled = false))
    }

    @Test
    fun `a channel that held starts over from the short delay`() {
        assertEquals(HA_LIVE_RECONNECT_MILLIS, haLiveWait(HA_LIVE_POLL_MILLIS, settled = true))
    }

    private companion object {
        const val TOKEN = "access-token"
    }
}
