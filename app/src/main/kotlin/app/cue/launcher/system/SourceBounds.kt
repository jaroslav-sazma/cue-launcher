package app.cue.launcher.system

import android.graphics.Rect
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Rect as ComposeRect

fun ComposeRect.toSourceBounds(): Rect = Rect(
    left.roundToInt(),
    top.roundToInt(),
    right.roundToInt(),
    bottom.roundToInt(),
)
