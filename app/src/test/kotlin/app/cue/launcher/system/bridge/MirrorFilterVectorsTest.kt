package app.cue.launcher.system.bridge

import android.app.Notification
import android.app.NotificationManager
import android.service.notification.NotificationListenerService.Ranking
import app.cue.launcher.data.bridge.BRIDGE_FIXTURE_CHANNEL_PREFIX
import app.cue.launcher.data.bridge.MirrorCandidate
import app.cue.launcher.data.bridge.MirrorSkip
import app.cue.launcher.data.bridge.mirrorLockscreen
import app.cue.launcher.data.bridge.mirrorSkip
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MirrorFilterVectorsTest {

    private val vectors = BridgeFixtures.load("filters.json")

    private val ownPackage = vectors.str("ownPackage")

    private val importance: Map<String, Int> =
        vectors.getValue("importance").jsonObject.mapValues { it.value.jsonPrimitive.int }

    private val visibility: Map<String, Int> =
        vectors.getValue("visibility").jsonObject.mapValues { it.value.jsonPrimitive.int }

    @Test
    fun `the vectors use the code's fixture prefix and check order`() {
        assertEquals(BRIDGE_FIXTURE_CHANNEL_PREFIX, vectors.str("fixtureChannelPrefix"))
        assertEquals(
            MirrorSkip.entries.map { it.wire },
            vectors.getValue("order").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            mapOf(
                "unspecified" to NotificationManager.IMPORTANCE_UNSPECIFIED,
                "none" to NotificationManager.IMPORTANCE_NONE,
                "min" to NotificationManager.IMPORTANCE_MIN,
                "low" to NotificationManager.IMPORTANCE_LOW,
                "default" to NotificationManager.IMPORTANCE_DEFAULT,
                "high" to NotificationManager.IMPORTANCE_HIGH,
            ),
            importance,
        )
        assertEquals(
            mapOf(
                "no-override" to Ranking.VISIBILITY_NO_OVERRIDE,
                "secret" to Notification.VISIBILITY_SECRET,
                "private" to Notification.VISIBILITY_PRIVATE,
                "public" to Notification.VISIBILITY_PUBLIC,
            ),
            visibility,
        )
    }

    @Test
    fun `every case reaches its expected decision`() {
        val cases = vectors.objects("cases")
        assertTrue(cases.isNotEmpty())
        cases.forEach { case ->
            val notification = case.getValue("notification").jsonObject
            val excluded = notification["excluded"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty().toSet()
            val skip = mirrorSkip(candidate(notification), ownPackage, notification.flag("debugBuild"), excluded)
            val expected = if (case.str("expected") == "mirror") null else case.str("reason")
            assertEquals(expected, skip?.wire) { "case \"${case.str("name")}\"" }
        }
    }

    @Test
    fun `every reason is exercised`() {
        val reasons = vectors.objects("cases").mapNotNull { it["reason"]?.jsonPrimitive?.content }.toSet()

        assertEquals(MirrorSkip.entries.map { it.wire }.toSet(), reasons)
    }

    @Test
    fun `the lock screen's view resolves as the lock screen does`() {
        val cases = vectors.objects("lockscreenCases")
        assertTrue(cases.isNotEmpty())
        cases.forEach { case ->
            val resolved = mirrorLockscreen(
                own = visibility.getValue(case.str("own")),
                channelOverride = visibility.getValue(case.str("channel")),
                sensitiveShown = case.getValue("sensitiveShown").jsonPrimitive.boolean,
            )
            assertEquals(visibility.getValue(case.str("expected")), resolved) { "case \"${case.str("name")}\"" }
        }
    }

    private fun candidate(notification: JsonObject) = MirrorCandidate(
        packageName = notification.str("pkg"),
        channelId = notification["channel"]?.jsonPrimitive?.content.orEmpty(),
        ongoing = notification.flag("ongoing"),
        foregroundService = notification.flag("foregroundService"),
        groupSummary = notification.flag("groupSummary"),
        promotedOngoing = notification.flag("promotedOngoing"),
        category = notification["category"]?.jsonPrimitive?.content,
        mediaSession = notification.flag("mediaSession"),
        importance = importance.getValue(notification["importance"]?.jsonPrimitive?.content ?: "default"),
        clearable = notification["clearable"]?.jsonPrimitive?.boolean ?: true,
        lockscreen = visibility.getValue(notification["lockscreen"]?.jsonPrimitive?.content ?: "public"),
    )

    private fun JsonObject.flag(name: String): Boolean = this[name]?.jsonPrimitive?.boolean ?: false
}
