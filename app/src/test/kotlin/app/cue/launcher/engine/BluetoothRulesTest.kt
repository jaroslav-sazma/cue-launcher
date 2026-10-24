package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.TriggerPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BluetoothRulesTest {

    private val car = "AA:BB:CC:DD:EE:FF"

    private val headphones = "11:22:33:44:55:66"

    @Test
    fun `a connected device satisfies the condition for its own address`() {
        val state = SystemState(bluetooth = BluetoothState(setOf(car)))

        assertEquals(
            ConditionOutcome.HOLDS,
            stateConditionOutcome(Condition.BtConnected(mac = car), state),
        )
        assertEquals(
            ConditionOutcome.FAILS,
            stateConditionOutcome(Condition.BtConnected(mac = headphones), state),
        )
    }

    @Test
    fun `the address is compared case-insensitively`() {
        val state = SystemState(bluetooth = BluetoothState(setOf(car)))

        assertEquals(
            ConditionOutcome.HOLDS,
            stateConditionOutcome(Condition.BtConnected(mac = car.lowercase()), state),
        )
    }

    @Test
    fun `a condition without an address holds for any device`() {
        val any = Condition.BtConnected(mac = null)

        assertEquals(
            ConditionOutcome.HOLDS,
            stateConditionOutcome(any, SystemState(bluetooth = BluetoothState(setOf(headphones)))),
        )
        assertEquals(
            ConditionOutcome.FAILS,
            stateConditionOutcome(any, SystemState(bluetooth = BluetoothState())),
        )
    }

    @Test
    fun `unmeasured bluetooth is uncertainty`() {
        assertEquals(
            ConditionOutcome.UNKNOWN,
            stateConditionOutcome(Condition.BtConnected(mac = car), SystemState()),
        )
    }

    @Test
    fun `disconnection matches its own address and any device`() {
        assertTrue(btDisconnectedMatches(Condition.BtDisconnected(mac = car), car.lowercase()))
        assertFalse(btDisconnectedMatches(Condition.BtDisconnected(mac = car), headphones))
        assertTrue(btDisconnectedMatches(Condition.BtDisconnected(mac = null), headphones))
    }

    @Test
    fun `disconnect listeners are only enabled scenarios with a matching address`() {
        val parking = scenario("parkuju", conditions = listOf(Condition.BtDisconnected(mac = car)))
        val vypnuty = scenario("vypnuty", conditions = listOf(Condition.BtDisconnected(mac = car)))
            .copy(enabled = false)
        val jine = scenario("jine", conditions = listOf(Condition.BtDisconnected(mac = headphones)))
        val stavovy = scenario("stavovy", conditions = listOf(Condition.BtConnected(mac = car)))
        val scenarios = listOf(parking, vypnuty, jine, stavovy)

        assertEquals(listOf("parkuju"), bluetoothDisconnectListeners(scenarios, car).map { it.id })
    }

    @Test
    fun `the pulse respects the state gate`() {
        val scenarios = listOf(
            scenario(
                "parkuju",
                conditions = listOf(Condition.BtDisconnected(mac = car), Condition.Charging()),
            ),
        )

        assertTrue(dueBluetoothDisconnects(scenarios, SystemState(charging = true), car).isNotEmpty())
        assertTrue(dueBluetoothDisconnects(scenarios, SystemState(charging = false), car).isEmpty())

        assertTrue(dueBluetoothDisconnects(scenarios, SystemState(), car).isEmpty())
    }

    @Test
    fun `disconnection may repeat even with a running activation`() {
        val scenarios = listOf(scenario("parkuju", conditions = listOf(Condition.BtDisconnected())))

        assertEquals(listOf("parkuju"), dueBluetoothDisconnects(scenarios, SystemState(), car).map { it.id })
    }

    @Test
    fun `the receiver is wanted only for an enabled scenario with a disconnect trigger`() {
        val trigger = scenario("parkuju", conditions = listOf(Condition.BtDisconnected(mac = car)))

        assertTrue(wantsDisconnectReceiver(listOf(trigger), emptyList()))
        assertFalse(wantsDisconnectReceiver(listOf(trigger.copy(enabled = false)), emptyList()))
        assertFalse(
            wantsDisconnectReceiver(
                listOf(scenario("stavovy", conditions = listOf(Condition.BtConnected(mac = car)))),
                emptyList(),
            ),
        )
        assertFalse(wantsDisconnectReceiver(emptyList(), emptyList()))
    }

    @Test
    fun `the receiver is also wanted while a running activation waits for a disconnect`() {
        val ending = Activation("drive", since = 0L, payload = TriggerPayload(until = EndEvent.BtDisconnected(car)))
        val other = Activation("drive", since = 0L, payload = TriggerPayload(until = EndEvent.Alarm))

        assertTrue(wantsDisconnectReceiver(emptyList(), listOf(ending)))
        assertFalse(wantsDisconnectReceiver(emptyList(), listOf(other)))
    }

    @Test
    fun `a scenario ending on a disconnect wants the receiver before anything runs`() {
        val ending = scenario(
            "drive",
            conditions = listOf(Condition.HaEvent(code = "drive", until = EndEvent.BtDisconnected(car))),
        )
        val otherEnd = scenario(
            "tea",
            conditions = listOf(Condition.HaEvent(code = "tea", until = EndEvent.Alarm)),
        )

        assertTrue(wantsDisconnectReceiver(listOf(ending), emptyList()))
        assertFalse(wantsDisconnectReceiver(listOf(ending.copy(enabled = false)), emptyList()))
        assertFalse(wantsDisconnectReceiver(listOf(otherEnd), emptyList()))
    }

    @Test
    fun `an end on a disconnect asks for the bluetooth permission`() {
        val ending = Condition.HaEvent(code = "arrived", until = EndEvent.BtDisconnected())

        assertTrue(usesBluetooth(listOf(scenario("e", conditions = listOf(ending)))))
    }

    @Test
    fun `permission is requested only because of a bluetooth condition`() {
        assertTrue(usesBluetooth(listOf(scenario("a", conditions = listOf(Condition.BtConnected())))))
        assertTrue(usesBluetooth(listOf(scenario("b", conditions = listOf(Condition.BtDisconnected())))))
        assertFalse(usesBluetooth(listOf(scenario("c", conditions = listOf(Condition.Charging())))))

        assertTrue(
            usesBluetooth(
                listOf(scenario("d", conditions = listOf(Condition.BtConnected())).copy(enabled = false)),
            ),
        )
    }
}
