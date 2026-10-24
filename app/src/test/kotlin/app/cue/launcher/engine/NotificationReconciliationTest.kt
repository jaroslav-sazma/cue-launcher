package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionMatch
import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TriggerPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset

class NotificationReconciliationTest {

    @Test
    fun `a post made while away fires its scenario`() {
        val plan = reconcile(active = listOf(post("a", at = 2 * MINUTE)))

        assertEquals(listOf("parcel" to "a"), plan.fire.map { it.scenario.id to it.notification.key })
        assertTrue(plan.end.isEmpty())
    }

    @Test
    fun `a post from before the window was heard live and is not replayed`() {
        val plan = reconcile(active = listOf(post("a", at = SINCE)), since = SINCE)

        assertTrue(plan.fire.isEmpty())
    }

    @Test
    fun `the latest of several posts is the one that fires`() {
        val plan = reconcile(active = listOf(post("late", at = 3 * MINUTE), post("early", at = 2 * MINUTE)))

        assertEquals(listOf("late"), plan.fire.map { it.notification.key })
    }

    @Test
    fun `a post whose countdown has already run out fires nothing`() {
        val plan = reconcile(active = listOf(post("a", at = 2 * MINUTE)), now = 2 * MINUTE + DURATION)

        assertTrue(plan.fire.isEmpty())
    }

    @Test
    fun `a running activation that lasts as long or longer is left alone`() {
        val running = Activation("parcel", since = 0L, expiresAt = 2 * MINUTE + DURATION)

        val plan = reconcile(active = listOf(post("a", at = 2 * MINUTE)), activations = listOf(running))

        assertTrue(plan.fire.isEmpty())
    }

    @Test
    fun `a running activation is restarted by a later post`() {
        val running = Activation("parcel", since = 0L, expiresAt = MINUTE + DURATION)

        val plan = reconcile(active = listOf(post("a", at = 2 * MINUTE)), activations = listOf(running))

        assertEquals(listOf("parcel"), plan.fire.map { it.scenario.id })
    }

    @Test
    fun `the state gate is judged now`() {
        val gated = scenario("parcel", conditions = listOf(parcel(), Condition.Charging()))

        val posted = listOf(post("a", at = 2 * MINUTE))

        val now = reconcile(listOf(gated), active = posted, state = SystemState(charging = true))
        val not = reconcile(listOf(gated), active = posted, state = SystemState(charging = false))

        assertEquals(1, now.fire.size)
        assertTrue(not.fire.isEmpty())
    }

    @Test
    fun `under ANY a holding state carries the scenario and an old post still counts`() {
        val any = scenario("parcel", conditions = listOf(parcel(), Condition.Charging()))
            .copy(match = ConditionMatch.ANY)

        val plan = reconcile(
            listOf(any),
            active = listOf(post("a", at = 2 * MINUTE)),
            state = SystemState(charging = true),
            now = 2 * MINUTE + 10 * DURATION,
        )

        assertEquals(listOf("parcel"), plan.fire.map { it.scenario.id })
    }

    @Test
    fun `a group summary fires nothing`() {
        val plan = reconcile(active = listOf(post("a", at = 2 * MINUTE).copy(isGroupSummary = true)))

        assertTrue(plan.fire.isEmpty())
    }

    @Test
    fun `an activation whose notification is gone ends`() {
        val running = fromNotification("a")

        val plan = reconcile(active = emptyList(), activations = listOf(running))

        assertEquals(listOf("parcel"), plan.end)
    }

    @Test
    fun `another matching notification keeps the activation running`() {
        val running = fromNotification("a")

        val plan = reconcile(active = listOf(post("b", at = SINCE - MINUTE)), activations = listOf(running))

        assertTrue(plan.end.isEmpty())
    }

    @Test
    fun `a scenario that no longer has a notification condition is not ended here`() {
        val edited = scenario("parcel", conditions = listOf(Condition.Charging()))

        val plan = reconcile(listOf(edited), activations = listOf(fromNotification("a")), active = emptyList())

        assertTrue(plan.end.isEmpty())
    }

    @Test
    fun `an activation not started by a notification is not ended`() {
        val running = Activation("parcel", since = 0L, expiresAt = HOUR)

        val plan = reconcile(active = emptyList(), activations = listOf(running))

        assertTrue(plan.end.isEmpty())
    }

    @Test
    fun `only an enabled scenario with a notification condition needs the posts`() {
        assertTrue(needsNotificationPosts(listOf(scenario("parcel", conditions = listOf(parcel()))), emptyList()))
        val disabled = scenario("parcel", conditions = listOf(parcel())).copy(enabled = false)
        assertFalse(needsNotificationPosts(listOf(disabled), emptyList()))
        val music = scenario("music", conditions = listOf(Condition.MediaPlaying()))
        assertFalse(needsNotificationPosts(listOf(music), emptyList()))
    }

