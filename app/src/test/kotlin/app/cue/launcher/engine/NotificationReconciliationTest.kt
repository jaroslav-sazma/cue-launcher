package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionMatch
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TriggerPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
        assertTrue(needsNotificationPosts(listOf(scenario("parcel", conditions = listOf(parcel())))))
        val disabled = scenario("parcel", conditions = listOf(parcel())).copy(enabled = false)
        assertFalse(needsNotificationPosts(listOf(disabled)))
        assertFalse(needsNotificationPosts(listOf(scenario("music", conditions = listOf(Condition.MediaPlaying())))))
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
        notificationReconciliation(scenarios, activations, state, NotificationReplay(active, since, now))

    @Test
    fun `the packages scenarios listen to are collected lower-cased`() {
        val scenarios = listOf(
            scenario("a", conditions = listOf(Condition.NotificationFrom(packageName = " Com.Example.Parcel "))),
            scenario("b", conditions = listOf(Condition.Charging())),
            scenario("c", conditions = listOf(Condition.NotificationFrom(packageName = "com.off")))
                .copy(enabled = false),
        )

        assertEquals(setOf("com.example.parcel"), notificationPackages(scenarios))
    }

    private fun fromNotification(key: String) =
        Activation("parcel", since = 0L, expiresAt = HOUR, payload = TriggerPayload(notificationKey = key))

    private fun parcel() = Condition.NotificationFrom(packageName = PARCEL_APP, durationSec = (DURATION / 1000).toInt())

    private fun post(key: String, at: Long) = PostedNotification(
        key = key,
        packageName = PARCEL_APP,
        title = "Parcel",
        text = "Pickup code 1234",
        postTime = at,
    )

    private companion object {
        const val PARCEL_APP = "com.example.parcel"
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DURATION = 10 * MINUTE
        const val SINCE = MINUTE
    }
}
