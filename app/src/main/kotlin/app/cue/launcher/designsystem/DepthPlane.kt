package app.cue.launcher.designsystem

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf

// What a page's depth does to the wallpaper blur underneath it. Geometry is not part of it — the
// regions inherit that from the layout coordinates — only opacity and focus, which no layer
// transform can express. See BlurRegionDepth.
@Stable
class DepthPlane(val depth: () -> BlurRegionDepth) {

    companion object {
        val FLAT = DepthPlane { BlurRegionDepth.NONE }
    }
}

val LocalDepthPlane = staticCompositionLocalOf { DepthPlane.FLAT }
