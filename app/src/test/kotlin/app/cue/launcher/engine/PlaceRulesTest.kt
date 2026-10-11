package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TriggerPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaceRulesTest {

    @Test
    fun `a place survives the trip through the payload`() {
        val place = SavedPlace(latitude = 50.087451, longitude = 14.420671, savedAt = 1_000L)

        assertEquals(place, savedPlaceOf(placePayload(place)))
    }

    @Test
    fun `a payload without coordinates holds no place`() {
        assertNull(savedPlaceOf(TriggerPayload(fields = mapOf("notif:title" to "Arriving"))))
        assertNull(savedPlaceOf(TriggerPayload(fields = mapOf("place:lat" to "91", "place:lng" to "14"))))
    }

    @Test
    fun `the newest place wins and an expired one is forgotten`() {
        val older = SavedPlace(50.0, 14.0, savedAt = 1_000L)
        val newer = SavedPlace(48.2, 16.37, savedAt = 2_000L)
        val running = listOf(
            Activation("a", since = 0L, expiresAt = 10_000L, payload = placePayload(older)),
            Activation("b", since = 0L, expiresAt = 10_000L, payload = placePayload(newer)),
        )

        assertEquals(newer, latestSavedPlace(running, now = 5_000L))
        assertNull(latestSavedPlace(running, now = 10_000L))
    }

    @Test
    fun `the link drops a pin on the coordinates`() {
        assertEquals(
            "geo:50.087451,14.420671?q=50.087451,14.420671",
            placeUri(SavedPlace(50.087451, 14.420671, savedAt = 0L)),
        )
    }

    @Test
    fun `saving answers the running tiles that asked for it`() {
        val prompt = scenario("save", conditions = listOf(Condition.BtDisconnected()), content = REMEMBER)
        val idle = scenario("idle", conditions = listOf(Condition.BtDisconnected()), content = REMEMBER)
        val shown = scenario("shown", conditions = listOf(Condition.PlaceSaved()), content = REMEMBER)
        val running = listOf(Activation("save", since = 0L), Activation("shown", since = 0L))

        assertEquals(listOf("save"), answeredPlacePrompts(listOf(prompt, idle, shown), running))
    }

    @Test
    fun `only a scenario that remembers a place needs the location`() {
        assertTrue(usesPlace(listOf(scenario("save", content = REMEMBER))))
        assertFalse(usesPlace(listOf(scenario("other"))))
    }

    @Test
    fun `only an enabled scenario waits for a place`() {
        val shown = scenario("shown", conditions = listOf(Condition.PlaceSaved(durationSec = 3600)))

        assertEquals(listOf(shown), placeSavedListeners(listOf(shown, scenario("other"))))
        assertTrue(placeSavedListeners(listOf(shown.copy(enabled = false))).isEmpty())
    }
}

private val REMEMBER = TileContent.Template(
    layout = TemplateLayout.ICON_TEXT,
    items = listOf(TemplateItem(actions = listOf(TapAction.SystemAction(SystemActionType.REMEMBER_PLACE)))),
)
