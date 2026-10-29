package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionType
import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.EndEventType
import app.cue.launcher.data.scenario.ExtractKind
import app.cue.launcher.data.scenario.ExtractRule
import app.cue.launcher.data.scenario.WifiMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConditionFormTest {

    @Test
    fun `time is padded to canonical form`() {
        assertEquals("08:05", parseTimeOfDay("8:5"))
        assertEquals("18:00", parseTimeOfDay("18:00"))
        assertEquals("00:00", parseTimeOfDay(" 0:0 "))
    }

    @Test
    fun `nonsensical time does not parse`() {
        listOf("", "18", "18:60", "24:00", "ráno", "1:2:3", "-1:00").forEach { value ->
            assertNull(parseTimeOfDay(value), value)
        }
    }

    @Test
    fun `an incomplete time reports a reason`() {
        val condition = Condition.TimeWindow(from = "18", to = "23:00")

        assertEquals(ConditionFormError.TIME, conditionFormError(condition))
    }

    @Test
    fun `a condition without a day reports a reason`() {
        val condition = Condition.TimeWindow(from = "18:00", to = "23:00", days = emptyList())

        assertEquals(ConditionFormError.DAYS, conditionFormError(condition))
    }

    @Test
    fun `a specific network needs a name`() {
        assertNull(conditionFormError(Condition.Wifi(mode = WifiMode.ANY)))
        assertEquals(
            ConditionFormError.WIFI_SSID,
            conditionFormError(Condition.Wifi(mode = WifiMode.SSID, ssid = " ")),
        )
        assertNull(conditionFormError(Condition.Wifi(mode = WifiMode.SSID, ssid = "Doma")))
    }

    @Test
    fun `an HA event needs a pairing code`() {
        assertEquals(ConditionFormError.HA_CODE, conditionFormError(Condition.HaEvent(code = "")))
        assertNull(conditionFormError(Condition.HaEvent(code = "zvonek")))
    }

    @Test
    fun `a notification needs a package`() {
        assertEquals(
            ConditionFormError.NOTIFICATION_PACKAGE,
            conditionFormError(Condition.NotificationFrom(packageName = "")),
        )
    }

    @Test
    fun `an extract rule reports a reason`() {
        val condition = Condition.NotificationFrom(packageName = "cz.zasilkovna.app")
        fun error(vararg rules: ExtractRule) = conditionFormError(condition.copy(extract = rules.toList()))

        assertNull(conditionFormError(condition))
        assertNull(error(ExtractRule(name = "kod", after = "kód", kind = ExtractKind.NUMBER)))
        assertEquals(
            ConditionFormError.NOTIFICATION_EXTRACT_NAME,
            error(ExtractRule(name = "kód", kind = ExtractKind.NUMBER)),
        )
        assertEquals(
            ConditionFormError.NOTIFICATION_EXTRACT_RESERVED,
            error(ExtractRule(name = "title", kind = ExtractKind.WORD)),
        )
        assertEquals(
            ConditionFormError.NOTIFICATION_EXTRACT_DUPLICATE,
            error(ExtractRule(name = "a", kind = ExtractKind.WORD), ExtractRule(name = "a", kind = ExtractKind.LINE)),
        )
        assertEquals(
            ConditionFormError.NOTIFICATION_EXTRACT_UNTIL,
            error(ExtractRule(name = "note", kind = ExtractKind.UNTIL, until = " ")),
        )
        assertEquals(
            ConditionFormError.NOTIFICATION_EXTRACT_LENGTH,
            error(ExtractRule(name = "a", after = "x".repeat(65), kind = ExtractKind.WORD)),
        )
        assertEquals(
            ConditionFormError.NOTIFICATION_EXTRACT_COUNT,
            error(*Array(5) { ExtractRule(name = "v$it", kind = ExtractKind.WORD) }),
        )
    }

    @Test
    fun `a missing package overrides the rule defect`() {
        val condition = Condition.NotificationFrom(
            packageName = "",
            extract = listOf(ExtractRule(name = "Kod", kind = ExtractKind.NUMBER)),
        )

        assertEquals(ConditionFormError.NOTIFICATION_PACKAGE, conditionFormError(condition))
    }

    @Test
    fun `conditions without parameters are always fine`() {
        listOf(
            Condition.Charging(),
            Condition.AudioOutput(),
            Condition.MediaPlaying(),
            Condition.CalendarEvent(),
            Condition.BtConnected(),
            Condition.MacNearby(),
            Condition.MacLocked(),
        ).forEach { assertNull(conditionFormError(it), it.toString()) }
    }

    @Test
    fun `a Home Assistant state needs an entity id and a state`() {
        val tv = Condition.HaState(entity = "media_player.tv", states = listOf("playing"))

        assertNull(conditionFormError(tv))
        assertEquals(ConditionFormError.HA_ENTITY, conditionFormError(tv.copy(entity = "")))
        assertEquals(ConditionFormError.HA_ENTITY, conditionFormError(tv.copy(entity = "the tv")))
        assertEquals(ConditionFormError.HA_ENTITY, conditionFormError(tv.copy(entity = "{{PLACEHOLDER:tv}}")))
        assertEquals(ConditionFormError.HA_STATES, conditionFormError(tv.copy(states = listOf(" "))))
    }

    @Test
    fun `confirmation tidies a Home Assistant state`() {
        val tidy = canonicalCondition(
            Condition.HaState(entity = " Media_Player.TV ", states = listOf("Playing", " playing ", "", "paused")),
        )

        assertEquals(Condition.HaState(entity = "media_player.tv", states = listOf("playing", "paused")), tidy)
    }

    @Test
    fun `the state choices start from the domain, then the current state and the hand-typed ones`() {
        assertEquals(
            listOf("playing", "paused", "idle", "on", "off", "standby", "buffering", "casting"),
            haStateChoices("media_player.tv", current = "Casting", chosen = listOf("playing")),
        )
        assertEquals(listOf("on", "off"), haStateChoices("binary_sensor.door", current = "off", chosen = emptyList()))
        assertEquals(
            listOf("21.5", "warm"),
            haStateChoices("sensor.outside", current = "21.5", chosen = listOf("warm")),
        )
        assertEquals(emptyList<String>(), haStateChoices("", current = null, chosen = emptyList()))
    }

    @Test
    fun `a state is ticked and unticked whatever its case`() {
        assertEquals(listOf("playing", "paused"), toggledHaStates(listOf("playing"), " Paused ", on = true))
        assertEquals(listOf("playing"), toggledHaStates(listOf("playing"), "PLAYING", on = true))
        assertEquals(listOf("paused"), toggledHaStates(listOf("playing", "paused"), "Playing", on = false))
        assertEquals(listOf("playing"), toggledHaStates(listOf("playing"), " ", on = true))
    }

    @Test
    fun `confirmation aligns an incomplete input`() {
        val window = canonicalCondition(
            Condition.TimeWindow(from = "8:5", to = "23:0", days = listOf(3, 1, 1, 9)),
        ) as Condition.TimeWindow

        assertEquals("08:05", window.from)
        assertEquals("23:00", window.to)
        assertEquals(listOf(1, 3), window.days)
    }

    @Test
    fun `switching to any wifi discards the network name`() {
        val wifi = canonicalCondition(
            Condition.Wifi(mode = WifiMode.ANY, ssid = "Doma"),
        ) as Condition.Wifi

        assertNull(wifi.ssid)
    }

    @Test
    fun `confirmation tidies the extract rules`() {
        val notification = canonicalCondition(
            Condition.NotificationFrom(
                packageName = "com.foo",
                extract = listOf(
                    ExtractRule(name = " kod ", after = "  ", kind = ExtractKind.NUMBER, until = "."),
                    ExtractRule(name = "note", after = " pozn ", kind = ExtractKind.UNTIL, until = " . "),
                ),
            ),
        ) as Condition.NotificationFrom

        assertEquals(
            listOf(
                ExtractRule(name = "kod", kind = ExtractKind.NUMBER),
                ExtractRule(name = "note", after = "pozn", kind = ExtractKind.UNTIL, until = "."),
            ),
            notification.extract,
        )
    }

    @Test
    fun `confirmation trims both the code and the package`() {
        val event = canonicalCondition(Condition.HaEvent(code = " zvonek ")) as Condition.HaEvent
        val notification = canonicalCondition(
            Condition.NotificationFrom(packageName = " com.foo ", contains = "  "),
        ) as Condition.NotificationFrom

        assertEquals("zvonek", event.code)
        assertEquals("com.foo", notification.packageName)
        assertNull(notification.contains)
    }

    @Test
    fun `the duration chip matches the stored value`() {
        assertEquals(DurationChoice.SEC_30, durationChoiceOf(30))
        assertEquals(DurationChoice.SEC_60, durationChoiceOf(60))
        assertEquals(DurationChoice.MIN_5, durationChoiceOf(300))
        assertEquals(DurationChoice.CUSTOM, durationChoiceOf(90))
    }

    @Test
    fun `a missing duration opens on the engine default`() {
        assertEquals(DurationChoice.SEC_60, durationChoiceOf(null))
        assertEquals("1", customMinutesOf(null, '.'))
    }

    @Test
    fun `a custom duration is a positive number of minutes`() {
        assertEquals(900, durationSecondsOf(DurationChoice.CUSTOM, " 15 "))
        assertEquals(90, durationSecondsOf(DurationChoice.CUSTOM, "1.5"))
        assertEquals(90, durationSecondsOf(DurationChoice.CUSTOM, "1,5"))
        assertEquals(30, durationSecondsOf(DurationChoice.CUSTOM, ".5"))
        assertEquals(300, durationSecondsOf(DurationChoice.CUSTOM, "5."))
        assertNull(durationSecondsOf(DurationChoice.CUSTOM, "0"))
        assertNull(durationSecondsOf(DurationChoice.CUSTOM, "0.00"))
        assertNull(durationSecondsOf(DurationChoice.CUSTOM, "-5"))
        assertNull(durationSecondsOf(DurationChoice.CUSTOM, "chvilku"))
        assertEquals(
            ConditionFormError.DURATION,
            durationError(DurationChoice.CUSTOM, ""),
        )
        assertNull(durationError(DurationChoice.SEC_60, ""))
    }

    @Test
    fun `a custom duration rejects input that is not a plain number`() {
        listOf("+5", "1e3", "1e100000000", "0.001", "1,5,0", "٣", "NaN", "1_000").forEach { text ->
            assertNull(durationSecondsOf(DurationChoice.CUSTOM, text)) { text }
        }
    }

    @Test
    fun `a custom duration that does not fit an Int is rejected instead of wrapping`() {
        assertNull(durationSecondsOf(DurationChoice.CUSTOM, "71582789"))
        assertNull(durationSecondsOf(DurationChoice.CUSTOM, "99999999.99"))
        assertEquals(Int.MAX_VALUE, durationSecondsOf(DurationChoice.CUSTOM, "35791394.12"))
    }

    @Test
    fun `a stored duration round-trips through the minutes field`() {
        assertEquals("15", customMinutesOf(900, '.'))
        assertEquals("1.5", customMinutesOf(90, '.'))
        assertEquals("1,5", customMinutesOf(90, ','))
        listOf(1, 45, 90, 100, 900, 3601, Int.MAX_VALUE).forEach { seconds ->
            listOf('.', ',').forEach { separator ->
                val minutes = customMinutesOf(seconds, separator)
                assertEquals(seconds, durationSecondsOf(DurationChoice.CUSTOM, minutes)) { "$seconds s as $minutes" }
            }
        }
    }

    @Test
    fun `duration is written only to instant conditions`() {
        val instant = withEnding(Condition.HaEvent(code = "zvonek"), 300, until = null) as Condition.HaEvent
        assertEquals(300, instant.durationSec)

        assertEquals(Condition.Charging(), withEnding(Condition.Charging(), 300, until = null))
    }

    @Test
    fun `a condition with an end event opens on the event choice, its duration as the cap`() {
        val end = EndEvent.Notification(packageName = "com.watch", contains = "offline")
        val condition = Condition.NotificationFrom(packageName = "com.watch", durationSec = 3_600, until = end)

        val draft = endingDraftOf(condition, '.')

        assertEquals(DurationChoice.EVENT, durationChoiceOf(condition))
        assertEquals(DurationChoice.EVENT, draft.choice)
        assertEquals(end, draft.end)
        assertEquals(3_600, draft.capSec)
        assertEquals(condition, withEnding(condition, draft))
    }

    @Test
    fun `the first end offered is a notification still to be picked, with no cap`() {
        val draft = endingDraftOf(Condition.Alarm(durationSec = 300), '.')

        assertEquals(DurationChoice.MIN_5, draft.choice)
        assertEquals(EndEvent.Notification(packageName = ""), draft.end)
        assertNull(draft.capSec)
        assertEquals(ConditionFormError.END_PACKAGE, endingError(draft.copy(choice = DurationChoice.EVENT)))
        assertNull(endingError(draft))
    }

    @Test
    fun `the event choice drops the default duration and a countdown drops the end`() {
        val condition = Condition.HaEvent(code = "watch", durationSec = 60)
        val draft = endingDraftOf(condition, '.').copy(choice = DurationChoice.EVENT, end = EndEvent.Alarm)

        val waiting = withEnding(condition, draft) as Condition.HaEvent
        val counted = withEnding(waiting, draft.copy(choice = DurationChoice.SEC_30)) as Condition.HaEvent

        assertEquals(EndEvent.Alarm, waiting.until)
        assertNull(waiting.durationSec)
        assertNull(counted.until)
        assertEquals(30, counted.durationSec)
    }

    @Test
    fun `an end event is checked as its condition counterpart would be`() {
        assertEquals(ConditionFormError.END_TIME, endEventError(EndEvent.AtTime(at = "17")))
        assertEquals(ConditionFormError.END_DAYS, endEventError(EndEvent.AtTime(at = "17:00", days = emptyList())))
        assertEquals(ConditionFormError.END_HA_CODE, endEventError(EndEvent.HaEvent(code = " ")))
        assertEquals(ConditionFormError.END_PACKAGE, endEventError(EndEvent.Notification(packageName = "")))
        assertNull(endEventError(EndEvent.Alarm))
        assertNull(endEventError(EndEvent.BtDisconnected()))
    }

    @Test
    fun `confirmation tidies the end event too`() {
        val condition = Condition.Alarm(until = EndEvent.AtTime(at = "7:5", days = listOf(3, 1, 3)))
        val notification = Condition.HaEvent(
            code = "door",
            until = EndEvent.Notification(packageName = " com.watch ", contains = "  "),
        )

        assertEquals(
            EndEvent.AtTime(at = "07:05", days = listOf(1, 3)),
            (canonicalCondition(condition) as Condition.Alarm).until,
        )
        assertEquals(
            EndEvent.Notification(packageName = "com.watch"),
            (canonicalCondition(notification) as Condition.HaEvent).until,
        )
    }

    @Test
    fun `an end that is the trigger itself is told apart from one that differs`() {
        val watch = Condition.NotificationFrom(packageName = "com.watch", contains = "connected")

        assertTrue(endRepeatsTrigger(watch, EndEvent.Notification(packageName = "COM.watch", contains = "Connected")))
        assertFalse(endRepeatsTrigger(watch, EndEvent.Notification(packageName = "com.watch", contains = "offline")))
        assertTrue(endRepeatsTrigger(Condition.HaEvent(code = "Door"), EndEvent.HaEvent(code = " door")))
        assertTrue(endRepeatsTrigger(Condition.Alarm(), EndEvent.Alarm))
        assertFalse(endRepeatsTrigger(Condition.Alarm(), EndEvent.AtTime(at = "07:00")))
    }

    @Test
    fun `a trigger that covers every end occurrence is told apart from one that does not`() {
        val anyWatch = Condition.NotificationFrom(packageName = "com.watch", contains = " ")
        val connected = Condition.NotificationFrom(packageName = "com.watch", contains = "connected")
        fun watchEnd(contains: String?) = EndEvent.Notification(packageName = " com.watch", contains = contains)

        assertTrue(endRepeatsTrigger(anyWatch, watchEnd("offline")))
        assertTrue(endRepeatsTrigger(connected, watchEnd("Disconnected")))
        assertFalse(endRepeatsTrigger(connected, watchEnd("offline")))
        assertFalse(endRepeatsTrigger(connected, watchEnd(null)))
        assertFalse(endRepeatsTrigger(anyWatch, EndEvent.Notification(packageName = "com.phone")))
    }

    @Test
    fun `any device as the trigger covers a one device end but not the other way round`() {
        val headphones = "AA:BB:CC:DD:EE:FF"
        val fromHeadphones = Condition.BtDisconnected(mac = headphones)

        assertTrue(endRepeatsTrigger(Condition.BtDisconnected(), EndEvent.BtDisconnected(mac = headphones)))
        assertTrue(endRepeatsTrigger(Condition.BtDisconnected(), EndEvent.BtDisconnected()))
        assertTrue(endRepeatsTrigger(fromHeadphones, EndEvent.BtDisconnected(headphones.lowercase())))
        assertFalse(endRepeatsTrigger(fromHeadphones, EndEvent.BtDisconnected()))
        assertFalse(endRepeatsTrigger(fromHeadphones, EndEvent.BtDisconnected("11:22:33:44:55:66")))
    }

    @Test
    fun `a set time end is covered by the same time on a superset of its days`() {
        val everyDay = Condition.TimeInstant(at = "7:00")
        val weekdays = Condition.TimeInstant(at = "07:00", days = listOf(1, 2, 3, 4, 5))

        assertTrue(endRepeatsTrigger(everyDay, EndEvent.AtTime(at = "07:00", days = listOf(6, 7))))
        assertTrue(endRepeatsTrigger(weekdays, EndEvent.AtTime(at = "07:00", days = listOf(1, 5))))
        assertFalse(endRepeatsTrigger(weekdays, EndEvent.AtTime(at = "07:00", days = listOf(6, 7))))
        assertFalse(endRepeatsTrigger(weekdays, EndEvent.AtTime(at = "07:00")))
        assertFalse(endRepeatsTrigger(everyDay, EndEvent.AtTime(at = "09:00")))
    }

    @Test
    fun `a new set time end does not start out repeating a new set time trigger`() {
        val trigger = newCondition(ConditionType.TIME_INSTANT)

        assertFalse(endRepeatsTrigger(trigger, newEndEvent(EndEventType.TIME_INSTANT)))
    }

    @Test
    fun `an HA end waiting for no code repeats nothing`() {
        assertFalse(endRepeatsTrigger(Condition.HaEvent(code = ""), EndEvent.HaEvent(code = " ")))
        assertFalse(endRepeatsTrigger(Condition.HaEvent(code = "door"), EndEvent.HaEvent(code = "doorbell")))
    }

    @Test
    fun `a picked app fills the package it was picked for`() {
        val condition = Condition.NotificationFrom(packageName = "com.watch", until = EndEvent.Notification(""))

        val forEnd = withWatchedPackage(condition, "com.other", AppTarget.END_EVENT) as Condition.NotificationFrom
        val forCondition = withWatchedPackage(condition, "com.other", AppTarget.CONDITION) as Condition.NotificationFrom

        assertEquals("com.watch", forEnd.packageName)
        assertEquals(EndEvent.Notification(packageName = "com.other"), forEnd.until)
        assertEquals("com.other", forCondition.packageName)
        assertEquals(EndEvent.Notification(""), forCondition.until)
    }
}
