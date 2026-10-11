package app.cue.launcher.system

import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.scenario.IconMode
import app.cue.launcher.designsystem.IconTint

data class IconStyle(
    val mode: IconMode = IconMode.NATIVE,
    val tint: IconTint? = null,
    val overrides: Set<ComponentRef> = emptySet(),
) {

    fun tints(component: ComponentRef): Boolean =
        mode == IconMode.TINTED && tint != null && component !in overrides
}

data class IconRequest(val mode: IconMode? = null, val tint: IconTint? = null) {

    fun styleOver(live: IconStyle): IconStyle = live.copy(
        mode = mode ?: live.mode,
        tint = tint ?: live.tint,
        overrides = if (mode == null) live.overrides else emptySet(),
    )
}

fun iconsMustRedraw(previous: IconStyle, next: IconStyle): Boolean =
    previous.mode != next.mode || next.mode == IconMode.TINTED

enum class TintedSource {

    MONOCHROME,

    FOREGROUND,

    LEGACY,
}

fun tintedSourceOf(hasMonochrome: Boolean, hasForeground: Boolean): TintedSource = when {
    hasMonochrome -> TintedSource.MONOCHROME
    hasForeground -> TintedSource.FOREGROUND
    else -> TintedSource.LEGACY
}
