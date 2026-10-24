package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionMatch
import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.ExtractKind
import app.cue.launcher.data.scenario.ExtractRule
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.NotificationReply
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.SemanticStyle
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileProgress
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.data.scenario.TriggerPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationRulesTest {

    @Test
    fun `a notification condition needs permission`() {
        assertTrue(usesNotificationListener(listOf(watching())))
    }

    @Test
    fun `a something-is-playing condition needs the same permission`() {
        assertTrue(usesNotificationListener(listOf(scenario("playing", conditions = listOf(Condition.MediaPlaying())))))
    }

    @Test
    fun `a scenario showing the media widget needs the same permission`() {
        val headphones = scenario(
            "headphones",
            size = TileSize.HERO_4X2,
            conditions = listOf(Condition.Charging()),
            content = TileContent.LauncherWidget(LauncherWidgetType.MEDIA),
        )

        assertTrue(usesNotificationListener(listOf(headphones)))
    }

    @Test
    fun `a scenario without a notification condition needs nothing`() {
        val scenarios = listOf(
            scenario("charging", conditions = listOf(Condition.Charging())),
            scenario("meeting", conditions = listOf(Condition.CalendarEvent())),
        )

        assertFalse(usesNotificationListener(scenarios))
    }

    @Test
    fun `a notification that only ends the scenario needs the same permission`() {
        val ending = Condition.HaEvent(code = "washer", until = EndEvent.Notification(packageName = "com.example"))

        assertTrue(usesNotificationListener(listOf(scenario("washer", conditions = listOf(ending)))))
        val alarm = Condition.HaEvent(code = "washer", until = EndEvent.Alarm)
        assertFalse(usesNotificationListener(listOf(scenario("alarm", conditions = listOf(alarm)))))
    }

    @Test
    fun `a disabled scenario counts too`() {
        assertTrue(usesNotificationListener(listOf(watching().copy(enabled = false))))
    }

    @Test
    fun `an empty scenario list needs nothing`() {
        assertFalse(usesNotificationListener(emptyList()))
    }
}

class NotificationTriggerRulesTest {

    @Test
    fun `a notification from the watched app matches`() {
        assertTrue(notificationMatches(CONDITION, posted()))
    }

    @Test
    fun `a notification from another app does not match`() {
        assertFalse(notificationMatches(CONDITION, posted(packageName = "cz.alza")))
    }

    @Test
    fun `the package name is compared without spaces and case`() {
        val condition = CONDITION.copy(packageName = "  CZ.Zasilkovna.App ")

        assertTrue(notificationMatches(condition, posted()))
    }

    @Test
    fun `a condition without text accepts any notification from the app`() {
        assertTrue(notificationMatches(CONDITION, posted(title = "Anything", text = null)))
    }

    @Test
    fun `empty text behaves as if none was given`() {
        assertTrue(notificationMatches(CONDITION.copy(contains = "   "), posted(title = "Anything")))
    }

    @Test
    fun `the text is searched in both the title and the body`() {
        val condition = CONDITION.copy(contains = "výdejní kód")

        assertTrue(notificationMatches(condition, posted(title = "Výdejní kód 1234", text = null)))
        assertTrue(notificationMatches(condition, posted(title = "Zásilkovna", text = "Tvůj výdejní kód: 1234")))
        assertFalse(notificationMatches(condition, posted(title = "Zásilkovna", text = "Zásilka je na cestě")))
    }

    @Test
    fun `the text search is case-insensitive`() {
        assertTrue(notificationMatches(CONDITION.copy(contains = "KÓD"), posted(text = "Tvůj kód je 1234")))
    }

    @Test
    fun `the text is not searched across the title-body boundary`() {
        val condition = CONDITION.copy(contains = "Zásilkovna Tvůj")

        assertFalse(notificationMatches(condition, posted(title = "Zásilkovna", text = "Tvůj kód")))
    }

    @Test
    fun `a group summary header does not count as a notification`() {
        val summary = posted(key = "summary", title = null, text = null, isGroupSummary = true)

        assertFalse(notificationMatches(CONDITION, summary))
    }

    @Test
    fun `a group summary header does not keep the activation alive`() {
        val scenarios = listOf(watching())
        val active = listOf(posted(key = "summary", title = null, text = null, isGroupSummary = true))

        assertEquals(1, notificationEndListeners(scenarios, posted(), active).size)
    }

