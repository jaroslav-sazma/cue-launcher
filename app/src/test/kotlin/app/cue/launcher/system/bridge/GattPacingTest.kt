package app.cue.launcher.system.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GattPacingTest {

    private val requested = mutableListOf<Int>()
    private var granted = true
    private val pacing = GattPacing(request = { priority ->
        requested += priority
        granted
    })

    @Test
    fun `nothing is asked of a link that is not subscribed`() {
        pacing.channelClosed(pacing.channelOpened())

        assertEquals(emptyList<Int>(), requested)
    }

    @Test
    fun `a subscribed link idles at low power, once`() {
        pacing.subscribed()
        pacing.subscribed()

        assertEquals(listOf(GattPacing.LOW_POWER), requested)
    }

    @Test
    fun `an L2CAP channel raises the link to balanced until the last one closes`() {
        pacing.subscribed()
        val first = pacing.channelOpened()
        val second = pacing.channelOpened()
        pacing.channelClosed(first)
        pacing.channelClosed(first)
        pacing.channelClosed(second)

        assertEquals(listOf(GattPacing.LOW_POWER, GattPacing.BALANCED, GattPacing.LOW_POWER), requested)
    }

    @Test
    fun `a channel opened before the subscription is honoured by it`() {
        val channel = pacing.channelOpened()
        pacing.subscribed()
        pacing.channelClosed(channel)

        assertEquals(listOf(GattPacing.BALANCED, GattPacing.LOW_POWER), requested)
    }

    @Test
    fun `a disconnect forgets the channels, and a late close of an old one asks for nothing`() {
        pacing.subscribed()
        val old = pacing.channelOpened()
        pacing.disconnected()
        pacing.subscribed()
        pacing.channelClosed(old)

        assertEquals(listOf(GattPacing.LOW_POWER, GattPacing.BALANCED, GattPacing.LOW_POWER), requested)
    }

    @Test
    fun `a refused request is asked again at the next change`() {
        granted = false
        pacing.subscribed()
        granted = true
        val channel = pacing.channelOpened()
        pacing.channelClosed(channel)

        assertEquals(listOf(GattPacing.LOW_POWER, GattPacing.BALANCED, GattPacing.LOW_POWER), requested)
    }
}
