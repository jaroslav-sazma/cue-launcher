package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Condition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationEndedRulesTest {

    @Test
    fun `navigation that ran for minutes matches`() {
        assertTrue(notificationEndedMatches(NAVIGATION_ENDED, navigation(), endedAt = 10 * MINUTE))
    }

    @Test
    fun `the blink at the start of navigation does not match`() {
        assertFalse(notificationEndedMatches(NAVIGATION_ENDED, navigation(), endedAt = 90L))
    }

    @Test
    fun `the time counts from the first post, not the last update`() {
        val updated = navigation(firstPostTime = 0L, postTime = 10 * MINUTE)

        assertTrue(notificationEndedMatches(NAVIGATION_ENDED, updated, endedAt = 10 * MINUTE + 1))
    }

    @Test
    fun `an update keeps the time of the first post and a new notification starts its own`() {
        val first = navigation(firstPostTime = 1_000L)
        val update = navigation(firstPostTime = 9_000L)

        assertEquals(1_000L, update.following(first).firstPostTime)
        assertEquals(9_000L, update.following(null).firstPostTime)
    }

    @Test
    fun `without a minimum any end matches`() {
        val anyEnd = NAVIGATION_ENDED.copy(minLastedSec = null)

        assertTrue(notificationEndedMatches(anyEnd, navigation(), endedAt = 0L))
    }

    @Test
    fun `an ongoing-only condition skips an ordinary notification`() {
        val ordinary = navigation().copy(ongoing = false)

        assertFalse(notificationEndedMatches(NAVIGATION_ENDED, ordinary, endedAt = 10 * MINUTE))
        assertTrue(notificationEndedMatches(NAVIGATION_ENDED.copy(ongoing = false), ordinary, endedAt = 10 * MINUTE))
    }

    @Test
    fun `the package matches regardless of case and padding`() {
        val condition = NAVIGATION_ENDED.copy(packageName = " COM.Google.Android.Apps.Maps ")

        assertTrue(notificationEndedMatches(condition, navigation(), endedAt = 10 * MINUTE))
        assertFalse(notificationEndedMatches(condition, navigation().copy(packageName = "cz.alza"), 10 * MINUTE))
    }

    @Test
    fun `a group summary never matches`() {
        val summary = navigation().copy(isGroupSummary = true)

        assertFalse(notificationEndedMatches(NAVIGATION_ENDED, summary, endedAt = 10 * MINUTE))
    }

    @Test
    fun `a disabled scenario does not listen`() {
        val parking = scenario("parking", conditions = listOf(NAVIGATION_ENDED))

        assertEquals(listOf(parking), notificationEndedListeners(listOf(parking), navigation(), 10 * MINUTE))
        assertTrue(
            notificationEndedListeners(listOf(parking.copy(enabled = false)), navigation(), 10 * MINUTE).isEmpty(),
        )
    }

    @Test
    fun `the payload carries the texts but no key of a notification that is gone`() {
        val payload = notificationEndedPayload(navigation())

        assertEquals(mapOf("notif:title" to "Arriving", "notif:text" to "at Taco Bell"), payload.fields)
        assertNull(payload.notificationKey)
    }

    @Test
    fun `the end of a notification binds the listener and watches its app`() {
        val parking = scenario("parking", conditions = listOf(NAVIGATION_ENDED))

        assertTrue(needsNotificationPosts(listOf(parking)))
        assertEquals(setOf(MAPS), notificationPackages(listOf(parking)))
        assertFalse(needsNotificationPosts(listOf(parking.copy(enabled = false))))
        assertTrue(usesNotificationListener(listOf(parking)))
    }
}

private const val MINUTE = 60_000L

private const val MAPS = "com.google.android.apps.maps"

private val NAVIGATION_ENDED = Condition.NotificationEnded(
    packageName = MAPS,
    ongoing = true,
    minLastedSec = 120,
    durationSec = 1800,
)

private fun navigation(firstPostTime: Long = 0L, postTime: Long = firstPostTime): PostedNotification =
    PostedNotification(
        key = "0|$MAPS|1|null|10172",
        packageName = MAPS,
        title = "Arriving",
        text = "at Taco Bell",
        postTime = postTime,
        ongoing = true,
        firstPostTime = firstPostTime,
    )
