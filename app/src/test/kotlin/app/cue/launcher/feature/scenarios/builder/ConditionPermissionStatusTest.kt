package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.WifiMode
import app.cue.launcher.feature.scenarios.JitPermission
import app.cue.launcher.feature.scenarios.JitRequirement
import app.cue.launcher.feature.scenarios.SystemAccess
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConditionPermissionStatusTest {

    private val ssid = Condition.Wifi(mode = WifiMode.SSID, ssid = "Doma")

    @Test
    fun `a specific network reports both location and services`() {
        val status = conditionPermissionStatus(
            condition = ssid,
            access = SystemAccess(locationGranted = true),
        )

        assertEquals(
            listOf(JitRequirement.Grant(JitPermission.LOCATION), JitRequirement.LocationServices),
            status.map { it.requirement },
        )
        assertEquals(listOf(true, false), status.map { it.granted })
    }

    @Test
    fun `an option without permission has nothing to report`() {
        val status = conditionPermissionStatus(
            condition = Condition.Wifi(mode = WifiMode.ANY),
            access = SystemAccess(),
        )

        assertTrue(status.isEmpty())
    }

    @Test
    fun `bluetooth reports permission even for any device`() {
        val status = conditionPermissionStatus(
            condition = Condition.BtConnected(),
            access = SystemAccess(bluetoothGranted = true),
        )

        assertEquals(listOf(JitRequirement.Grant(JitPermission.BLUETOOTH)), status.map { it.requirement })
        assertEquals(listOf(true), status.map { it.granted })
    }

    @Test
    fun `an ongoing event reports calendar`() {
        val missing = conditionRequirements(Condition.CalendarEvent(), SystemAccess())
        val granted = conditionRequirements(Condition.CalendarEvent(), SystemAccess(calendarGranted = true))

        assertEquals(listOf(JitRequirement.Grant(JitPermission.CALENDAR)), missing)
        assertTrue(granted.isEmpty())
    }

    @Test
    fun `notification reports notification access`() {
        val condition = Condition.NotificationFrom(packageName = "cz.zasilkovna.app")
        val missing = conditionRequirements(condition, SystemAccess())
        val granted = conditionRequirements(condition, SystemAccess(notificationListenerEnabled = true))

        assertEquals(listOf(JitRequirement.NotificationListener), missing)
        assertTrue(granted.isEmpty())
    }

    @Test
    fun `an end event asks for what its condition counterpart needs`() {
        val notificationEnd = Condition.HaEvent(code = "door", until = EndEvent.Notification(packageName = "com.watch"))
        val disconnectEnd = Condition.Alarm(until = EndEvent.BtDisconnected())

        assertEquals(
            listOf(JitRequirement.NotificationListener),
            conditionRequirements(notificationEnd, SystemAccess()),
        )
        assertEquals(
            listOf(JitRequirement.Grant(JitPermission.BLUETOOTH)),
            conditionRequirements(disconnectEnd, SystemAccess()),
        )
        assertTrue(conditionRequirements(Condition.Alarm(until = EndEvent.Alarm), SystemAccess()).isEmpty())
    }

    @Test
    fun `something playing requests notification access`() {
        val missing = conditionRequirements(Condition.MediaPlaying(), SystemAccess())
        val granted = conditionRequirements(Condition.MediaPlaying(), SystemAccess(notificationListenerEnabled = true))

        assertEquals(listOf(JitRequirement.NotificationListener), missing)
        assertTrue(granted.isEmpty())
    }

    @Test
    fun `a notification condition reports access even when it has it`() {
        val status = conditionPermissionStatus(
            condition = Condition.NotificationFrom(packageName = "cz.zasilkovna.app"),
            access = SystemAccess(notificationListenerEnabled = true),
        )

        assertEquals(listOf(JitRequirement.NotificationListener), status.map { it.requirement })
        assertEquals(listOf(true), status.map { it.granted })
    }
}