    @Test
    fun `a running activation waiting for a notification needs the posts`() {
        val washer = scenario("washer", conditions = listOf(Condition.HaEvent(code = "washer", until = DELIVERED)))

        assertTrue(needsNotificationPosts(emptyList(), listOf(waiting(since = 0L))))
        assertFalse(needsNotificationPosts(listOf(washer), emptyList()))
        val alarm = Activation("wake", since = 0L, payload = TriggerPayload(until = EndEvent.Alarm))
        assertFalse(needsNotificationPosts(emptyList(), listOf(alarm)))
    }

    @Test
    fun `the packages of end events are watched too`() {
        val washer = scenario("washer", conditions = listOf(Condition.HaEvent(code = "washer", until = DELIVERED)))
        val edited = Activation(
            "old",
            since = 0L,
            payload = TriggerPayload(until = EndEvent.Notification(packageName = " Com.Example.Old ")),
        )

        assertEquals(setOf(PARCEL_APP), notificationPackages(listOf(washer), emptyList()))
        assertEquals(setOf("com.example.old"), notificationPackages(emptyList(), listOf(edited)))
        assertEquals(emptySet<String>(), notificationPackages(listOf(washer.copy(enabled = false)), emptyList()))
    }

    @Test
    fun `a still posted end notification ends the activation waiting for it`() {
        val plan = reconcile(
            listOf(tracking()),
            activations = listOf(waiting(since = 0L)),
            active = listOf(post("d", at = 2 * MINUTE, text = "Delivered")),
        )

        assertEquals(listOf("parcel"), plan.end)
        assertTrue(plan.fire.isEmpty())
    }

    @Test
    fun `an end notification older than the activation ends nothing`() {
        val plan = reconcile(
            listOf(tracking()),
            activations = listOf(waiting(since = 3 * MINUTE)),
            active = listOf(post("d", at = 2 * MINUTE, text = "Delivered")),
        )

        assertTrue(plan.end.isEmpty())
    }

    @Test
    fun `an end posted before the activation fired again ends nothing`() {
        val refired = Activation(
            "parcel",
            since = 0L,
            payload = TriggerPayload(until = DELIVERED, firedAt = 3 * MINUTE),
        )

        val plan = reconcile(
            listOf(tracking()),
            activations = listOf(refired),
            active = listOf(post("d", at = 2 * MINUTE, text = "Delivered")),
        )

        assertTrue(plan.end.isEmpty())
        assertTrue(plan.fire.isEmpty())
    }

    @Test
    fun `an end posted after the last fire still ends a re-fired activation`() {
        val refired = Activation(
            "parcel",
            since = 0L,
            payload = TriggerPayload(until = DELIVERED, firedAt = 2 * MINUTE),
        )

        val plan = reconcile(
            listOf(tracking()),
            activations = listOf(refired),
            active = listOf(post("d", at = 3 * MINUTE, text = "Delivered")),
            now = 3 * MINUTE,
        )

        assertEquals(listOf("parcel"), plan.end)
    }

    @Test
    fun `a capped replayed firing restarts an activation held by its end alone`() {
        val capped = scenario(
            "parcel",
            conditions = listOf(
                Condition.NotificationFrom(
                    packageName = PARCEL_APP,
                    contains = "on its way",
                    durationSec = (DURATION / 1000).toInt(),
                    until = DELIVERED,
                ),
            ),
        )

        val plan = reconcile(
            listOf(capped),
            activations = listOf(waiting(since = 0L)),
            active = listOf(post("w", at = 2 * MINUTE, text = "On its way")),
        )

        assertEquals(listOf("w"), plan.fire.map { it.notification.key })
        assertTrue(plan.end.isEmpty())
    }

    @Test
    fun `an end posted before a later firing ends the old activation and the firing starts a new one`() {
        val plan = reconcile(
            listOf(tracking()),
            activations = listOf(waiting(since = 0L)),
            active = listOf(
                post("d", at = 2 * MINUTE, text = "Delivered"),
                post("w", at = 3 * MINUTE, text = "On its way"),
            ),
        )

        assertEquals(listOf("parcel"), plan.end)
        assertEquals(listOf("w"), plan.fire.map { it.notification.key })
    }

    @Test
    fun `an end posted after the last firing leaves nothing running`() {
        val posts = listOf(
            post("w", at = 2 * MINUTE, text = "On its way"),
            post("d", at = 3 * MINUTE, text = "Delivered"),
        )

        val running = reconcile(listOf(tracking()), activations = listOf(waiting(since = 0L)), active = posts)
        val idle = reconcile(listOf(tracking()), active = posts)

        assertEquals(listOf("parcel"), running.end)
        assertTrue(running.fire.isEmpty())
        assertTrue(idle.end.isEmpty())
        assertTrue(idle.fire.isEmpty())
    }

