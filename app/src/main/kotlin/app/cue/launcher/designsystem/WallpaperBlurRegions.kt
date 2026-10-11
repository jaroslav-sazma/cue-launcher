package app.cue.launcher.designsystem

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RectF
import android.view.RoundedRectBlurRegion
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.round

@Stable
class WallpaperBlurRegionsState internal constructor() {

    internal val regions = mutableStateMapOf<String, WallpaperBlurRegion>()
}

data class WallpaperBlurRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val cornerRadius: Float,
    val blurRadius: Float,

    val alpha: Float = 1f,
    val zIndex: Float = 0f,
)

fun Collection<WallpaperBlurRegion>.sortedForDraw(): List<WallpaperBlurRegion> =
    sortedBy { it.zIndex }

// Geometry is deliberately absent. A region is placed from boundsInWindow(), and those coordinates
// already carry every graphics layer transform above the element — scale, translation and all — so
// mirroring the page's or the card's transform here would apply it a second time and slide the blur
// out from under the colour it belongs to. Only what a layer transform cannot express belongs here.
@Stable
data class BlurRegionDepth(val alpha: Float = 1f, val blurScale: Float = 1f) {

    companion object {
        val NONE = BlurRegionDepth()
    }
}

fun WallpaperBlurRegion.withDepth(depth: BlurRegionDepth): WallpaperBlurRegion {
    if (depth == BlurRegionDepth.NONE) return this
    return copy(alpha = alpha * depth.alpha, blurRadius = blurRadius * depth.blurScale)
}

// A blur region costs a pass over its area whether or not anything of it ends up on screen, and a
// radius that wobbles by a fraction of a pixel between frames buys a rebuild for a picture nobody
// can tell apart. Null means the region is not worth handing to the surface at all.
fun WallpaperBlurRegion.worthDrawing(): WallpaperBlurRegion? {
    val drawn = alpha >= MIN_VISIBLE_ALPHA && blurRadius >= 1f && right > left && bottom > top
    if (!drawn) return null
    return copy(alpha = round(alpha * ALPHA_STEPS) / ALPHA_STEPS, blurRadius = round(blurRadius))
}

private const val MIN_VISIBLE_ALPHA = 1f / 255f
private const val ALPHA_STEPS = 255f

val LocalWallpaperBlurRegions = staticCompositionLocalOf<WallpaperBlurRegionsState?> { null }

@Composable
fun WallpaperBlurRegionsLayer(content: @Composable () -> Unit) {
    val state = remember { WallpaperBlurRegionsState() }
    WallpaperBlurRegionsBackdrop(state = state, modifier = Modifier.fillMaxSize())
    CompositionLocalProvider(LocalWallpaperBlurRegions provides state, content = content)
}

@Composable
fun Modifier.wallpaperBlurRegion(
    key: String,
    cornerRadius: Dp,
    blurRadius: Dp = Tokens.Material.thin.blurRadius,
    zIndex: Float = 0f,
    depth: () -> BlurRegionDepth = LocalDepthPlane.current.depth,
    // Applied before `depth`: the card's own arrival, inside the page that is itself receding.
    localDepth: () -> BlurRegionDepth = { BlurRegionDepth.NONE },
): Modifier {

    val state = LocalWallpaperBlurRegions.current
    if (state == null) return this
    DisposableEffect(state, key) {
        onDispose { state.regions.remove(key) }
    }
    val bounds = remember { mutableStateOf<Rect?>(null) }
    val cornerPx = rememberUpdatedState(with(LocalDensity.current) { cornerRadius.toPx() })
    val blurPx = rememberUpdatedState(with(LocalDensity.current) { blurRadius.toPx() })
    val currentDepth = rememberUpdatedState(depth)
    val currentLocalDepth = rememberUpdatedState(localDepth)
    LaunchedEffect(state, key) {
        snapshotFlow {
            bounds.value?.let { box ->
                WallpaperBlurRegion(
                    left = box.left,
                    top = box.top,
                    right = box.right,
                    bottom = box.bottom,
                    cornerRadius = cornerPx.value,
                    blurRadius = blurPx.value,
                    zIndex = zIndex,
                ).withDepth(currentLocalDepth.value.invoke())
                    .withDepth(currentDepth.value.invoke())
                    .worthDrawing()
            }
        }.collect { region ->
            if (region == null) state.regions.remove(key) else state.regions[key] = region
        }
    }
    return onGloballyPositioned { coordinates -> bounds.value = coordinates.boundsInWindow() }
}

@Composable
private fun WallpaperBlurRegionsBackdrop(state: WallpaperBlurRegionsState, modifier: Modifier = Modifier) {
    val available = !LocalCueColors.current.reducedTransparency && rememberCrossWindowBlurEnabled()
    var view by remember { mutableStateOf<SurfaceView?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { context -> transparentBlurSurface(context).also { view = it } },
    )
    LaunchedEffect(view, available) {
        val surface = view ?: return@LaunchedEffect
        if (!available) {
            surface.setBlurRegions(null)
            return@LaunchedEffect
        }

        // Every region writes into the map from a collector of its own, so a single frame of a swipe
        // arrives here as a burst. Each push is a surface transaction, so the burst is held until
        // the frame and only the state it ends on is handed over.
        var pushed: List<WallpaperBlurRegion>? = null
        snapshotFlow { state.regions.values.sortedForDraw() }.collect {
            withFrameNanos { }
            val regions = state.regions.values.sortedForDraw()
            if (regions == pushed) return@collect
            pushed = regions
            surface.setBlurRegions(regions.map { region -> region.toPlatformRegion() })
        }
    }
}

fun blurRegionCornerRadii(cornerRadius: Float): FloatArray = FloatArray(CORNER_RADII_SIZE) { cornerRadius }

private const val CORNER_RADII_SIZE = 8

private fun WallpaperBlurRegion.toPlatformRegion(): RoundedRectBlurRegion = RoundedRectBlurRegion(
    RectF(left, top, right, bottom),
    blurRegionCornerRadii(cornerRadius),
    alpha,
    blurRadius,
)

private fun transparentBlurSurface(context: Context): SurfaceView = SurfaceView(context).apply {
    holder.setFormat(PixelFormat.TRANSLUCENT)
    holder.addCallback(object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) = Unit
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            postTransparentFrame(holder)
        }
        override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
    })
}

private fun postTransparentFrame(holder: SurfaceHolder) {
    val canvas = holder.lockHardwareCanvas() ?: return
    try {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
    } finally {
        holder.unlockCanvasAndPost(canvas)
    }
}