    @Test
    fun `the listener is only an enabled scenario with a notification condition`() {
        val scenarios = listOf(
            watching(),
            watching("disabled").copy(enabled = false),
            watching("other", packageName = "cz.alza"),
            scenario("charging", conditions = listOf(Condition.Charging())),
        )

        assertEquals(listOf("shipment"), notificationListeners(scenarios, posted()).map(Scenario::id))
    }

    @Test
    fun `a state gate blocks the notification`() {
        val scenarios = listOf(
            watching().copy(conditions = listOf(CONDITION, Condition.Charging())),
        )

        assertTrue(dueNotifications(scenarios, SystemState(charging = false), posted()).isEmpty())
        assertEquals(1, dueNotifications(scenarios, SystemState(charging = true), posted()).size)
    }

    @Test
    fun `an unmeasured state does not let the notification through`() {
        val scenarios = listOf(watching().copy(conditions = listOf(CONDITION, Condition.Charging())))

        assertTrue(dueNotifications(scenarios, SystemState(), posted()).isEmpty())
    }

    @Test
    fun `dismissing the last notification ends the scenario`() {
        val scenarios = listOf(watching())

        val ended = notificationEndListeners(scenarios, posted(), active = emptyList())

        assertEquals(listOf("shipment"), ended.map(Scenario::id))
    }

    @Test
    fun `a remaining notification from the same scenario keeps it running`() {
        val scenarios = listOf(watching())
        val active = listOf(posted(key = "second", title = "Second shipment"))

        assertTrue(notificationEndListeners(scenarios, posted(), active).isEmpty())
    }

    @Test
    fun `a dismissed notification does not count toward the remainder`() {
        val scenarios = listOf(watching())

        val ended = notificationEndListeners(scenarios, posted(), active = listOf(posted()))

        assertEquals(listOf("shipment"), ended.map(Scenario::id))
    }

    @Test
    fun `a remaining notification with no text match does not keep the scenario`() {
        val scenarios = listOf(watching().copy(conditions = listOf(CONDITION.copy(contains = "kód"))))
        val active = listOf(posted(key = "second", title = "Zásilkovna", text = "Zásilka je na cestě"))

        assertEquals(1, notificationEndListeners(scenarios, posted(text = "Tvůj kód: 1234"), active).size)
    }

    @Test
    fun `a disabled scenario can be ended`() {
        val scenarios = listOf(watching().copy(enabled = false))

        assertEquals(1, notificationEndListeners(scenarios, posted(), active = emptyList()).size)
    }

    @Test
    fun `dismissing a notification from another app ends nothing`() {
        val scenarios = listOf(watching())

        assertTrue(notificationEndListeners(scenarios, posted(packageName = "cz.alza"), emptyList()).isEmpty())
    }

    @Test
    fun `dismissing the notification of a condition with an end ends nothing`() {
        val ends = listOf(
            EndEvent.Notification(packageName = "cz.zasilkovna.app", contains = "vydána"),
            EndEvent.AtTime(at = "17:00"),
        )

        ends.forEach { until ->
            val scenarios = listOf(scenario("shipment", conditions = listOf(CONDITION.copy(until = until))))

            assertTrue(notificationEndCandidates(scenarios, posted()).isEmpty(), "$until")
            assertTrue(notificationEndListeners(scenarios, posted(), active = emptyList()).isEmpty(), "$until")
        }
    }

    @Test
    fun `under ANY only a plain condition makes a dismissal an end`() {
        val impulse = Condition.NotificationFrom(packageName = "cz.alza", until = EndEvent.Alarm)
        val any = scenario("shipment", conditions = listOf(impulse, CONDITION)).copy(match = ConditionMatch.ANY)
        val scenarios = listOf(any)
        val alza = posted(key = "alza", packageName = "cz.alza", title = "Alza")

        assertTrue(notificationEndListeners(scenarios, alza, active = emptyList()).isEmpty())
        assertEquals(1, notificationEndListeners(scenarios, posted(), active = listOf(alza)).size)
        val second = posted(key = "second", title = "Second shipment")
        assertTrue(notificationEndListeners(scenarios, posted(), active = listOf(second)).isEmpty())
    }

