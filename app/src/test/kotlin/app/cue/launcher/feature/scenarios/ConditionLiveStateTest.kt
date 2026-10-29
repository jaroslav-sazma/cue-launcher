package app.cue.launcher.feature.scenarios

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.RingerModeState
import app.cue.launcher.data.scenario.WifiMode
import app.cue.launcher.engine.BluetoothState
import app.cue.launcher.engine.ClockState
import app.cue.launcher.engine.HaStates
import app.cue.launcher.engine.MacBridgeState
import app.cue.launcher.engine.MacLockState
import app.cue.launcher.engine.MediaActivity
import app.cue.launcher.engine.SystemState
import app.cue.launcher.engine.WifiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConditionLiveStateTest {

    @Test
    fun `the media reading names how many apps it is about`() {
        val state = SystemState(media = MediaActivity(setOf("com.spotify.music")))

        val any = liveReading(Condition.MediaPlaying(), state)
        val one = liveReading(Condition.MediaPlaying(packages = listOf("com.spotify.music")), state)
        val two = liveReading(
            Condition.MediaPlaying(packages = listOf("com.spotify.music", "org.example.radio")),
            state,
        )
        val other = liveReading(Condition.MediaPlaying(packages = listOf("org.example.radio")), state)

        assertEquals(LiveVerdict.HOLDS to NowKey.MEDIA_PLAYING, any.verdict to any.now)
        assertEquals(LiveVerdict.HOLDS to NowKey.MEDIA_APP_PLAYING, one.verdict to one.now)
        assertEquals(LiveVerdict.HOLDS to NowKey.MEDIA_APPS_PLAYING, two.verdict to two.now)
        assertEquals(LiveVerdict.FAILS to NowKey.MEDIA_APP_SILENT, other.verdict to other.now)
    }

    @Test
    fun `a Home Assistant state names what the entity reports, or that nothing is known`() {
        val tv = Condition.HaState(entity = "media_player.tv", states = listOf("playing"))
        val reported = SystemState(ha = HaStates.of(mapOf("media_player.tv" to "Paused")))

        assertEquals(LiveReading(LiveVerdict.FAILS, NowKey.HA_STATE, "paused"), liveReading(tv, reported))
        assertEquals(
            LiveReading(LiveVerdict.UNKNOWN, NowKey.HA_UNKNOWN),
            liveReading(tv.copy(negate = true), SystemState(ha = HaStates())),
        )
    }

    @Test
    fun `media without notification access stays unmeasured, negated or not`() {
        val chosen = Condition.MediaPlaying(negate = true, packages = listOf("com.spotify.music"))

        val reading = liveReading(chosen, SystemState(media = null))

        assertEquals(LiveVerdict.UNKNOWN, reading.verdict)
        assertEquals(NowKey.MEDIA_UNKNOWN, reading.now)
    }

    @Test
    fun `a mismatched wifi reports the current network name`() {
        val reading = liveReading(
            condition = Condition.Wifi(mode = WifiMode.SSID, ssid = "Doma"),
            state = SystemState(wifi = WifiState(connected = true, ssid = "Hotel-Guest")),
        )

        assertEquals(LiveVerdict.FAILS, reading.verdict)
        assertEquals(NowKey.WIFI_SSID, reading.now)
        assertEquals("Hotel-Guest", reading.argument)
    }

    @Test
    fun `a matching wifi holds`() {
        val reading = liveReading(
            condition = Condition.Wifi(mode = WifiMode.SSID, ssid = "Doma"),
            state = SystemState(wifi = WifiState(connected = true, ssid = "Doma")),
        )

        assertEquals(LiveVerdict.HOLDS, reading.verdict)
    }

    @Test
    fun `wifi without a network name is unmeasured, not failing`() {
        val reading = liveReading(
            condition = Condition.Wifi(mode = WifiMode.SSID, ssid = "Doma"),
            state = SystemState(wifi = WifiState(connected = true, ssid = null)),
        )

        assertEquals(LiveVerdict.UNKNOWN, reading.verdict)
        assertEquals(NowKey.WIFI_HIDDEN, reading.now)
        assertNull(reading.argument)
    }

    @Test
    fun `any wifi does not ask for the network name`() {
        val reading = liveReading(
            condition = Condition.Wifi(mode = WifiMode.ANY),
            state = SystemState(wifi = WifiState(connected = true, ssid = null)),
        )

        assertEquals(LiveVerdict.HOLDS, reading.verdict)
        assertEquals(NowKey.WIFI_CONNECTED, reading.now)
        assertNull(reading.argument)
    }

    @Test
    fun `disconnected wifi is a measured answer`() {
        val reading = liveReading(
            condition = Condition.Wifi(mode = WifiMode.ANY),
            state = SystemState(wifi = WifiState(connected = false)),
        )

        assertEquals(LiveVerdict.FAILS, reading.verdict)
        assertEquals(NowKey.WIFI_DISCONNECTED, reading.now)
    }

    @Test
    fun `an unmeasured state is its own answer`() {
        val reading = liveReading(Condition.Charging(), SystemState())

        assertEquals(LiveVerdict.UNKNOWN, reading.verdict)
        assertEquals(NowKey.CHARGING_UNKNOWN, reading.now)
    }

    @Test
    fun `charging reports both sides`() {
        assertEquals(
            NowKey.CHARGING,
            liveReading(Condition.Charging(), SystemState(charging = true)).now,
        )
        assertEquals(
            NowKey.NOT_CHARGING,
            liveReading(Condition.Charging(), SystemState(charging = false)).now,
        )
    }

    @Test
    fun `the Mac reading tells a bridge that is off from a Mac that is away`() {
        val reading = { mac: MacBridgeState? -> liveReading(Condition.MacNearby(), SystemState(macBridge = mac)) }

        assertEquals(LiveReading(LiveVerdict.HOLDS, NowKey.MAC_NEARBY), reading(MacBridgeState.NEARBY))
        assertEquals(LiveReading(LiveVerdict.FAILS, NowKey.MAC_AWAY), reading(MacBridgeState.AWAY))
        assertEquals(LiveReading(LiveVerdict.FAILS, NowKey.MAC_BRIDGE_OFF), reading(MacBridgeState.OFF))
        assertEquals(LiveReading(LiveVerdict.UNKNOWN, NowKey.MAC_UNKNOWN), reading(null))
    }

    @Test
    fun `the Mac lock reading tells a locked screen from an absent or silent Mac`() {
        val reading = { mac: MacBridgeState?, lock: MacLockState? ->
            liveReading(Condition.MacLocked(), SystemState(macBridge = mac, macLock = lock))
        }

        assertEquals(
            LiveReading(LiveVerdict.HOLDS, NowKey.MAC_LOCKED),
            reading(MacBridgeState.NEARBY, MacLockState.LOCKED),
        )
        assertEquals(
            LiveReading(LiveVerdict.FAILS, NowKey.MAC_UNLOCKED),
            reading(MacBridgeState.NEARBY, MacLockState.UNLOCKED),
        )
        assertEquals(
            LiveReading(LiveVerdict.UNKNOWN, NowKey.MAC_LOCK_UNREPORTED),
            reading(MacBridgeState.NEARBY, MacLockState.UNREPORTED),
        )
        assertEquals(
            LiveReading(LiveVerdict.UNKNOWN, NowKey.MAC_LOCK_AWAY),
            reading(MacBridgeState.AWAY, MacLockState.UNREPORTED),
        )
        assertEquals(
            LiveReading(LiveVerdict.UNKNOWN, NowKey.MAC_LOCK_BRIDGE_OFF),
            reading(MacBridgeState.OFF, MacLockState.UNREPORTED),
        )
        assertEquals(LiveReading(LiveVerdict.UNKNOWN, NowKey.MAC_LOCK_UNKNOWN), reading(null, null))
    }

    @Test
    fun `Mac not locked cannot tell an absent or silent Mac either`() {
        val reading = { mac: MacBridgeState, lock: MacLockState ->
            liveReading(Condition.MacLocked(negate = true), SystemState(macBridge = mac, macLock = lock))
        }

        assertEquals(
            LiveReading(LiveVerdict.HOLDS, NowKey.MAC_UNLOCKED),
            reading(MacBridgeState.NEARBY, MacLockState.UNLOCKED),
        )
        assertEquals(
            LiveReading(LiveVerdict.FAILS, NowKey.MAC_LOCKED),
            reading(MacBridgeState.NEARBY, MacLockState.LOCKED),
        )
        assertEquals(
            LiveReading(LiveVerdict.UNKNOWN, NowKey.MAC_LOCK_UNREPORTED),
            reading(MacBridgeState.NEARBY, MacLockState.UNREPORTED),
        )
        assertEquals(
            LiveReading(LiveVerdict.UNKNOWN, NowKey.MAC_LOCK_AWAY),
            reading(MacBridgeState.AWAY, MacLockState.UNREPORTED),
        )
        assertEquals(
            LiveReading(LiveVerdict.UNKNOWN, NowKey.MAC_LOCK_BRIDGE_OFF),
            reading(MacBridgeState.OFF, MacLockState.UNREPORTED),
        )
    }

    @Test
    fun `bluetooth without a connected device is measured`() {
        val reading = liveReading(Condition.BtConnected(), SystemState(bluetooth = BluetoothState()))

        assertEquals(LiveVerdict.FAILS, reading.verdict)
        assertEquals(NowKey.BT_NONE, reading.now)
    }

    @Test
    fun `bluetooth without permission is unmeasured`() {
        assertEquals(NowKey.BT_UNKNOWN, liveReading(Condition.BtConnected(), SystemState()).now)
    }

    @Test
    fun `a time window carries the current time`() {
        val reading = liveReading(
            condition = Condition.TimeWindow(from = "18:00", to = "23:00"),
            state = SystemState(clock = ClockState(isoDayOfWeek = 2, minuteOfDay = 8 * 60 + 5)),
        )

        assertEquals(LiveVerdict.FAILS, reading.verdict)
        assertEquals(NowKey.TIME, reading.now)
        assertEquals("8:05", reading.argument)
    }

    @Test
    fun `do-not-disturb asks a different axis than the ringer mode`() {
        val state = SystemState(ringerMode = RingerModeState.VIBRATE, doNotDisturb = true)

        assertEquals(NowKey.DND_ON, liveReading(Condition.RingerMode(RingerModeState.DND), state).now)
        assertEquals(
            NowKey.RINGER_VIBRATE,
            liveReading(Condition.RingerMode(RingerModeState.SILENT), state).now,
        )
    }

    @Test
    fun `an instant condition waits for an event`() {
        listOf(
            Condition.HaEvent(code = "zvonek"),
            Condition.TimeInstant(at = "07:00"),
            Condition.BtDisconnected(),
            Condition.NotificationFrom(packageName = "com.foo"),
        ).forEach { condition ->
            val reading = liveReading(condition, SystemState())

            assertEquals(LiveVerdict.AWAITS, reading.verdict, condition.toString())
            assertEquals(NowKey.AWAITS_EVENT, reading.now)
        }
    }
}
