package app.cue.launcher.feature.icons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.cue.launcher.R
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.notifications.BadgeKey

// Holds the state rather than the map, so a change never recomposes through the composition local.
@Stable
class NotificationCounts(private val counts: State<Map<BadgeKey, Int>>) {

    fun of(component: ComponentRef): Int =
        counts.value[BadgeKey(component.packageName, component.userSerial)] ?: 0

    // Counts are kept per package, and a folder may hold two activities of the same one.
    fun of(components: List<ComponentRef>): Int =
        components.distinctBy { it.packageName to it.userSerial }.sumOf(::of)
}

// null while badges are off, so no cell composes any badge machinery at all.
val LocalNotificationCounts: ProvidableCompositionLocal<NotificationCounts?> = compositionLocalOf { null }

@Composable
internal fun rememberNotificationCount(component: ComponentRef): (() -> Int)? =
    rememberCount(component) { of(it) }

@Composable
internal fun rememberNotificationCount(components: List<ComponentRef>): (() -> Int)? =
    rememberCount(components) { of(it) }

// Every count reads the same map, but through a derived state that notifies only when its own
// number moves, so one app's notification redraws its badge and leaves every other one alone.
// [read] is kept only from the first composition per key, so it gets the key instead of capturing.
@Composable
private fun <K : Any> rememberCount(key: K, read: NotificationCounts.(K) -> Int): (() -> Int)? {
    val counts = LocalNotificationCounts.current ?: return null
    return remember(counts, key) {
        val count = derivedStateOf { counts.read(key) }
        val current: () -> Int = { count.value }
        current
    }
}

// Semantics blocks are re-run on their own when a state they read changes.
@Composable
internal fun Modifier.notificationCountSemantics(count: (() -> Int)?): Modifier {
    if (count == null) return this
    val resources = LocalResources.current
    return semantics {
        val shown = count()
        if (shown > 0) {
            stateDescription = resources.getQuantityString(R.plurals.notification_badge_count, shown, shown)
        }
    }
}
