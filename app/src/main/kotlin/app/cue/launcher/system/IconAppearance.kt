package app.cue.launcher.system

import app.cue.launcher.data.icons.IconOverrideRepository
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.scenario.IconMode
import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.designsystem.IconTint
import app.cue.launcher.system.iconpack.IconPackRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

@SingleIn(AppScope::class)
@Inject
class IconAppearance(
    private val settings: SettingsRepository,
    private val overrides: IconOverrideRepository,
    private val iconPacks: IconPackRepository,
    private val processScope: CoroutineScope,
) {

    private var current = IconStyle()

    private val _revision = MutableStateFlow(0)

    // Moves on every change an icon on screen has to be asked for again. styleRevision moves only
    // with the look itself, which is what makes every cached bitmap worthless; a changed app makes
    // only its own entries stale, and those are dropped by package.
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val _styleRevision = AtomicInteger()

    val styleRevision: Int get() = _styleRevision.get()

    @Synchronized
    fun style(): IconStyle = current

    val mode: IconMode get() = style().mode

    fun isNative(component: ComponentRef): Boolean = component in style().overrides

    private val modeKnown = CompletableDeferred<Unit>()

    private val overridesKnown = CompletableDeferred<Unit>()

    private val styleKnown = CompletableDeferred<Unit>()

    // An icon drawn before the stored look is known would be drawn again a moment later in another
    // style — on every first home screen of a fresh process. The bound keeps a store that never
    // answers from holding the icons back for good.
    suspend fun awaitStyle() {
        if (styleKnown.isCompleted) return
        withTimeoutOrNull(STYLE_WAIT_MILLIS) {
            modeKnown.await()
            overridesKnown.await()
            iconPacks.awaitSelected()
        }
        styleKnown.complete(Unit)
    }

    fun start(scope: CoroutineScope) {
        scope.launch {
            settings.settings
                .map { it.appearance.iconMode }
                .distinctUntilChanged()
                .collect { mode ->
                    update { it.copy(mode = mode) }
                    modeKnown.complete(Unit)
                }
        }
        scope.launch {
            overrides.observeOverrides().collect { set ->
                update { it.copy(overrides = set) }
                overridesKnown.complete(Unit)
            }
        }

        scope.launch { iconPacks.revision.drop(1).collect { restyle() } }
    }

    fun setTint(tint: IconTint) = update { it.copy(tint = tint) }

    fun redrawIcons() = _revision.update { it + 1 }

    fun setNative(component: ComponentRef, native: Boolean) {
        processScope.launch { overrides.setOverride(component, native) }
    }

    @Synchronized
    private fun update(transform: (IconStyle) -> IconStyle) {
        val previous = current
        val next = transform(previous)
        if (next == previous) return
        current = next
        if (iconsMustRedraw(previous, next)) restyle()
    }

    private fun restyle() {
        _styleRevision.incrementAndGet()
        _revision.update { it + 1 }
    }
}

private const val STYLE_WAIT_MILLIS = 1_000L
