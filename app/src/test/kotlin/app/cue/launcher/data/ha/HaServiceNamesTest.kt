package app.cue.launcher.data.ha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HaServiceNamesTest {

    private fun message(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun step(text: String) = haServiceNamesStep(message(text), token = "secret", language = "cs")

    @Test
    fun `English needs no names from the socket, any other language does`() {
        assertFalse(haServiceNamesNeeded("en"))
        assertTrue(haServiceNamesNeeded("cs"))
    }

    @Test
    fun `the socket authenticates first and asks for the service translations after`() {
        val auth = message(step("""{"type": "auth_required"}""").send!!)
        assertEquals("auth", auth["type"]!!.jsonPrimitive.content)
        assertEquals("secret", auth["access_token"]!!.jsonPrimitive.content)

        val request = message(step("""{"type": "auth_ok"}""").send!!)
        assertEquals("frontend/get_translations", request["type"]!!.jsonPrimitive.content)
        assertEquals("cs", request["language"]!!.jsonPrimitive.content)
        assertEquals("services", request["category"]!!.jsonPrimitive.content)
        assertEquals(1, request["id"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `a refused token ends the exchange with no names`() {
        assertEquals(emptyMap<String, String>(), step("""{"type": "auth_invalid"}""").names)
    }

    @Test
    fun `the result carries the service names and nothing else`() {
        val result = step(
            """
            {"id": 1, "type": "result", "success": true, "result": {"resources": {
              "component.light.services.toggle.name": "Přepnout",
              "component.light.services.toggle.description": "Přepne světlo.",
              "component.light.services.turn_on.fields.brightness.name": "Jas",
              "component.cover.services.open_cover.name": "  Otevřít  ",
              "component.cover.services.close_cover.name": " ",
              "component.light.title": "Světlo"
            }}}
            """.trimIndent(),
        )

        assertEquals(mapOf("light.toggle" to "Přepnout", "cover.open_cover" to "Otevřít"), result.names)
    }

    @Test
    fun `a failed or foreign result is no answer to wait for`() {
        assertEquals(
            emptyMap<String, String>(),
            step("""{"id": 1, "type": "result", "success": false, "error": {"code": "unknown_command"}}""").names,
        )
        assertNull(step("""{"id": 7, "type": "result", "success": true, "result": {}}""").names)
        assertNull(step("""{"type": "pong"}""").names)
    }
}
