package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.TriggerPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EndEventRulesTest {

    @Test
    fun `a notification end matches the package regardless of case and padding`() {
        val until = EndEvent.Notification(packageName = " Com.Example.Parcel ")

        assertTrue(endEventMatches(until, post(packageName = "com.example.parcel")))
        assertFalse(endEventMatches(until, post(packageName = "com.example.other")))
    }

    @Test
    fun `a notification end with contains needs the text in the title or the text`() {
        val until = EndEvent.Notification(packageName = PARCEL_APP, contains = " Delivered ")

        assertTrue(endEventMatches(until, post(title = "Parcel delivered")))
        assertTrue(endEventMatches(until, post(text = "It was DELIVERED today")))
        assertFalse(endEventMatches(until, post(title = "Out for delivery")))
    }

    @Test
    fun `a blank contains matches any notification of the app`() {
        assertTrue(endEventMatches(EndEvent.Notification(packageName = PARCEL_APP, contains = "  "), post()))
    }

    @Test
    fun `a group summary never ends anything`() {
        val summary = post().copy(isGroupSummary = true)

        assertFalse(endEventMatches(EndEvent.Notification(packageName = PARCEL_APP), summary))
    }

    @Test
    fun `a disconnect end without an address matches any device`() {
        assertTrue(endEventMatches(EndEvent.BtDisconnected(mac = null), CAR))
        assertTrue(endEventMatches(EndEvent.BtDisconnected(mac = CAR.lowercase()), CAR))
        assertFalse(endEventMatches(EndEvent.BtDisconnected(mac = "11:22:33:44:55:66"), CAR))
    }

    @Test
    fun `a Home Assistant end matches the code as the event condition does`() {
        assertTrue(endEventMatches(EndEvent.HaEvent(code = "Washer_Done"), "washer_done"))
        assertTrue(endEventMatches(EndEvent.HaEvent(code = "washer_done"), " WASHER_DONE "))
        assertFalse(endEventMatches(EndEvent.HaEvent(code = "washer_done"), "washer_start"))
        assertFalse(endEventMatches(EndEvent.HaEvent(code = "  "), ""))
    }

    @Test
    fun `an end that could never arrive is recognised`() {
        assertFalse(endEventCanArrive(EndEvent.Notification(packageName = " ")))
        assertFalse(endEventCanArrive(EndEvent.HaEvent(code = " ")))
        assertTrue(endEventCanArrive(EndEvent.Notification(packageName = PARCEL_APP)))
        assertTrue(endEventCanArrive(EndEvent.HaEvent(code = "x")))
        assertTrue(endEventCanArrive(EndEvent.Alarm))
        assertTrue(endEventCanArrive(EndEvent.BtDisconnected()))
    }

    @Test
    fun `each event ends only the activations waiting for it`() {
        val running = listOf(
            ending("parcel", EndEvent.Notification(packageName = PARCEL_APP, contains = "delivered")),
            ending("car", EndEvent.BtDisconnected(mac = CAR)),
            ending("headphones", EndEvent.BtDisconnected(mac = "11:22:33:44:55:66")),
            ending("washer", EndEvent.HaEvent(code = "washer_done")),
            ending("wake", EndEvent.Alarm),
            Activation("countdown", since = 0L, expiresAt = MINUTE),
            Activation("state", since = 0L),
        )

        assertEquals(listOf("parcel"), endedByNotification(running, post(title = "Delivered")).ids())
        assertEquals(emptyList<String>(), endedByNotification(running, post(title = "On its way")).ids())
        assertEquals(listOf("car"), endedByBluetoothDisconnect(running, CAR).ids())
        assertEquals(listOf("washer"), endedByHaEvent(running, "WASHER_DONE").ids())
        assertEquals(listOf("wake"), endedByAlarm(running, rangAt = MINUTE).ids())
    }

    @Test
    fun `an alarm is judged against the last fire, not the first`() {
        val refired = ending("refired", EndEvent.Alarm, firedAt = 2 * MINUTE)
        val atTheRing = ending("at-ring", EndEvent.Alarm, firedAt = MINUTE)
        val before = ending("before", EndEvent.Alarm, firedAt = MINUTE - 1)
        val withoutFireTime = ending("plain", EndEvent.Alarm)

        assertEquals(
            listOf("before", "plain"),
            endedByAlarm(listOf(refired, atTheRing, before, withoutFireTime), rangAt = MINUTE).ids(),
        )
    }

    @Test
    fun `a state-held activation carries no end, so no comparison sees it`() {
        val held = Activation("held", since = 0L, payload = TriggerPayload(fields = mapOf("ha:x" to "1")))

        assertEquals(emptyList<String>(), endedByAlarm(listOf(held), rangAt = MINUTE).ids())
        assertEquals(emptyList<String>(), endedByNotification(listOf(held), post()).ids())
    }

    @Test
    fun `the log counts every scenario waiting for the code, to start or to end`() {
        val starts = scenario("start", conditions = listOf(Condition.HaEvent(code = "door")))
        val ends = scenario(
            "end",
            conditions = listOf(Condition.Alarm(until = EndEvent.HaEvent(code = "door"))),
        )
        val off = ends.copy(id = "off", enabled = false)
        val definitions = listOf(starts, ends, off)
        val listeners = haEventListeners(definitions, "door")

        assertEquals(2, haEventWaiters(definitions, listeners, ended = listOf("end"), code = "door"))
        assertEquals(3, haEventWaiters(definitions, listeners, ended = listOf("edited"), code = "door"))
        assertEquals(0, haEventWaiters(definitions, emptyList(), ended = emptyList(), code = "bell"))
    }

    private fun List<Activation>.ids() = map(Activation::scenarioId)

    private fun ending(id: String, until: EndEvent, firedAt: Long? = null) =
        Activation(id, since = 0L, payload = TriggerPayload(until = until, firedAt = firedAt))

    private fun post(
        packageName: String = PARCEL_APP,
        title: String? = "Parcel",
        text: String? = null,
    ) = PostedNotification(key = "k", packageName = packageName, title = title, text = text)

    private companion object {
        const val PARCEL_APP = "com.example.parcel"
        const val CAR = "AA:BB:CC:DD:EE:FF"
        const val MINUTE = 60_000L
    }
}
