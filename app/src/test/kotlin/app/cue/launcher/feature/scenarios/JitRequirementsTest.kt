package app.cue.launcher.feature.scenarios

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.data.scenario.WifiMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JitRequirementsTest {

    private val home = Condition.Wifi(mode = WifiMode.SSID, ssid = "Doma-5G")

    private val denied = SystemAccess(locationEnabled = true)

    @Test
    fun `a scenario without sensitive conditions requests nothing`() {
        val scenarios = listOf(scenario(listOf(Condition.Charging())))

        assertTrue(requirements(scenarios).isEmpty())
    }

    @Test
    fun `any wifi does not need location`() {
        val scenarios = listOf(scenario(listOf(Condition.Wifi(mode = WifiMode.ANY))))

        assertTrue(requirements(scenarios, denied.copy(locationEnabled = false)).isEmpty())
    }

    @Test
    fun `a specific network requests both location and services`() {
        val scenarios = listOf(scenario(listOf(home)))

        assertEquals(
            listOf(JitRequirement.Grant(JitPermission.LOCATION), JitRequirement.LocationServices),
            requirements(scenarios, denied.copy(locationEnabled = false)),
        )
    }

    @Test
    fun `disabled services remain even with permission granted`() {
        val scenarios = listOf(scenario(listOf(home)))

        assertEquals(
            listOf(JitRequirement.LocationServices),
            requirements(scenarios, denied.copy(locationGranted = true, locationEnabled = false)),
        )
    }

    @Test
    fun `with everything granted, nothing remains`() {
        val scenarios = listOf(scenario(listOf(home, Condition.BtConnected())))

        assertTrue(
            requirements(scenarios, denied.copy(bluetoothGranted = true, locationGranted = true)).isEmpty(),
        )
    }

    @Test
    fun `the order is permissions, then settings`() {
        val scenarios = listOf(scenario(listOf(home, Condition.BtDisconnected())))

        assertEquals(
            listOf(
                JitRequirement.Grant(JitPermission.BLUETOOTH),
                JitRequirement.Grant(JitPermission.LOCATION),
                JitRequirement.LocationServices,
            ),
            requirements(scenarios, denied.copy(locationEnabled = false)),
        )
    }

    @Test
    fun `location is requested as a pair and the precise one decides`() {
        assertEquals(
            listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"),
            JitPermission.LOCATION.requested,
        )
        assertEquals("android.permission.ACCESS_FINE_LOCATION", JitPermission.LOCATION.decisive)
    }

    @Test
    fun `a ringer-switching action requests do not disturb access`() {
        val scenarios = listOf(
            scenario(listOf(Condition.Charging()), TapAction.SystemAction(SystemActionType.DND_ON)),
        )

        assertEquals(listOf(JitRequirement.NotificationPolicy), requirements(scenarios))
        assertTrue(requirements(scenarios, denied.copy(dndAccessGranted = true)).isEmpty())
    }

    @Test
    fun `neither flashlight nor panel request access`() {
        val scenarios = listOf(
            scenario(emptyList(), TapAction.SystemAction(SystemActionType.FLASHLIGHT)),
            scenario(emptyList(), TapAction.SystemAction(SystemActionType.PANEL_VOLUME)),
        )

        assertTrue(requirements(scenarios).isEmpty())
    }

    @Test
    fun `a scenario with a calendar requests calendar access`() {
        val scenarios = listOf(scenario(listOf(Condition.CalendarEvent())))

        assertEquals(listOf(JitRequirement.Grant(JitPermission.CALENDAR)), requirements(scenarios))
        assertTrue(requirements(scenarios, denied.copy(calendarGranted = true)).isEmpty())
    }

    @Test
    fun `a scenario with a notification requests notification access`() {
        val scenarios = listOf(scenario(listOf(Condition.NotificationFrom(packageName = "cz.zasilkovna.app"))))

        assertEquals(listOf(JitRequirement.NotificationListener), requirements(scenarios))
        assertTrue(requirements(scenarios, denied.copy(notificationListenerEnabled = true)).isEmpty())
    }

    @Test
    fun `notifications rank before do not disturb and both rank behind dialogs`() {
        val scenarios = listOf(
            scenario(
                listOf(Condition.BtConnected(), Condition.NotificationFrom(packageName = "cz.zasilkovna.app")),
                TapAction.SystemAction(SystemActionType.DND_ON),
            ),
        )

        assertEquals(
            listOf(
                JitRequirement.Grant(JitPermission.BLUETOOTH),
                JitRequirement.NotificationListener,
                JitRequirement.NotificationPolicy,
            ),
            requirements(scenarios),
        )
    }

    @Test
    fun `playback and notification request a single access`() {
        val scenarios = listOf(
            scenario(listOf(Condition.MediaPlaying())),
            scenario(listOf(Condition.NotificationFrom(packageName = "cz.zasilkovna.app"))),
        )

        assertEquals(listOf(JitRequirement.NotificationListener), requirements(scenarios))
        assertTrue(requirements(scenarios, denied.copy(notificationListenerEnabled = true)).isEmpty())
    }

    @Test
    fun `do not disturb ranks behind permission dialogs`() {
        val scenarios = listOf(
            scenario(listOf(Condition.BtConnected()), TapAction.SystemAction(SystemActionType.RINGER_SILENT)),
        )

        assertEquals(
            listOf(JitRequirement.Grant(JitPermission.BLUETOOTH), JitRequirement.NotificationPolicy),
            requirements(scenarios),
        )
    }

    private fun requirements(
        scenarios: List<Scenario>,
        access: SystemAccess = denied,
    ): List<JitRequirement> = jitRequirements(scenarios = scenarios, access = access)

    private fun scenario(
        conditions: List<Condition>,
        action: TapAction = TapAction.App(packageName = "com.example"),
    ): Scenario = Scenario(
        id = "test",
        name = "Test",
        priority = 0,
        size = TileSize.GRID_1X1,
        conditions = conditions,
        content = TileContent.Template(
            layout = TemplateLayout.ICON_TEXT,
            items = listOf(TemplateItem(actions = listOf(action))),
        ),
    )
}
