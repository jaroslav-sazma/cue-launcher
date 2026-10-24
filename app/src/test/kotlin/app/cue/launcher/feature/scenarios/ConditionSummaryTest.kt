package app.cue.launcher.feature.scenarios

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionType
import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.RingerModeState
import app.cue.launcher.data.scenario.WifiMode
import app.cue.launcher.data.scenario.withNegate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConditionSummaryTest {

    @Test
    fun `a time window carries a range and days`() {
        val phrase = conditionPhrase(Condition.TimeWindow(from = "18:00", to = "23:00"))

        assertEquals(PhraseKey.TIME_WINDOW, phrase.key)
        assertEquals("18–23", phrase.argument)
        assertEquals(DaysPhrase.EveryDay, phrase.days)
    }

    @Test
    fun `an instant condition carries its end event and nothing else does`() {
        val watch = conditionPhrase(
            Condition.NotificationFrom(
                packageName = "com.watch",
                until = EndEvent.Notification(packageName = "com.watch", contains = "offline"),
            ),
        )
        val evening = conditionPhrase(Condition.HaEvent(code = "door", until = EndEvent.AtTime("17:00", listOf(6, 7))))

        assertEquals(PhraseKey.NOTIFICATION, watch.key)
        assertEquals(EndPhrase(EndPhraseKey.NOTIFICATION, "com.watch"), watch.until)
        assertEquals(EndPhrase(EndPhraseKey.TIME, "17", DaysPhrase.Weekend), evening.until)
        assertEquals(EndPhrase(EndPhraseKey.BT_DEVICE, "AA:BB"), endPhrase(EndEvent.BtDisconnected("AA:BB")))
        assertEquals(EndPhrase(EndPhraseKey.BT_ANY), endPhrase(EndEvent.BtDisconnected()))
        assertNull(conditionPhrase(Condition.Alarm()).until)
    }

    @Test
    fun `time is shortened to hours and does not print a zero`() {
        assertEquals("18", shortTime("18:00"))
        assertEquals("6", shortTime("06:00"))
        assertEquals("6:30", shortTime("06:30"))
    }

    @Test
    fun `nonsensical time is printed as-is`() {
        assertEquals("25:00", shortTime("25:00"))
        assertEquals("ráno", shortTime("ráno"))
    }

    @Test
    fun `days are summarized into one word`() {
        assertEquals(DaysPhrase.EveryDay, daysPhraseOf(listOf(1, 2, 3, 4, 5, 6, 7)))
        assertEquals(DaysPhrase.Workdays, daysPhraseOf(listOf(1, 2, 3, 4, 5)))
        assertEquals(DaysPhrase.Weekend, daysPhraseOf(listOf(6, 7)))
        assertEquals(DaysPhrase.Selected(listOf(2, 4)), daysPhraseOf(listOf(4, 2)))
    }

    @Test
    fun `days out of range are discarded`() {
        assertEquals(DaysPhrase.Workdays, daysPhraseOf(listOf(0, 1, 2, 3, 4, 5, 9)))
    }

    @Test
    fun `wifi has its own phrase for every mode and negation combination`() {
        val any = Condition.Wifi(mode = WifiMode.ANY)
        val ssid = Condition.Wifi(mode = WifiMode.SSID, ssid = "Doma")

        assertEquals(PhraseKey.WIFI_ANY, conditionPhrase(any).key)
        assertEquals(PhraseKey.WIFI_ANY_NEGATED, conditionPhrase(any.copy(negate = true)).key)
        assertEquals(PhraseKey.WIFI_SSID, conditionPhrase(ssid).key)
        assertEquals("Doma", conditionPhrase(ssid).argument)
        assertEquals(PhraseKey.WIFI_SSID_NEGATED, conditionPhrase(ssid.copy(negate = true)).key)
    }

    @Test
    fun `bluetooth distinguishes a specific device`() {
        assertEquals(PhraseKey.BT_CONNECTED_ANY, conditionPhrase(Condition.BtConnected()).key)
        assertEquals(
            PhraseKey.BT_CONNECTED_DEVICE,
            conditionPhrase(Condition.BtConnected(mac = "AA:BB")).key,
        )
        assertEquals(PhraseKey.BT_DISCONNECTED_ANY, conditionPhrase(Condition.BtDisconnected()).key)
    }

    @Test
    fun `ringer mode has a phrase for every state`() {
        val keys = RingerModeState.entries.map { mode ->
            conditionPhrase(Condition.RingerMode(mode)).key
        }

        assertEquals(keys.toSet().size, keys.size)
    }

    @Test
    fun `a scenario without conditions has no phrase`() {
        assertTrue(conditionPhrases(scenario()).isEmpty())
    }

    @Test
    fun `multiple conditions give multiple phrases in the same order`() {
        val subject = scenario(
            conditions = listOf(
                Condition.TimeWindow(from = "18:00", to = "23:00"),
                Condition.Wifi(mode = WifiMode.SSID, ssid = "Doma"),
            ),
        )

        assertEquals(
            listOf(PhraseKey.TIME_WINDOW, PhraseKey.WIFI_SSID),
            conditionPhrases(subject).map(ConditionPhrase::key),
        )
    }

    @Test
    fun `every condition type has its own phrase`() {
        val phrases = ALL_CONDITIONS.map(::conditionPhrase)

        assertEquals(ConditionType.entries.size, phrases.size)
        assertTrue(phrases.all { it.key in PhraseKey.entries })
    }

    @Test
    fun `every state condition has a phrase of its own when negated`() {
        val states = ALL_CONDITIONS.filterIsInstance<Condition.StateCondition>()
        val keys = states.map { conditionPhrase(it.withNegate(true)).key }

        assertEquals(keys.size, keys.toSet().size)
        states.forEach { condition ->
            assertNotEquals(
                conditionPhrase(condition).key,
                conditionPhrase(condition.withNegate(true)).key,
                "condition $condition",
            )
        }
    }

    @Test
    fun `a negated ringer mode has a phrase for every state`() {
        val keys = RingerModeState.entries.map { mode ->
            conditionPhrase(Condition.RingerMode(mode, negate = true)).key
        }

        assertEquals(keys.toSet().size, keys.size)
    }

    @Test
    fun `a media condition names its apps only when it has some`() {
        val any = conditionPhrase(Condition.MediaPlaying())
        val chosen = conditionPhrase(Condition.MediaPlaying(negate = true, packages = listOf("com.spotify.music")))
        val several = conditionPhrase(Condition.MediaPlaying(packages = listOf("com.spotify.music", "org.radio")))

        assertEquals(PhraseKey.MEDIA_PLAYING, any.key)
        assertTrue(any.apps.isEmpty())
        assertEquals(PhraseKey.MEDIA_APPS_NEGATED, chosen.key)
        assertEquals(listOf("com.spotify.music"), chosen.apps)
        assertEquals(PhraseKey.MEDIA_APPS, several.key)
    }

    @Test
    fun `a Home Assistant state names the entity and its states`() {
        val tv = Condition.HaState(entity = "media_player.living_room_tv", states = listOf("playing", "paused"))

        val playing = conditionPhrase(tv)
        val stopped = conditionPhrase(tv.copy(states = listOf("playing"), negate = true))
        val neither = conditionPhrase(tv.copy(negate = true))

        assertEquals(PhraseKey.HA_STATE, playing.key)
        assertEquals("media_player.living_room_tv", playing.argument)
        assertEquals(listOf("playing", "paused"), playing.states)
        assertEquals(PhraseKey.HA_STATE_NEGATED, stopped.key)
        assertEquals(PhraseKey.HA_STATE_NONE, neither.key)
    }

    @Test
    fun `none of several apps has a phrase of its own`() {
        val none = conditionPhrase(
            Condition.MediaPlaying(negate = true, packages = listOf("com.spotify.music", "org.radio")),
        )

        assertEquals(PhraseKey.MEDIA_APPS_NONE, none.key)
        assertEquals(listOf("com.spotify.music", "org.radio"), none.apps)
    }

    private companion object {

        val ALL_CONDITIONS = listOf(
            Condition.TimeWindow(from = "18:00", to = "23:00"),
            Condition.BtConnected(),
            Condition.Wifi(mode = WifiMode.ANY),
            Condition.Charging(),
            Condition.AudioOutput(),
            Condition.MediaPlaying(),
            Condition.RingerMode(RingerModeState.SILENT),
            Condition.CalendarEvent(),
            Condition.HaState(entity = "binary_sensor.door", states = listOf("on")),
            Condition.TimeInstant(at = "17:00"),
            Condition.Alarm(),
            Condition.BtDisconnected(),
            Condition.NotificationFrom(packageName = "cz.alza"),
            Condition.HaEvent(code = "zvonek"),
            Condition.NotificationEnded(packageName = "com.google.android.apps.maps"),
            Condition.PlaceSaved(),
        )
    }
}
