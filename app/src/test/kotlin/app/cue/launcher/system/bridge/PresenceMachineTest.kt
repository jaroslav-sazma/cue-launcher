package app.cue.launcher.system.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PresenceMachineTest {

    // Plays inputs at virtual times and fires the debounce the way the window does: one timer at a
    // time, replaced by every Arm, dropped by Cancel, delivered with the token it was armed with.
    private class Timeline {

        val machine = PresenceMachine()

        var now = 0L
            private set

        private var timer: Pair<Long, Long>? = null

        val absences = mutableListOf<Long>()

        val armed get() = timer != null

        fun at(time: Long, input: PresenceInput) {
            advanceTo(time)
            apply(machine.on(input))
        }

        fun advanceTo(time: Long) {
            require(time >= now)
            timer?.let { (due, token) ->
                if (due <= time) {
                    now = due
                    timer = null
                    apply(machine.expired(token))
                }
            }
            now = time
        }

        private fun apply(step: PresenceStep) {
            when (val order = step.debounce) {
                is DebounceOrder.Arm -> timer = now + PRESENCE_DEBOUNCE_MS to order.token
                DebounceOrder.Cancel -> timer = null
                DebounceOrder.Keep -> Unit
            }
            if (!step.present && absences.lastOrNull() != now && wasPresent) absences += now
            wasPresent = step.present
        }

        private var wasPresent = false
    }

    @Test
    fun `the Mac is absent until the system first reports it`() {
        val machine = PresenceMachine()

        assertFalse(machine.present)
        assertEquals(PresenceStep(present = false), machine.on(PresenceInput.CDM_DISAPPEARED))
        assertEquals(PresenceStep(present = false), machine.on(PresenceInput.GATT_DISCONNECTED))
    }

    @Test
    fun `an appearance makes the Mac present at once`() {
        val machine = PresenceMachine()

        assertEquals(PresenceStep(present = true), machine.on(PresenceInput.CDM_APPEARED))
    }

    @Test
    fun `a disappearance only arms the debounce`() {
        val machine = PresenceMachine()
        machine.on(PresenceInput.CDM_APPEARED)

        val step = machine.on(PresenceInput.CDM_DISAPPEARED)

        assertTrue(step.present)
        assertTrue(step.debounce is DebounceOrder.Arm)
    }

    @Test
    fun `the debounce expiring without a sign of the Mac makes it absent`() {
        val machine = PresenceMachine()
        machine.on(PresenceInput.CDM_APPEARED)
        val arm = machine.on(PresenceInput.CDM_DISAPPEARED).debounce as DebounceOrder.Arm

        assertEquals(PresenceStep(present = false), machine.expired(arm.token))
        assertFalse(machine.present)
    }

    @Test
    fun `a reappearance cancels the debounce`() {
        val machine = PresenceMachine()
        machine.on(PresenceInput.CDM_APPEARED)
        val arm = machine.on(PresenceInput.CDM_DISAPPEARED).debounce as DebounceOrder.Arm

        assertEquals(PresenceStep(present = true, DebounceOrder.Cancel), machine.on(PresenceInput.CDM_APPEARED))
        assertEquals(PresenceStep(present = true), machine.expired(arm.token))
    }

    @Test
    fun `an expiry from a cancelled debounce changes nothing, even after a new one was armed`() {
        val machine = PresenceMachine()
        machine.on(PresenceInput.CDM_APPEARED)
        val first = machine.on(PresenceInput.CDM_DISAPPEARED).debounce as DebounceOrder.Arm
        machine.on(PresenceInput.CDM_APPEARED)
        val second = machine.on(PresenceInput.CDM_DISAPPEARED).debounce as DebounceOrder.Arm

        assertTrue(first.token != second.token)
        assertTrue(machine.expired(first.token).present)
        assertFalse(machine.expired(second.token).present)
    }

    @Test
    fun `one debounce at a time, so a second disappearance does not rearm it`() {
        val machine = PresenceMachine()
        machine.on(PresenceInput.CDM_APPEARED)
        machine.on(PresenceInput.CDM_DISAPPEARED)

        assertEquals(PresenceStep(present = true), machine.on(PresenceInput.CDM_DISAPPEARED))
        assertEquals(PresenceStep(present = true), machine.on(PresenceInput.GATT_DISCONNECTED))
    }

    @Test
    fun `while the authenticated GATT link is up the system's disappearance is ignored`() {
        val machine = PresenceMachine()
        machine.on(PresenceInput.CDM_APPEARED)
        machine.on(PresenceInput.GATT_CONNECTED)
        machine.on(PresenceInput.GATT_AUTHENTICATED)

        assertTrue(machine.linked)
        assertEquals(PresenceStep(present = true), machine.on(PresenceInput.CDM_DISAPPEARED))
    }

    @Test
    fun `a bare GATT connection is not a link, so the system's disappearance still arms the debounce`() {
        val machine = PresenceMachine()
        machine.on(PresenceInput.CDM_APPEARED)
        machine.on(PresenceInput.GATT_CONNECTED)

        assertFalse(machine.linked)
        assertTrue(machine.on(PresenceInput.CDM_DISAPPEARED).debounce is DebounceOrder.Arm)
    }

    @Test
    fun `a device answering under the Mac's address with nothing that opens does not keep the Mac present`() {
        val timeline = Timeline()
        timeline.at(0, PresenceInput.CDM_APPEARED)
        timeline.at(1_000, PresenceInput.GATT_CONNECTED)
        timeline.at(10_000, PresenceInput.CDM_DISAPPEARED)
        timeline.advanceTo(HOUR)

        assertFalse(timeline.machine.present)
        assertFalse(timeline.machine.linked)
        assertEquals(listOf(70_000L), timeline.absences)
    }

    @Test
    fun `only a frame that opens over a connection that is up links it, and the link ends with the connection`() {
        val machine = PresenceMachine()
        machine.on(PresenceInput.CDM_APPEARED)

        machine.on(PresenceInput.GATT_AUTHENTICATED)
        assertFalse(machine.linked, "a frame that arrived after the connection dropped")

        machine.on(PresenceInput.GATT_CONNECTED)
        machine.on(PresenceInput.GATT_AUTHENTICATED)
        assertTrue(machine.linked)

        machine.on(PresenceInput.GATT_DISCONNECTED)
        assertFalse(machine.linked)
        machine.on(PresenceInput.GATT_CONNECTED)
        assertFalse(machine.linked, "a reconnect waits for a frame of its own")
    }

    @Test
    fun `an authenticated frame is a sign of the Mac and cancels a running debounce`() {
        val machine = PresenceMachine()
        machine.on(PresenceInput.CDM_APPEARED)
        machine.on(PresenceInput.GATT_CONNECTED)
        machine.on(PresenceInput.CDM_DISAPPEARED)

        assertEquals(PresenceStep(present = true, DebounceOrder.Cancel), machine.on(PresenceInput.GATT_AUTHENTICATED))
    }

    @Test
    fun `a GATT connection is a sign of the Mac and cancels a running debounce`() {
        val machine = PresenceMachine()
        machine.on(PresenceInput.CDM_APPEARED)
        machine.on(PresenceInput.CDM_DISAPPEARED)

        assertEquals(PresenceStep(present = true, DebounceOrder.Cancel), machine.on(PresenceInput.GATT_CONNECTED))
    }

    @Test
    fun `losing the GATT link with the system silent arms the debounce`() {
        val machine = PresenceMachine()
        machine.on(PresenceInput.CDM_APPEARED)
        machine.on(PresenceInput.GATT_CONNECTED)
        machine.on(PresenceInput.GATT_AUTHENTICATED)
        machine.on(PresenceInput.CDM_DISAPPEARED)

        val step = machine.on(PresenceInput.GATT_DISCONNECTED)

        assertTrue(step.present)
        assertTrue(step.debounce is DebounceOrder.Arm)
    }

    @Test
    fun `a GATT reconnect within the debounce keeps the Mac present`() {
        val timeline = Timeline()
        timeline.at(0, PresenceInput.CDM_APPEARED)
        timeline.at(1_000, PresenceInput.GATT_CONNECTED)
        timeline.at(5_000, PresenceInput.GATT_DISCONNECTED)
        timeline.at(50_000, PresenceInput.GATT_CONNECTED)
        timeline.advanceTo(HOUR)

        assertTrue(timeline.machine.present)
        assertEquals(emptyList<Long>(), timeline.absences)
    }

    // Measured with the phone locked next to the Mac: a false absence of about a second, recurring
    // with a median period of eleven seconds, a few of 12 and 22.5 seconds, and stretches of 21 s
    // between.
    @Test
    fun `the measured flapping never makes the Mac absent, without a GATT link`() {
        val timeline = Timeline()
        timeline.at(0, PresenceInput.CDM_APPEARED)
        var t = 0L
        var cycle = 0
        while (t < HALF_HOUR) {
            t += if (cycle % 5 == 4) 21_000 else 9_500
            timeline.at(t, PresenceInput.CDM_DISAPPEARED)
            t += when (cycle % 37) {
                12 -> 12_000
                30 -> 22_500
                else -> 1_010
            }
            timeline.at(t, PresenceInput.CDM_APPEARED)
            cycle++
        }
        timeline.advanceTo(t + 1)

        assertTrue(timeline.machine.present)
        assertEquals(emptyList<Long>(), timeline.absences)
        assertTrue(cycle > 100)
    }

    @Test
    fun `the measured flapping never makes the Mac absent with the GATT link up, and arms nothing`() {
        val timeline = Timeline()
        timeline.at(0, PresenceInput.CDM_APPEARED)
        timeline.at(2_000, PresenceInput.GATT_CONNECTED)
        timeline.at(2_500, PresenceInput.GATT_AUTHENTICATED)
        var t = 2_500L
        repeat(FLAPS) {
            t += 9_500
            timeline.at(t, PresenceInput.CDM_DISAPPEARED)
            assertFalse(timeline.armed)
            t += 1_000
            timeline.at(t, PresenceInput.CDM_APPEARED)
        }

        assertTrue(timeline.machine.present)
        assertEquals(emptyList<Long>(), timeline.absences)
    }

    // Measured with Bluetooth switched off as a stand-in for leaving: the system reported the
    // absence after 10 s; the debounce makes it count 60 s later, and the Mac's return 2.8 s after
    // Bluetooth came back is at once.
    @Test
    fun `a genuine absence counts once the debounce has run out, and the return at once`() {
        val timeline = Timeline()
        timeline.at(0, PresenceInput.CDM_APPEARED)
        timeline.at(1_000, PresenceInput.GATT_CONNECTED)
        timeline.at(20_000, PresenceInput.GATT_DISCONNECTED)
        timeline.at(30_000, PresenceInput.CDM_DISAPPEARED)

        timeline.advanceTo(79_999)
        assertTrue(timeline.machine.present)
        timeline.advanceTo(80_000)
        assertFalse(timeline.machine.present)
        assertEquals(listOf(80_000L), timeline.absences)

        timeline.at(200_000, PresenceInput.CDM_APPEARED)
        assertTrue(timeline.machine.present)
    }

    @Test
    fun `the 67-second absence of the walk counts as one absence`() {
        val timeline = Timeline()
        timeline.at(0, PresenceInput.CDM_APPEARED)
        timeline.at(10_000, PresenceInput.CDM_DISAPPEARED)
        timeline.at(77_100, PresenceInput.CDM_APPEARED)

        assertEquals(listOf(70_000L), timeline.absences)
        assertTrue(timeline.machine.present)
    }

    @Test
    fun `an expiry while the GATT link is up leaves the Mac present`() {
        val machine = PresenceMachine()
        machine.on(PresenceInput.CDM_APPEARED)
        val arm = machine.on(PresenceInput.CDM_DISAPPEARED).debounce as DebounceOrder.Arm
        machine.on(PresenceInput.GATT_CONNECTED)

        assertTrue(machine.expired(arm.token).present)
    }

    // The stack reports no disconnect when the phone's radio turns off, and the system may keep the
    // Mac nearby throughout.
    @Test
    fun `the radio turning off ends the link and the debounce makes the Mac absent`() {
        val timeline = Timeline()
        timeline.at(0, PresenceInput.CDM_APPEARED)
        timeline.at(1_000, PresenceInput.GATT_CONNECTED)
        timeline.at(1_500, PresenceInput.GATT_AUTHENTICATED)

        timeline.at(10_000, PresenceInput.BLUETOOTH_OFF)
        assertFalse(timeline.machine.linked)
        assertTrue(timeline.armed)
        assertTrue(timeline.machine.present)

        timeline.advanceTo(HOUR)
        assertFalse(timeline.machine.present)
        assertEquals(listOf(70_000L), timeline.absences)
    }

    @Test
    fun `turning the radio off again changes nothing`() {
        val machine = PresenceMachine()
        machine.on(PresenceInput.CDM_APPEARED)
        assertTrue(machine.on(PresenceInput.BLUETOOTH_OFF).debounce is DebounceOrder.Arm)

        assertEquals(PresenceStep(present = true), machine.on(PresenceInput.BLUETOOTH_OFF))
    }

    @Test
    fun `a GATT connection after the radio came back within the debounce keeps the Mac present`() {
        val timeline = Timeline()
        timeline.at(0, PresenceInput.CDM_APPEARED)
        timeline.at(1_000, PresenceInput.GATT_CONNECTED)
        timeline.at(1_500, PresenceInput.GATT_AUTHENTICATED)
        timeline.at(10_000, PresenceInput.BLUETOOTH_OFF)
        timeline.at(20_000, PresenceInput.BLUETOOTH_ON)
        assertTrue(timeline.armed)

        timeline.at(23_000, PresenceInput.GATT_CONNECTED)
        assertFalse(timeline.armed)
        timeline.advanceTo(HOUR)

        assertTrue(timeline.machine.present)
        assertEquals(emptyList<Long>(), timeline.absences)
    }

    @Test
    fun `the radio coming on just before the off's debounce runs out gets a debounce of its own`() {
        val timeline = Timeline()
        timeline.at(0, PresenceInput.CDM_APPEARED)
        timeline.at(1_000, PresenceInput.GATT_CONNECTED)
        timeline.at(1_500, PresenceInput.GATT_AUTHENTICATED)
        timeline.at(10_000, PresenceInput.BLUETOOTH_OFF)
        timeline.at(69_000, PresenceInput.BLUETOOTH_ON)

        timeline.at(72_000, PresenceInput.GATT_CONNECTED)
        timeline.advanceTo(HOUR)

        assertTrue(timeline.machine.present)
        assertEquals(emptyList<Long>(), timeline.absences)
    }

    @Test
    fun `a frame queued from before the radio went off does not cancel the off's debounce`() {
        val timeline = Timeline()
        timeline.at(0, PresenceInput.CDM_APPEARED)
        timeline.at(1_000, PresenceInput.GATT_CONNECTED)
        timeline.at(1_500, PresenceInput.GATT_AUTHENTICATED)
        timeline.at(10_000, PresenceInput.BLUETOOTH_OFF)

        timeline.at(10_100, PresenceInput.GATT_AUTHENTICATED)
        assertTrue(timeline.armed)
        assertFalse(timeline.machine.linked)
        timeline.advanceTo(HOUR)

        assertFalse(timeline.machine.present)
        assertEquals(listOf(70_000L), timeline.absences)
    }

    @Test
    fun `a failed attendance makes the Mac absent at once, even over a link`() {
        val machine = PresenceMachine()
        machine.on(PresenceInput.CDM_APPEARED)
        machine.on(PresenceInput.GATT_CONNECTED)
        machine.on(PresenceInput.GATT_AUTHENTICATED)

        assertEquals(PresenceStep(present = false, DebounceOrder.Cancel), machine.on(PresenceInput.ATTENDANCE_FAILED))
        assertFalse(machine.present)
        assertFalse(machine.linked)
    }

    @Test
    fun `a failed attendance drops a running debounce and nothing but a new appearance brings the Mac back`() {
        val machine = PresenceMachine()
        machine.on(PresenceInput.CDM_APPEARED)
        machine.on(PresenceInput.GATT_CONNECTED)
        val arm = machine.on(PresenceInput.GATT_DISCONNECTED).debounce as DebounceOrder.Arm
        machine.on(PresenceInput.ATTENDANCE_FAILED)

        assertEquals(PresenceStep(present = false), machine.expired(arm.token))
        assertEquals(PresenceStep(present = false), machine.on(PresenceInput.GATT_AUTHENTICATED))
        assertEquals(PresenceStep(present = false), machine.on(PresenceInput.CDM_DISAPPEARED))
        assertEquals(PresenceStep(present = false), machine.on(PresenceInput.GATT_DISCONNECTED))
        assertEquals(PresenceStep(present = true), machine.on(PresenceInput.CDM_APPEARED))
    }

    @Test
    fun `after a failed attendance the radio coming on puts the Mac on trial while the system holds it nearby`() {
        val machine = PresenceMachine()
        machine.on(PresenceInput.CDM_APPEARED)
        machine.on(PresenceInput.ATTENDANCE_FAILED)

        val on = machine.on(PresenceInput.BLUETOOTH_ON)

        assertTrue(on.present)
        assertTrue(on.debounce is DebounceOrder.Arm)
    }

    @Test
    fun `the radio coming on while the system still holds the Mac nearby makes it present on trial`() {
        val machine = PresenceMachine()
        machine.on(PresenceInput.CDM_APPEARED)
        val off = machine.on(PresenceInput.BLUETOOTH_OFF).debounce as DebounceOrder.Arm
        machine.expired(off.token)
        assertFalse(machine.present)

        val on = machine.on(PresenceInput.BLUETOOTH_ON)

        assertTrue(on.present)
        assertTrue(on.debounce is DebounceOrder.Arm)
        assertFalse(machine.linked)
    }

    @Test
    fun `a trial that no GATT connection confirms ends in absence`() {
        val timeline = Timeline()
        timeline.at(0, PresenceInput.CDM_APPEARED)
        timeline.at(10_000, PresenceInput.BLUETOOTH_OFF)
        timeline.advanceTo(80_000)
        timeline.at(100_000, PresenceInput.BLUETOOTH_ON)
        assertTrue(timeline.machine.present)

        timeline.advanceTo(HOUR)

        assertFalse(timeline.machine.present)
        assertEquals(listOf(70_000L, 160_000L), timeline.absences)
    }

    @Test
    fun `the radio coming on after the system's last word was a disappearance leaves the Mac absent`() {
        val machine = PresenceMachine()
        machine.on(PresenceInput.CDM_APPEARED)
        val arm = machine.on(PresenceInput.CDM_DISAPPEARED).debounce as DebounceOrder.Arm
        machine.expired(arm.token)

        assertEquals(PresenceStep(present = false), machine.on(PresenceInput.BLUETOOTH_ON))
        assertFalse(machine.present)
    }

    @Test
    fun `the radio coming on without an off that was seen still unlinks and arms the debounce`() {
        val machine = PresenceMachine()
        machine.on(PresenceInput.CDM_APPEARED)
        machine.on(PresenceInput.GATT_CONNECTED)
        machine.on(PresenceInput.GATT_AUTHENTICATED)

        val step = machine.on(PresenceInput.BLUETOOTH_ON)

        assertFalse(machine.linked)
        assertTrue(step.present)
        assertTrue(step.debounce is DebounceOrder.Arm)
    }

    @Test
    fun `after the radio came back the system's disappearance counts again`() {
        val machine = PresenceMachine()
        machine.on(PresenceInput.CDM_APPEARED)
        machine.on(PresenceInput.GATT_CONNECTED)
        machine.on(PresenceInput.GATT_AUTHENTICATED)
        machine.on(PresenceInput.BLUETOOTH_ON)
        machine.on(PresenceInput.GATT_CONNECTED)

        val step = machine.on(PresenceInput.CDM_DISAPPEARED)

        assertFalse(machine.linked)
        assertTrue(step.debounce is DebounceOrder.Arm)
    }

    private companion object {
        const val HALF_HOUR = 30 * 60_000L
        const val HOUR = 60 * 60_000L
        const val FLAPS = 200
    }
}