    @Test
    fun `a dismissal ends only a tile that kept the key of its notification`() {
        val ending = listOf(watching(), watching("held"), watching("impulse"))
        val running = listOf(
            Activation("shipment", since = 0L, payload = TriggerPayload(notificationKey = "first")),
            Activation("held", since = 0L),
            Activation("impulse", since = 0L, payload = TriggerPayload(until = EndEvent.Alarm, firedAt = 0L)),
            Activation("other", since = 0L, payload = TriggerPayload(notificationKey = "other")),
        )

        assertEquals(listOf("shipment"), heldByNotification(running, ending))
    }

    @Test
    fun `the payload carries title and text with a namespace`() {
        val payload = notificationPayload(posted(title = "Zásilkovna", text = "Kód 1234"))

        assertEquals(mapOf("notif:title" to "Zásilkovna", "notif:text" to "Kód 1234"), payload.fields)
    }

    @Test
    fun `the payload does not invent missing fields`() {
        val payload = notificationPayload(posted(title = "Zásilkovna", text = null))

        assertEquals(mapOf("notif:title" to "Zásilkovna"), payload.fields)
    }

    @Test
    fun `the payload does not carry an icon`() {
        assertEquals(null, notificationPayload(posted()).icon)
    }

    @Test
    fun `only a dismissible payload carries the notification key`() {
        val kept = notificationPayload(posted(), listOf(KOD))
        val impulse = notificationPayload(posted(), listOf(KOD), dismissible = false)

        assertEquals("first", kept.notificationKey)
        assertEquals(null, impulse.notificationKey)
        assertEquals(kept.fields, impulse.fields)
    }
}

class NotificationTilePayloadTest {

    @Test
    fun `the payload carries progress and semantics`() {
        val progress = TileProgress(value = 3, max = 4)

        val payload = notificationPayload(posted(progress = progress, semantic = SemanticStyle.CAUTION))

        assertEquals(progress, payload.progress)
        assertEquals(SemanticStyle.CAUTION, payload.semantic)
    }

    @Test
    fun `a plain notification adds nothing extra`() {
        val payload = notificationPayload(posted())

        assertEquals(null, payload.progress)
        assertEquals(SemanticStyle.UNSPECIFIED, payload.semantic)
    }

    @Test
    fun `progress without a scale does not make it into the payload`() {
        val payload = notificationPayload(posted(progress = TileProgress(value = 7, max = 0)))

        assertEquals(null, payload.progress)
    }

    @Test
    fun `a payload with semantics is not empty`() {
        assertFalse(notificationPayload(posted(semantic = SemanticStyle.DANGER)).isEmpty)
        assertFalse(notificationPayload(posted(progress = TileProgress(value = 1, max = 2))).isEmpty)
    }

    @Test
    fun `progress is not substituted into the text`() {
        val payload = notificationPayload(
            posted(title = null, text = null, progress = TileProgress(value = 1, max = 2)),
        )

        assertTrue(payload.fields.isEmpty())
    }

    @Test
    fun `the payload carries the reply option`() {
        val reply = NotificationReply(key = "first", hint = "Reply to Honza")

        val payload = notificationPayload(posted(reply = reply))

        assertEquals(reply, payload.reply)
    }

    @Test
    fun `a payload with a reply is not empty`() {
        val payload = notificationPayload(posted(title = null, text = null, reply = NotificationReply("first")))

        assertFalse(payload.isEmpty)
        assertTrue(payload.fields.isEmpty())
    }

    @Test
    fun `a plain notification does not offer a reply`() {
        assertEquals(null, notificationPayload(posted()).reply)
    }
}

class NotificationExtractPayloadTest {

    @Test
    fun `the payload carries extracted values alongside title and text`() {
        val payload = notificationPayload(posted(), listOf(KOD))

        assertEquals(
            mapOf(
                "notif:title" to "Zásilkovna",
                "notif:text" to "Tvůj výdejní kód: 1234",
                "notif:kod" to "1234",
            ),
            payload.fields,
        )
    }

    @Test
    fun `the rule also searches the title`() {
        val bolt = posted(title = "Bolt · 3 min", text = "Řidič je na cestě")
        val minutes = ExtractRule(name = "min", kind = ExtractKind.NUMBER)

        assertEquals("3", notificationPayload(bolt, listOf(minutes))["notif:min"])
    }

    @Test
    fun `a scenario without a rule has the same payload as before`() {
        val payload = notificationPayload(posted(), notificationTrigger(watching(), posted())?.extract.orEmpty())

        assertEquals(mapOf("notif:title" to "Zásilkovna", "notif:text" to "Tvůj výdejní kód: 1234"), payload.fields)
    }