    @Test
    fun `a post that both ends and fires its scenario restarts it`() {
        val every = EndEvent.Notification(packageName = PARCEL_APP)
        val restarting = scenario(
            "parcel",
            conditions = listOf(Condition.NotificationFrom(packageName = PARCEL_APP, until = every)),
        )
        val running = Activation("parcel", since = 0L, payload = TriggerPayload(until = every))

        val plan = reconcile(
            listOf(restarting),
            activations = listOf(running),
            active = listOf(post("a", at = 2 * MINUTE)),
        )

        assertEquals(listOf("parcel"), plan.end)
        assertEquals(listOf("a"), plan.fire.map { it.notification.key })
    }

    @Test
    fun `a replayed firing with an end and no duration fires however old it is`() {
        val plan = reconcile(
            listOf(tracking()),
            active = listOf(post("w", at = 2 * MINUTE, text = "On its way")),
            now = 2 * MINUTE + 100 * DURATION,
        )

        assertEquals(listOf("w"), plan.fire.map { it.notification.key })
    }

    @Test
    fun `a tile with an end outlives its dismissed notification and still ends by its end`() {
        val scenario = tracking()
        val fired = post("w", at = 0L, text = "On its way")
        val trigger = notificationTrigger(scenario, fired)
        val running = requireNotNull(
            activationOf(
                scenario,
                Instant.ofEpochMilli(0L).atZone(ZoneOffset.UTC),
                payload = notificationPayload(fired, dismissible = trigger?.until == null),
                trigger = trigger,
            ),
        )

        val dismissed = reconcile(listOf(scenario), activations = listOf(running), active = emptyList())
        val delivered = reconcile(
            listOf(scenario),
            activations = listOf(running),
            active = listOf(post("d", at = 2 * MINUTE, text = "Delivered")),
        )

        assertTrue(dismissed.end.isEmpty())
        assertEquals(listOf("parcel"), delivered.end)
    }

    @Test
    fun `under ANY the post of a condition with an end does not keep a plain tile`() {
        val impulse = Condition.NotificationFrom(packageName = WATCH_APP, until = DELIVERED)
        val any = scenario("parcel", conditions = listOf(impulse, parcel())).copy(match = ConditionMatch.ANY)
        val watch = PostedNotification(key = "watch", packageName = WATCH_APP, postTime = SINCE - MINUTE)

        val plan = reconcile(listOf(any), activations = listOf(fromNotification("a")), active = listOf(watch))

        assertEquals(listOf("parcel"), plan.end)
    }

    @Suppress("LongParameterList")
    private fun reconcile(
        scenarios: List<Scenario> = listOf(scenario("parcel", conditions = listOf(parcel()))),
        activations: List<Activation> = emptyList(),
        active: List<PostedNotification>,
        state: SystemState = SystemState(),
        since: Long = SINCE,
        now: Long = 2 * MINUTE + DURATION / 2,
    ): NotificationReconciliation =
        notificationReconciliation(
            scenarios,
            activations,
            state,
            NotificationReplay(active, since, now, ZoneOffset.UTC),
        )

    @Test
    fun `the packages scenarios listen to are collected lower-cased`() {
        val scenarios = listOf(
            scenario("a", conditions = listOf(Condition.NotificationFrom(packageName = " Com.Example.Parcel "))),
            scenario("b", conditions = listOf(Condition.Charging())),
            scenario("c", conditions = listOf(Condition.NotificationFrom(packageName = "com.off")))
                .copy(enabled = false),
        )

        assertEquals(setOf("com.example.parcel"), notificationPackages(scenarios, emptyList()))
    }

    private fun tracking() = scenario(
        "parcel",
        conditions = listOf(
            Condition.NotificationFrom(packageName = PARCEL_APP, contains = "on its way", until = DELIVERED),
        ),
    )

    private fun waiting(since: Long) = Activation("parcel", since = since, payload = TriggerPayload(until = DELIVERED))

    private fun fromNotification(key: String) =
        Activation("parcel", since = 0L, expiresAt = HOUR, payload = TriggerPayload(notificationKey = key))

    private fun parcel() = Condition.NotificationFrom(packageName = PARCEL_APP, durationSec = (DURATION / 1000).toInt())

    private fun post(key: String, at: Long, text: String = "Pickup code 1234") = PostedNotification(
        key = key,
        packageName = PARCEL_APP,
        title = "Parcel",
        text = text,
        postTime = at,
    )

    private companion object {
        const val PARCEL_APP = "com.example.parcel"
        const val WATCH_APP = "com.example.watch"
        val DELIVERED = EndEvent.Notification(packageName = PARCEL_APP, contains = "delivered")
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DURATION = 10 * MINUTE
        const val SINCE = MINUTE
    }
}
