package app.cue.launcher.data.launchlog

import app.cue.launcher.data.layout.ComponentRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppLaunchEntityTest {

    private val chrome = ComponentRef(
        packageName = "com.android.chrome",
        className = "com.google.android.apps.chrome.Main",
        userSerial = 0,
    )

    @Test
    fun `a row converts back to its component`() {
        assertEquals(chrome, row(chrome, launchedAt = 1_758_000_000_000).toComponentRef())
    }

    @Test
    fun `the same app in two profiles is two entries`() {
        val work = chrome.copy(userSerial = 11)
        val stats = launchStatsOf(
            listOf(row(chrome, 100, searchHits = 3), row(work, 200)),
        )

        assertEquals(2, stats.size)
        assertEquals(LaunchStats(lastLaunchedAt = 100, searchHits = 3), stats[chrome])
        assertEquals(LaunchStats(lastLaunchedAt = 200, searchHits = 0), stats[work])
    }

    @Test
    fun `an empty log gives an empty map`() {
        assertEquals(emptyMap<ComponentRef, LaunchStats>(), launchStatsOf(emptyList()))
    }

    private fun row(component: ComponentRef, launchedAt: Long, searchHits: Int = 0) =
        AppLaunchEntity(
            packageName = component.packageName,
            activityClassName = component.className,
            userSerial = component.userSerial,
            launchedAt = launchedAt,
            searchHits = searchHits,
        )
}
