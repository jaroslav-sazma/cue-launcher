package app.cue.launcher.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BluetoothDevicesTest {

    private val headphones = ConnectedBtDevice(address = "AA:BB:CC:00:00:01", name = "Headphones")
    private val car = ConnectedBtDevice(address = "AA:BB:CC:00:00:02", name = "Car")

    @Test
    fun `a paired device that is connected is offered only once`() {
        assertEquals(listOf(car), pairedNotConnected(connected = listOf(headphones), paired = listOf(headphones, car)))
    }

    @Test
    fun `the address comparison ignores case`() {
        val lowerCase = headphones.copy(address = headphones.address.lowercase())

        assertEquals(emptyList<ConnectedBtDevice>(), pairedNotConnected(listOf(headphones), listOf(lowerCase)))
    }

    @Test
    fun `nothing connected leaves every paired device`() {
        assertEquals(listOf(headphones, car), pairedNotConnected(emptyList(), listOf(headphones, car)))
    }
}
