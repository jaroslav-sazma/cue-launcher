package app.cue.launcher.system

import android.app.NotificationManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import app.cue.launcher.data.scenario.RingerModeState
import app.cue.launcher.engine.MediaPlayback
import app.cue.launcher.engine.WifiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SystemStateReadingsTest {

    @Test
    fun `charging is recognized from every source`() {
        assertTrue(chargingFrom(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_AC))
        assertTrue(chargingFrom(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_USB))
        assertTrue(
            chargingFrom(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_WIRELESS),
        )
    }

    @Test
    fun `a full battery plugged in still counts as charging`() {
        assertTrue(chargingFrom(BatteryManager.BATTERY_STATUS_FULL, BatteryManager.BATTERY_PLUGGED_AC))
    }

    @Test
    fun `charging status is enough even without a reported source`() {
        assertTrue(chargingFrom(BatteryManager.BATTERY_STATUS_CHARGING, plugged = 0))
    }

    @Test
    fun `an unplugged phone is not charging`() {
        assertFalse(chargingFrom(BatteryManager.BATTERY_STATUS_DISCHARGING, plugged = 0))
        assertFalse(chargingFrom(BatteryManager.BATTERY_STATUS_NOT_CHARGING, plugged = 0))
        assertFalse(chargingFrom(BatteryManager.BATTERY_STATUS_FULL, plugged = 0))
    }

    @Test
    fun `headphones and speakers are an external output`() {
        assertTrue(isExternalAudioOutput(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertTrue(isExternalAudioOutput(AudioDeviceInfo.TYPE_WIRED_HEADSET))
        assertTrue(isExternalAudioOutput(AudioDeviceInfo.TYPE_USB_HEADSET))
        assertTrue(isExternalAudioOutput(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertTrue(isExternalAudioOutput(AudioDeviceInfo.TYPE_BLUETOOTH_SCO))
        assertTrue(isExternalAudioOutput(AudioDeviceInfo.TYPE_BLE_HEADSET))
        assertTrue(isExternalAudioOutput(AudioDeviceInfo.TYPE_HEARING_AID))
    }

    @Test
    fun `built-in outputs are not external`() {
        assertFalse(isExternalAudioOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertFalse(isExternalAudioOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE))
        assertFalse(isExternalAudioOutput(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))
        assertFalse(isExternalAudioOutput(AudioDeviceInfo.TYPE_TELEPHONY))
    }

    @Test
    fun `an unknown output type does not count`() {
        assertFalse(isExternalAudioOutput(AudioDeviceInfo.TYPE_UNKNOWN))
        assertFalse(isExternalAudioOutput(Int.MAX_VALUE))
    }

    @Test
    fun `a network name gets its quotes stripped`() {
        assertEquals("Doma-5G", normalizedSsid("\"Doma-5G\""))
        assertEquals("Doma-5G", normalizedSsid("Doma-5G"))
    }

    @Test
    fun `a redacted network name is unknown`() {
        assertNull(normalizedSsid("<unknown ssid>"))
        assertNull(normalizedSsid("\"<unknown ssid>\""))
        assertNull(normalizedSsid(""))
        assertNull(normalizedSsid(null))
    }

    @Test
    fun `a redacted access point mac is unknown`() {
        assertEquals("a4:2b:b0:11:22:33", normalizedBssid("a4:2b:b0:11:22:33"))
        assertNull(normalizedBssid("02:00:00:00:00:00"))
        assertNull(normalizedBssid(""))
        assertNull(normalizedBssid(null))
    }

    @Test
    fun `with no network wifi is not connected`() {
        assertEquals(WifiState(connected = false, ssid = null), wifiStateOf(emptyList()))
    }

    @Test
    fun `the name is taken from the first network that reported it`() {
        val blind = WifiState(connected = true, ssid = null, bssid = null)
        val named = WifiState(connected = true, ssid = "Doma-5G", bssid = "a4:2b:b0:11:22:33")

        assertEquals(named, wifiStateOf(listOf(blind, named)))
        assertEquals(blind, wifiStateOf(listOf(blind)))
    }

    @Test
    fun `ringer mode translates to a condition state`() {
        assertEquals(RingerModeState.SILENT, ringerModeFrom(AudioManager.RINGER_MODE_SILENT))
        assertEquals(RingerModeState.VIBRATE, ringerModeFrom(AudioManager.RINGER_MODE_VIBRATE))
        assertEquals(RingerModeState.NORMAL, ringerModeFrom(AudioManager.RINGER_MODE_NORMAL))
    }

    @Test
    fun `an unknown ringer mode is unknown`() {
        assertNull(ringerModeFrom(Int.MAX_VALUE))
        assertNull(ringerModeFrom(-1))
    }

    @Test
    fun `do not disturb recognizes every enabled filter`() {
        assertEquals(false, dndFrom(NotificationManager.INTERRUPTION_FILTER_ALL))
        assertEquals(true, dndFrom(NotificationManager.INTERRUPTION_FILTER_PRIORITY))
        assertEquals(true, dndFrom(NotificationManager.INTERRUPTION_FILTER_NONE))
        assertEquals(true, dndFrom(NotificationManager.INTERRUPTION_FILTER_ALARMS))
    }

    @Test
    fun `an unknown filter is unknown`() {
        assertNull(dndFrom(NotificationManager.INTERRUPTION_FILTER_UNKNOWN))
    }

    @Test
    fun `if any player is playing, playback is playing`() {
        val states = listOf(PlaybackState.STATE_PAUSED, PlaybackState.STATE_PLAYING)

        assertEquals(MediaPlayback.PLAYING, mediaPlaybackOf(states))
        assertEquals(MediaPlayback.PLAYING, mediaPlaybackOf(listOf(PlaybackState.STATE_PLAYING)))
    }

    @Test
    fun `buffering and seeking count as playing`() {
        assertEquals(MediaPlayback.PLAYING, mediaPlaybackOf(listOf(PlaybackState.STATE_BUFFERING)))
        assertEquals(MediaPlayback.PLAYING, mediaPlaybackOf(listOf(PlaybackState.STATE_FAST_FORWARDING)))
        assertEquals(MediaPlayback.PLAYING, mediaPlaybackOf(listOf(PlaybackState.STATE_REWINDING)))
    }

    @Test
    fun `a paused player is paused`() {
        assertEquals(MediaPlayback.PAUSED, mediaPlaybackOf(listOf(PlaybackState.STATE_PAUSED, null)))
    }

    @Test
    fun `a stopped player and no session at all are idle`() {
        assertEquals(MediaPlayback.IDLE, mediaPlaybackOf(emptyList()))
        assertEquals(MediaPlayback.IDLE, mediaPlaybackOf(listOf(PlaybackState.STATE_STOPPED)))
        assertEquals(MediaPlayback.IDLE, mediaPlaybackOf(listOf(PlaybackState.STATE_ERROR, PlaybackState.STATE_NONE)))
        assertEquals(MediaPlayback.IDLE, mediaPlaybackOf(listOf(null)))
    }

    @Test
    fun `the age of a pause is counted from the player report`() {
        assertEquals(5_000L, pausedForMillis(listOf(95_000L), realtimeMillis = 100_000L))
    }

    @Test
    fun `among multiple pauses the freshest one wins`() {
        assertEquals(5_000L, pausedForMillis(listOf(20_000L, 95_000L), realtimeMillis = 100_000L))
    }

    @Test
    fun `an undated pause has no age`() {
        assertNull(pausedForMillis(listOf(0L), realtimeMillis = 100_000L))
        assertNull(pausedForMillis(emptyList(), realtimeMillis = 100_000L))
    }

    @Test
    fun `a timestamp from the future is a fresh pause`() {
        assertEquals(0L, pausedForMillis(listOf(120_000L), realtimeMillis = 100_000L))
    }
}