    @Test
    fun `the rule is a property of the scenario`() {
        val whole = ExtractRule(name = "vse", kind = ExtractKind.UNTIL, until = "1234")
        val kod = scenario("kod", conditions = listOf(CONDITION.copy(extract = listOf(KOD))))
        val cely = scenario("whole", conditions = listOf(CONDITION.copy(extract = listOf(whole))))

        val kodRules = notificationTrigger(kod, posted())?.extract.orEmpty()
        val celyRules = notificationTrigger(cely, posted())?.extract.orEmpty()

        assertEquals("1234", notificationPayload(posted(), kodRules)["notif:kod"])
        assertEquals(null, notificationPayload(posted(), kodRules)["notif:vse"])
        assertEquals("Zásilkovna\nTvůj výdejní kód", notificationPayload(posted(), celyRules)["notif:vse"])
        assertEquals(emptyList<ExtractRule>(), notificationTrigger(watching(), posted())?.extract)
    }

    @Test
    fun `a rule without a match simply does not add the field`() {
        val payload = notificationPayload(posted(), listOf(ExtractRule(name = "cislo", kind = ExtractKind.AMOUNT)))

        assertEquals(mapOf("notif:title" to "Zásilkovna", "notif:text" to "Tvůj výdejní kód: 1234"), payload.fields)
    }

    @Test
    fun `a rule cannot replace the title or the text`() {
        val payload = notificationPayload(
            posted(),
            listOf(
                ExtractRule(name = "title", after = "kód", kind = ExtractKind.NUMBER),
                ExtractRule(name = "text", kind = ExtractKind.WORD),
            ),
        )

        assertEquals(mapOf("notif:title" to "Zásilkovna", "notif:text" to "Tvůj výdejní kód: 1234"), payload.fields)
    }

    @Test
    fun `the extracted value is substituted into the template`() {
        val content = TileContent.Template(
            layout = TemplateLayout.TEXT_ONLY,
            text = "Code {notif:kod}",
            items = listOf(TemplateItem(actions = listOf(TapAction.App("cz.zasilkovna.app")))),
        )
        val payload = notificationPayload(posted(), listOf(KOD))

        assertEquals("Code 1234", (resolveTemplateValues(content, payload) as TileContent.Template).text)
    }

    @Test
    fun `a template without an extracted value is skipped`() {
        val content = TileContent.Template(
            layout = TemplateLayout.TEXT_ONLY,
            text = "Code {notif:kod}",
            items = listOf(TemplateItem(actions = listOf(TapAction.App("cz.zasilkovna.app")))),
        )
        val payload = notificationPayload(posted(text = "Zásilka je na cestě"), listOf(KOD))

        assertEquals(null, resolveTemplateValues(content, payload))
    }

    @Test
    fun `a template with a value from an unknown namespace can never be filled`() {
        val content = TileContent.Template(
            layout = TemplateLayout.TEXT_ONLY,
            text = "Code {other:kod}",
            items = listOf(TemplateItem(actions = listOf(TapAction.App("cz.zasilkovna.app")))),
        )

        assertEquals(null, resolveTemplateValues(content, notificationPayload(posted(), listOf(KOD))))
    }
}

private val KOD = ExtractRule(name = "kod", after = "kód", kind = ExtractKind.NUMBER)

private val CONDITION = Condition.NotificationFrom(packageName = "cz.zasilkovna.app")

@Suppress("LongParameterList")
private fun posted(
    key: String = "first",
    packageName: String = "cz.zasilkovna.app",
    title: String? = "Zásilkovna",
    text: String? = "Tvůj výdejní kód: 1234",
    isGroupSummary: Boolean = false,
    progress: TileProgress? = null,
    semantic: SemanticStyle = SemanticStyle.UNSPECIFIED,
    reply: NotificationReply? = null,
): PostedNotification = PostedNotification(
    key = key,
    packageName = packageName,
    title = title,
    text = text,
    isGroupSummary = isGroupSummary,
    progress = progress,
    semantic = semantic,
    reply = reply,
)

private fun watching(
    id: String = "shipment",
    packageName: String = "cz.zasilkovna.app",
): Scenario = scenario(id, conditions = listOf(Condition.NotificationFrom(packageName = packageName)))
