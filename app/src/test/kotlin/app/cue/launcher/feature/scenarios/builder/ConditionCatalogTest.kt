package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionType
import app.cue.launcher.engine.DEFAULT_INSTANT_DURATION_SEC
import app.cue.launcher.engine.MacBridgeState
import app.cue.launcher.engine.SystemState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConditionCatalogTest {

    @Test
    fun `the catalog contains every condition type exactly once except the set time`() {
        assertEquals(ConditionType.entries.toSet() - ConditionType.TIME_INSTANT, CONDITION_CATALOG.toSet())
        assertEquals(ConditionType.entries.size - 1, CONDITION_CATALOG.size)
    }

    @Test
    fun `Mac locked is offered only with a Mac paired, Mac nearby always`() {
        val unpaired = conditionCatalog(bridgePaired = false)

        assertFalse(ConditionType.MAC_LOCKED in unpaired)
        assertTrue(ConditionType.MAC_NEARBY in unpaired)
        assertEquals(CONDITION_CATALOG - ConditionType.MAC_LOCKED, unpaired)
        assertEquals(CONDITION_CATALOG, conditionCatalog(bridgePaired = true))
    }

    @Test
    fun `a bridge counts as paired only once it reads as more than off`() {
        assertFalse(SystemState().bridgePaired)
        assertFalse(SystemState(macBridge = MacBridgeState.OFF).bridgePaired)
        assertTrue(SystemState(macBridge = MacBridgeState.AWAY).bridgePaired)
        assertTrue(SystemState(macBridge = MacBridgeState.NEARBY).bridgePaired)
    }

    @Test
    fun `a moment in time starts at the alarm`() {
        val moment = newCondition(ConditionType.ALARM)

        assertTrue(moment is Condition.Alarm)
        assertNull(conditionFormError(moment))
    }

    @Test
    fun `sections have eleven state and six instant`() {
        val sections = catalogSections(CONDITION_CATALOG)

        assertEquals(listOf(ConditionSection.STATE, ConditionSection.INSTANT), sections.map { it.section })
        assertEquals(STATE_COUNT, sections[0].types.size)
        assertEquals(INSTANT_COUNT, sections[1].types.size)
    }

    @Test
    fun `an empty section is omitted`() {
        val sections = catalogSections(listOf(ConditionType.WIFI, ConditionType.CHARGING))

        assertEquals(1, sections.size)
        assertEquals(ConditionSection.STATE, sections.single().section)
    }

    @Test
    fun `a new condition is pre-filled`() {
        val window = newCondition(ConditionType.TIME_WINDOW) as Condition.TimeWindow

        assertEquals("18:00", window.from)
        assertEquals("23:00", window.to)
        assertNull(conditionFormError(window))
    }

    @Test
    fun `both bluetooth and wifi start on the option without permission`() {
        val bluetooth = newCondition(ConditionType.BT_CONNECTED) as Condition.BtConnected
        val wifi = newCondition(ConditionType.WIFI) as Condition.Wifi

        assertNull(bluetooth.mac)
        assertEquals(app.cue.launcher.data.scenario.WifiMode.ANY, wifi.mode)
    }

    @Test
    fun `instant conditions carry a default duration`() {
        val instants = CONDITION_CATALOG.filter(ConditionType::isInstant)
            .filterNot { it in LONGER_DEFAULTS }
            .map(::newCondition)

        assertTrue(instants.all { it is Condition.InstantCondition })
        instants.forEach { condition ->
            assertEquals(
                DEFAULT_INSTANT_DURATION_SEC,
                (condition as Condition.InstantCondition).durationSec,
            )
        }
    }

    @Test
    fun `the end of a notification starts shaped for navigation`() {
        val ended = newCondition(ConditionType.NOTIFICATION_ENDED) as Condition.NotificationEnded

        assertTrue(ended.ongoing)
        assertEquals(120, ended.minLastedSec)
        assertEquals(30 * 60, ended.durationSec)
        assertEquals(ConditionFormError.NOTIFICATION_PACKAGE, conditionFormError(ended))
        assertNull(conditionFormError(ended.copy(packageName = "com.google.android.apps.maps")))
    }

    @Test
    fun `a remembered place is kept for a day`() {
        val saved = newCondition(ConditionType.PLACE_SAVED) as Condition.PlaceSaved

        assertEquals(24 * 60 * 60, saved.durationSec)
        assertNull(conditionFormError(saved))
    }

    @Test
    fun `the type can be recognized back from every condition`() {
        ConditionType.entries.forEach { type ->
            assertEquals(type, conditionTypeOf(newCondition(type)))
        }
    }

    @Test
    fun `the section is determined by the condition's nature`() {
        assertEquals(ConditionSection.STATE, conditionSection(ConditionType.RINGER_MODE))
        assertEquals(ConditionSection.INSTANT, conditionSection(ConditionType.HA_EVENT))
    }

    private companion object {
        const val STATE_COUNT = 11
        const val INSTANT_COUNT = 6

        val LONGER_DEFAULTS = setOf(ConditionType.NOTIFICATION_ENDED, ConditionType.PLACE_SAVED)
    }
}
