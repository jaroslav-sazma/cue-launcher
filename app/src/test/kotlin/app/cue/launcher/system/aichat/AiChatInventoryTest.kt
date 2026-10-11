package app.cue.launcher.system.aichat

import app.cue.launcher.data.aichat.AiChatOffer
import app.cue.launcher.data.aichat.AiChatShare
import app.cue.launcher.data.aichat.CachedInventory
import app.cue.launcher.data.aichat.InventoryApp
import app.cue.launcher.data.aichat.InventoryAppAction
import app.cue.launcher.data.aichat.InventorySnapshot
import app.cue.launcher.data.aichat.InventorySource
import app.cue.launcher.data.ha.HaEntity
import app.cue.launcher.data.ha.HaService
import app.cue.launcher.data.scenario.TapAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AiChatInventoryTest {

    private val apps = listOf(InventoryApp("Maps", "com.maps"), InventoryApp("Clock", "com.clock"))

    @Test
    fun `every app's actions and the services are read along with the snapshot`() = runTest {
        val source = FakeSource(InventorySnapshot(apps = apps, haEntities = listOf(HaEntity("light.desk", "Desk"))))

        val inventory = fullInventory(source, AiChatShare.ALL)

        assertEquals(setOf("com.maps", "com.clock"), source.actionReads.toSet())
        assertEquals(setOf("com.maps", "com.clock"), inventory.appActions.keys)
        assertEquals(listOf(HaService("light.toggle")), inventory.haServices)
    }

    @Test
    fun `the services are not asked for without a listed entity`() = runTest {
        val source = FakeSource(InventorySnapshot(apps = apps, haEntities = null))

        val inventory = fullInventory(source, AiChatShare.ALL)

        assertEquals(0, source.serviceReads.get())
        assertNull(inventory.haServices)
    }

    @Test
    fun `Bluetooth and Home Assistant kept back are not read, the apps' actions still are`() = runTest {
        val source = FakeSource(InventorySnapshot(apps = apps, haEntities = listOf(HaEntity("light.desk", "Desk"))))
        val nothing = AiChatShare(bluetooth = false, homeAssistant = false, shortcuts = false)

        val inventory = fullInventory(source, nothing)

        assertEquals(listOf(nothing), source.shares.toList())
        assertEquals(0, source.serviceReads.get())
        assertNull(inventory.haServices)
        assertEquals(setOf("com.maps", "com.clock"), source.actionReads.toSet())
        assertEquals(setOf("com.maps", "com.clock"), inventory.appActions.keys)
    }

    @Test
    fun `shortcuts kept back are not read at all, and neither they nor capabilities reach the brief`() = runTest {
        val source = FakeSource(InventorySnapshot(apps = apps), pause = Duration.ZERO)
        val share = AiChatShare(shortcuts = false)

        val text = collectAiChatContext(source, Locale.ENGLISH, TEST_ROUTE, share, AiChatOffer.ALL)
        val lines = text.substringAfter("### Apps").substringBefore("### Bluetooth").lines()

        assertEquals(listOf(false, false), source.shortcutReads.toList())
        assertFalse(lines.any { it.startsWith("shortcut ") || it.startsWith("capability ") }, text)
        listOf("deeplink Home → https://com.maps/home", "activity Settings → com.maps/com.maps.SettingsActivity")
            .forEach { assertTrue(it in lines, it) }
    }

    @Test
    fun `shared shortcuts are read with the capabilities they carry`() = runTest {
        val source = FakeSource(InventorySnapshot(apps = apps), pause = Duration.ZERO)

        val lines = collectAiChatContext(source, Locale.ENGLISH, TEST_ROUTE, AiChatShare.ALL, AiChatOffer.ALL).lines()

        assertEquals(listOf(true, true), source.shortcutReads.toList())
        listOf(
            "shortcut Open → com.maps/open",
            "capability Thing → com.maps/actions.intent.GET_THING",
            "deeplink Home → https://com.maps/home",
        ).forEach { assertTrue(it in lines, it) }
    }

    @Test
    fun `a part the phone does not offer is not read, whatever the stored choice`() = runTest {
        val source = FakeSource(InventorySnapshot(apps = apps, haEntities = listOf(HaEntity("light.desk", "Desk"))))
        val offer = AiChatOffer(bluetooth = false, homeAssistant = false)

        val text = collectAiChatContext(source, Locale.ENGLISH, TEST_ROUTE, AiChatShare.ALL, offer)

        assertEquals(listOf(AiChatShare(bluetooth = false, homeAssistant = false)), source.shares.toList())
        assertEquals(0, source.serviceReads.get())
        assertTrue("Home Assistant is not connected to Cue:" in text && "Cue: has no Bluetooth permission" in text)
        assertFalse("light.desk" in text)
    }

    @Test
    fun `keeping back Home Assistant alone still reads every app's actions`() = runTest {
        val source = FakeSource(InventorySnapshot(apps = apps, haEntities = listOf(HaEntity("light.desk", "Desk"))))

        fullInventory(source, AiChatShare(homeAssistant = false))

        assertEquals(0, source.serviceReads.get())
        assertEquals(setOf("com.maps", "com.clock"), source.actionReads.toSet())
    }

    @Test
    fun `an app whose actions do not come in time is listed without them`() = runTest {
        val source = FakeSource(InventorySnapshot(apps = apps), slow = setOf("com.clock"))

        val inventory = fullInventory(source, AiChatShare.ALL)

        assertEquals(setOf("com.maps"), inventory.appActions.keys)
    }

    @Test
    fun `through the cache the reads run side by side and the brief carries them`() = runTest {
        val many = (1..40).map { InventoryApp("App $it", "com.app$it") }
        val source = FakeSource(InventorySnapshot(apps = many), pause = 20.milliseconds)

        val text =
            collectAiChatContext(CachedInventory(source), Locale.ENGLISH, TEST_ROUTE, AiChatShare.ALL, AiChatOffer.ALL)

        assertTrue(source.mostAtOnce.get() in 2..4, "${source.mostAtOnce.get()} at once")
        assertTrue("shortcut Open → com.app40/open" in text, text)
    }

    private class FakeSource(
        private val snapshot: InventorySnapshot,
        private val slow: Set<String> = emptySet(),
        private val pause: Duration = 1.seconds,
    ) : InventorySource {
        val actionReads = ConcurrentLinkedQueue<String>()
        val shortcutReads = ConcurrentLinkedQueue<Boolean>()
        val serviceReads = AtomicInteger()
        val shares = ConcurrentLinkedQueue<AiChatShare>()
        val mostAtOnce = AtomicInteger()
        private val running = AtomicInteger()

        override suspend fun snapshot(share: AiChatShare): InventorySnapshot = snapshot.also { shares += share }

        override suspend fun appActions(packageName: String, shortcuts: Boolean): List<InventoryAppAction> {
            actionReads += packageName
            shortcutReads += shortcuts
            val now = running.incrementAndGet()
            mostAtOnce.accumulateAndGet(now) { most, current -> maxOf(most, current) }
            delay(if (packageName in slow) 1.hours else pause)
            running.decrementAndGet()
            val always = listOf(
                InventoryAppAction("Home", TapAction.DeepLink("https://$packageName/home")),
                InventoryAppAction("Settings", TapAction.ActivityLaunch(packageName, "$packageName.SettingsActivity")),
            )
            if (!shortcuts) return always
            return listOf(
                InventoryAppAction("Open", TapAction.Shortcut(packageName, "open")),
                InventoryAppAction("Thing", TapAction.Capability(packageName, "actions.intent.GET_THING")),
            ) + always
        }

        override suspend fun haServices(): List<HaService> {
            serviceReads.incrementAndGet()
            return listOf(HaService("light.toggle"))
        }
    }
}
