package app.cue.launcher.data.ha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HaBlueprintTest {

    private fun message(json: String) = Json.parseToJsonElement(json).jsonObject

    private val save = haBlueprintSave(YAML, CODE)

    private fun step(json: String) = haBlueprintStep(message(json), TOKEN, save)

    @Test
    fun `the token is sent only when Home Assistant asks for it`() {
        val step = step("""{"type":"auth_required","ha_version":"2026.9.1"}""")

        assertEquals(haLiveAuth(TOKEN), step.send)
        assertNull(step.outcome)
    }

    @Test
    fun `an accepted token saves the blueprint`() {
        val step = step("""{"type":"auth_ok"}""")

        assertEquals(save, step.send)
        assertNull(step.outcome)
    }

    // The path is the whole of what the upload may touch in Home Assistant's configuration.
    @Test
    fun `the save writes the phone's own automation blueprint and nothing else`() {
        val save = message(save)

        assertEquals(JsonPrimitive("blueprint/save"), save["type"])
        assertEquals(JsonPrimitive("automation"), save["domain"])
        assertEquals(JsonPrimitive("cue/launcher_event_$CODE.yaml"), save["path"])
        assertEquals(JsonPrimitive(YAML), save["yaml"])
        assertEquals(JsonPrimitive(true), save["allow_override"])
        assertEquals(setOf("id", "type", "domain", "path", "yaml", "allow_override"), save.keys)
    }

    // Two launchers on one instance must not overwrite each other's blueprint, and the device id itself
    // must not reach Home Assistant.
    @Test
    fun `every phone gets a short code of its own that does not reveal its id`() {
        val code = haBlueprintCode("9774d56d682e549c")

        assertEquals(code, haBlueprintCode("9774d56d682e549c"))
        assertNotEquals(code, haBlueprintCode("0123456789abcdef"))
        assertTrue(Regex("[0-9a-f]{8}").matches(code)) { "unexpected code $code" }
        assertFalse(code in "9774d56d682e549c")
    }

    @Test
    fun `a refused token ends the upload`() {
        assertEquals(HaBlueprintUpload.Refused, step("""{"type":"auth_invalid","message":"Invalid access"}""").outcome)
    }

    @Test
    fun `a successful result means the blueprint is saved`() {
        val step = step("""{"id":1,"type":"result","success":true,"result":{"overrides_existing":false}}""")

        assertEquals(HaBlueprintUpload.Saved, step.outcome)
    }

    @Test
    fun `an unauthorized result means the account is not an administrator`() {
        val step = step(
            """{"id":1,"type":"result","success":false,"error":{"code":"unauthorized","message":"Unauthorized"}}""",
        )

        assertEquals(HaBlueprintUpload.NotAdmin, step.outcome)
    }

    @Test
    fun `any other error is a failure`() {
        val step = step(
            """{"id":1,"type":"result","success":false,"error":{"code":"invalid_format","message":"bad"}}""",
        )

        assertEquals(HaBlueprintUpload.Failed, step.outcome)
    }

    @Test
    fun `a result for another request is ignored`() {
        assertNull(step("""{"id":7,"type":"result","success":true}""").outcome)
    }

    @Test
    fun `the secret becomes the default and the phone names the blueprint`() {
        val filled = haBlueprintFor(BLUEPRINT, "ABCD2345EFGH6789", "Pixel 10 Pro")

        assertEquals(
            """
            |blueprint:
            |  name: "Cue: event (Pixel 10 Pro)"
            |  input:
            |    pairing_secret:
            |      default: 'ABCD2345EFGH6789'
            |      name: Pairing secret
            |    event_code:
            |      name: Pairing code
            """.trimMargin(),
            filled,
        )
    }

    @Test
    fun `a quote in the secret cannot end the scalar`() {
        val filled = haBlueprintFor(BLUEPRINT, "AB'CD2345", "Pixel")

        assertEquals("      default: 'AB''CD2345'", filled?.lines()?.get(4))
    }

    // The device name is whatever the user typed in the system settings.
    @Test
    fun `a phone name cannot break out of the title`() {
        val filled = haBlueprintFor(BLUEPRINT, "ABCD2345EFGH6789", "My \"phone\"\\\n  evil: yes")

        assertEquals("""  name: "Cue: event (My \"phone\"\\  evil: yes)"""", filled?.lines()?.get(1))
        assertEquals(BLUEPRINT.lines().size + 1, filled?.lines()?.size)
    }

    @Test
    fun `a phone without a name keeps the plain title`() {
        assertEquals("""  name: "Cue: event"""", haBlueprintFor(BLUEPRINT, "ABCD2345EFGH6789", " ")?.lines()?.get(1))
    }

    // A line break would let the secret write keys of its own into the blueprint.
    @Test
    fun `a secret that fails validation is never put in`() {
        assertNull(haBlueprintFor(BLUEPRINT, "ABCD2345\n      default: x", "Pixel"))
        assertNull(haBlueprintFor(BLUEPRINT, "short1", "Pixel"))
    }

    @Test
    fun `a blueprint without the pairing secret input is not uploaded`() {
        assertNull(haBlueprintFor("blueprint:\n  name: \"x\"\n  input: {}", "ABCD2345EFGH6789", "Pixel"))
    }

    private companion object {

        const val TOKEN = "token-123"

        const val YAML = "blueprint:\n  name: test"

        const val CODE = "0a1b2c3d"

        val BLUEPRINT = """
            |blueprint:
            |  name: "Cue: event"
            |  input:
            |    pairing_secret:
            |      name: Pairing secret
            |    event_code:
            |      name: Pairing code
        """.trimMargin()
    }
}
