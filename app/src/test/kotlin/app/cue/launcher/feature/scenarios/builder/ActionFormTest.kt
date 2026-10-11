package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.ha.HaProbe
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.system.ConfirmationResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActionFormTest {

    @Test
    fun `an empty link is a reason, not a crash`() {
        assertEquals(ActionFormError.URI_EMPTY, uriActionError("   "))
        assertNull(uriActionOf(""))
    }

    @Test
    fun `a file link is rejected the same as in the executor`() {
        assertEquals(ActionFormError.URI_UNUSABLE, uriActionError("file:///sdcard/foto.jpg"))
    }

    @Test
    fun `a valid link passes and gets trimmed`() {
        assertNull(uriActionError("  spotify:search  "))
        assertEquals("spotify:search", uriActionOf("  spotify:search  ")?.uri)
    }

    @Test
    fun `a value without a scheme passes`() {
        assertNull(uriActionError("www.seznam.cz"))
    }

    @Test
    fun `the service is required and needs a dot between domain and name`() {
        assertEquals(ActionFormError.HA_SERVICE_EMPTY, haActionError(service = "", data = ""))
        assertEquals(ActionFormError.HA_SERVICE_FORMAT, haActionError(service = "turn_off", data = ""))
        assertNull(haActionError(service = "light.turn_off", data = ""))
    }

    @Test
    fun `the entity is optional`() {
        val action = haActionOf(entity = "  ", service = "script.vecer", data = "")

        assertNull(action?.entity)
        assertEquals("script.vecer", action?.service)
    }

    @Test
    fun `the icon state entity is optional and normalized`() {
        val plain = haActionOf(entity = "lock.front_door", service = "lock.open", data = "", stateEntity = "  ")
        val door = haActionOf(
            entity = "lock.front_door",
            service = "lock.open",
            data = "",
            stateEntity = " binary_sensor.Front_Door ",
        )

        assertNull(plain?.stateEntity)
        assertEquals("binary_sensor.front_door", door?.stateEntity)
        assertEquals("lock.front_door", door?.entity)
    }

    @Test
    fun `the icon state takes exactly one entity id`() {
        listOf("front_door", "binary_sensor.a, binary_sensor.b", "{{PLACEHOLDER:door}}").forEach { stateEntity ->
            assertEquals(
                ActionFormError.HA_STATE_ENTITY_FORMAT,
                haActionError(service = "lock.open", data = "", stateEntity = stateEntity),
                stateEntity,
            )
            assertNull(haActionOf(entity = "", service = "lock.open", data = "", stateEntity = stateEntity))
        }
        assertNull(haActionError(service = "lock.open", data = "", stateEntity = "binary_sensor.front_door"))
    }

    @Test
    fun `data must be an object`() {
        assertEquals(ActionFormError.HA_DATA, haActionError(service = "light.turn_on", data = "[1, 2]"))
        assertEquals(ActionFormError.HA_DATA, haActionError(service = "light.turn_on", data = "nesmysl"))
        assertNull(haActionError(service = "light.turn_on", data = """{"transition": 2}"""))
    }

    @Test
    fun `data is carried over including values`() {
        val action = haActionOf(
            entity = "light.living_room",
            service = "light.turn_on",
            data = """{"brightness": 200}""",
        )

        assertEquals(JsonPrimitive(200), action?.data?.get("brightness"))
        assertEquals("light.living_room", action?.entity)
    }

    @Test
    fun `empty data is an empty object`() {
        assertTrue(haActionOf(entity = "", service = "light.turn_off", data = "  ")?.data?.isEmpty() == true)
    }

    @Test
    fun `the test result maps into the row state`() {
        assertEquals(HaTestState.OK, haTestStateOf(HaTestResult.Ok(millis = 240)))
        assertEquals(HaTestState.FAILED, haTestStateOf(HaTestResult.Failed(detail = "401")))
        assertEquals(HaTestState.NOT_CONFIGURED, haTestStateOf(HaTestResult.NotConfigured))
    }

    @Test
    fun `an HA call maps into the test result`() {
        assertEquals(HaTestResult.Ok(millis = 240), haTestResultOf(HaProbe.Ok(entities = 1, millis = 240)))
        assertEquals(HaTestResult.Failed("HTTP 401"), haTestResultOf(HaProbe.Http(401)))
        assertEquals(HaTestResult.Failed("timeout"), haTestResultOf(HaProbe.Network("timeout")))
        assertEquals(HaTestResult.NotConfigured, haTestResultOf(HaProbe.NotConfigured))
    }

    @Test
    fun `an action without confirmation is tested without a prompt`() = runTest {
        var prompts = 0
        val result = haConfirmedTest(haAction(confirm = false), { prompts++; ConfirmationResult.GRANTED }) {
            HaTestResult.Ok(millis = 10)
        }

        assertEquals(HaTestResult.Ok(millis = 10), result)
        assertEquals(0, prompts)
    }

    // The Test button sends the real call, so it must not be the way around the prompt a tap shows.
    @Test
    fun `an action marked for confirmation is tested only after the prompt passes`() = runTest {
        var calls = 0
        val call: suspend () -> HaTestResult = { calls++; HaTestResult.Ok(millis = 10) }

        assertEquals(HaTestResult.Ok(millis = 10), haConfirmedTest(haAction(), { ConfirmationResult.GRANTED }, call))
        assertEquals(HaTestResult.Dismissed, haConfirmedTest(haAction(), { ConfirmationResult.DISMISSED }, call))
        assertEquals(
            HaTestResult.ConfirmationUnavailable,
            haConfirmedTest(haAction(), { ConfirmationResult.UNAVAILABLE }, call),
        )
        assertEquals(1, calls)
    }

    @Test
    fun `a refused confirmation leaves the action untested`() {
        assertEquals(HaTestState.NOT_RUN, haTestStateOf(HaTestResult.Dismissed))
        assertEquals(HaTestState.CONFIRMATION_UNAVAILABLE, haTestStateOf(HaTestResult.ConfirmationUnavailable))
    }

    private fun haAction(confirm: Boolean = true) =
        TapAction.HaAction(entity = "lock.front_door", service = "lock.unlock", confirm = confirm)
}
