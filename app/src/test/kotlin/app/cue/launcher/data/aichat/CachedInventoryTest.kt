package app.cue.launcher.data.aichat

import app.cue.launcher.data.ha.HaService
import app.cue.launcher.data.scenario.TapAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class CachedInventoryTest {

    @Test
    fun `reads are shared within the lifetime and repeated after it`() = runTest {
        var now = 0L
        var reads = 0
        val cached = CachedInventory(
            source = { InventorySnapshot(apps = listOf(InventoryApp("App ${++reads}", "p"))) },
            now = { now },
            lifetime = 1.minutes,
        )

        val first = cached.snapshot(AiChatShare.ALL)
        now += 59_000
        val second = cached.snapshot(AiChatShare.ALL)
        now += 2_000
        val third = cached.snapshot(AiChatShare.ALL)

        assertEquals(first, second)
        assertEquals("App 2", third.apps.single().label)
        assertEquals(2, reads)
    }

    @Test
    fun `an app's actions are read once within the lifetime and again after it or a clear`() = runTest {
        var now = 0L
        val reads = mutableListOf<String>()
        val cached = CachedInventory(
            source = object : InventorySource {
                override suspend fun snapshot(share: AiChatShare) = InventorySnapshot.EMPTY

                override suspend fun appActions(packageName: String, shortcuts: Boolean): List<InventoryAppAction>? {
                    reads += packageName
                    return null
                }
            },
            now = { now },
            lifetime = 1.minutes,
        )

        cached.appActions("com.maps")
        cached.appActions("com.maps")
        cached.appActions("com.clock")
        now += 61_000
        cached.appActions("com.maps")
        cached.clear()
        cached.appActions("com.maps")

        assertEquals(listOf("com.maps", "com.clock", "com.maps", "com.maps"), reads)
    }

    @Test
    fun `the actions of different apps are read side by side`() = runTest {
        var running = 0
        var mostAtOnce = 0
        val released = CompletableDeferred<Unit>()
        val cached = CachedInventory(
            source = object : InventorySource {
                override suspend fun snapshot(share: AiChatShare) = InventorySnapshot.EMPTY

                override suspend fun appActions(packageName: String, shortcuts: Boolean): List<InventoryAppAction> {
                    mostAtOnce = maxOf(mostAtOnce, ++running)
                    released.await()
                    running--
                    return emptyList()
                }
            },
        )

        val reads = listOf("com.maps", "com.clock", "com.mail").map { async { cached.appActions(it) } }
        runCurrent()
        released.complete(Unit)
        reads.awaitAll()

        assertEquals(3, mostAtOnce)
    }

    @Test
    fun `a read without shortcuts and a full one never stand in for each other`() = runTest {
        val shortcut = InventoryAppAction("Home", TapAction.Shortcut("com.maps", "home"))
        val link = InventoryAppAction("Map", TapAction.DeepLink("geo:0,0"))
        val reads = mutableListOf<Boolean>()
        val cached = CachedInventory(
            source = object : InventorySource {
                override suspend fun snapshot(share: AiChatShare) = InventorySnapshot.EMPTY

                override suspend fun appActions(packageName: String, shortcuts: Boolean): List<InventoryAppAction> {
                    reads += shortcuts
                    return if (shortcuts) listOf(shortcut, link) else listOf(link)
                }
            },
        )

        val without = cached.appActions("com.maps", shortcuts = false)
        val full = cached.appActions("com.maps")
        val withoutAgain = cached.appActions("com.maps", shortcuts = false)
        val fullAgain = cached.appActions("com.maps")

        assertEquals(listOf(link), without)
        assertEquals(listOf(shortcut, link), full)
        assertEquals(without, withoutAgain)
        assertEquals(full, fullAgain)
        assertEquals(listOf(false, true), reads)
    }

    @Test
    fun `the services are read once within the lifetime and again after it or a clear`() = runTest {
        var now = 0L
        var reads = 0
        val cached = CachedInventory(
            source = object : InventorySource {
                override suspend fun snapshot(share: AiChatShare) = InventorySnapshot.EMPTY

                override suspend fun haServices(): List<HaService>? =
                    if (++reads == 1) null else listOf(HaService("light.toggle$reads"))
            },
            now = { now },
            lifetime = 1.minutes,
        )

        val unknown = cached.haServices()
        now += 59_000
        val stillUnknown = cached.haServices()
        now += 2_000
        val second = cached.haServices()
        val shared = cached.haServices()
        cached.clear()
        val third = cached.haServices()

        assertEquals(null, unknown)
        assertEquals(null, stillUnknown)
        assertEquals(listOf(HaService("light.toggle2")), second)
        assertEquals(second, shared)
        assertEquals(listOf(HaService("light.toggle3")), third)
        assertEquals(3, reads)
    }

    @Test
    fun `a snapshot does not read the services`() = runTest {
        var reads = 0
        val cached = CachedInventory(
            source = object : InventorySource {
                override suspend fun snapshot(share: AiChatShare) = InventorySnapshot.EMPTY

                override suspend fun haServices(): List<HaService>? {
                    reads++
                    return null
                }
            },
        )

        cached.snapshot(AiChatShare.ALL)

        assertEquals(0, reads)
    }

    @Test
    fun `a cleared cache reads again`() = runTest {
        var reads = 0
        val cached = CachedInventory(
            source = { InventorySnapshot(apps = listOf(InventoryApp("App ${++reads}", "p"))) },
            now = { 0L },
        )

        cached.snapshot(AiChatShare.ALL)
        cached.clear()
        val after = cached.snapshot(AiChatShare.ALL)

        assertEquals("App 2", after.apps.single().label)
    }

    @Test
    fun `a snapshot that kept a part back never stands in for the whole phone`() = runTest {
        val asked = mutableListOf<AiChatShare>()
        val cached = CachedInventory(source = { share -> InventorySnapshot.EMPTY.also { asked += share } })
        val partial = AiChatShare(bluetooth = false, homeAssistant = false)

        cached.snapshot(partial)
        cached.snapshot(partial)
        cached.snapshot(AiChatShare.ALL)

        assertEquals(listOf(partial, AiChatShare.ALL), asked)
    }
}
