package app.cue.launcher.system

import android.app.Application
import android.content.res.Resources
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.scenario.IconMode
import app.cue.launcher.designsystem.IconTint
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.system.iconpack.IconPackRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@SingleIn(AppScope::class)
@Inject
class AppIconLoader(
    private val application: Application,
    private val iconPacks: IconPackRepository,

    val appearance: IconAppearance,
) {

    private val density = application.resources.displayMetrics.density

    val sizePx: Int = (Tokens.Size.icon.value * density).roundToInt()

    val densityDpi: Int = application.resources.displayMetrics.densityDpi

    private val radiusPx: Float = Tokens.Radius.icon.value * density

    private val edgeWidthPx: Float = Tokens.Material.edgeWidth.value * density

    private val cache = IconCache<ImageBitmap>(CACHE_SIZE)

    // Icons asked for in a style of their own — a tinted one on a bento chip, a preview in the
    // appearance settings — cannot share the cache above, which holds one bitmap per component in
    // the live style. They get their own, one slot per component: only one style of them is on
    // screen at a time, and a slot per style would grow with every colour the wallpaper ever had.
    private val variants = IconCache<StyledIcon>(VARIANT_CACHE_SIZE)

    private var rendered = appearance.styleRevision

    val revision: StateFlow<Int> = appearance.revision

    suspend fun icon(
        component: ComponentRef,
        load: () -> Drawable?,
        badge: ((Drawable) -> Drawable)? = null,
    ): ImageBitmap? {
        appearance.awaitStyle()
        val snapshot = syncStyle()
        val cached = cache.get(component)
        if (cached != null) return cached
        val bitmap = withContext(Dispatchers.Default) {
            sourceFor(component, snapshot.style, load)?.let { render(it, badge) }
        }
        return bitmap?.also { icon -> cacheIfCurrent(snapshot.revision) { cache.put(component, icon) } }
    }

    suspend fun render(
        component: ComponentRef,
        request: IconRequest,
        load: () -> Drawable?,
        badge: ((Drawable) -> Drawable)? = null,
    ): ImageBitmap? {
        appearance.awaitStyle()
        val snapshot = syncStyle()
        variants.get(component)?.takeIf { it.request == request }?.let { return it.bitmap }
        val bitmap = withContext(Dispatchers.Default) {
            sourceFor(component, request.styleOver(snapshot.style), load)?.let { render(it, badge) }
        }
        return bitmap?.also { icon ->
            cacheIfCurrent(snapshot.revision) { variants.put(component, StyledIcon(request, icon)) }
        }
    }

    fun cachedIcon(component: ComponentRef): ImageBitmap? {
        syncStyle()
        return cache.get(component)
    }

    // Dropped and re-asked for under the lock cacheIfCurrent takes, so a render that began before the
    // update cannot slip its old bitmap into the cache between the two. A change to the active icon
    // pack itself redraws everything anyway.
    @Synchronized
    fun refreshPackages(packageNames: Collection<String>, userSerial: Long) {
        packageNames.forEach { packageName ->
            if (iconPacks.invalidatePackage(packageName)) {
                clear()
            } else {
                cache.invalidatePackage(packageName, userSerial)
                variants.invalidatePackage(packageName, userSerial)
            }
        }
        appearance.redrawIcons()
    }

    fun clear() {
        cache.clear()
        variants.clear()
    }

    @Synchronized
    private fun syncStyle(): Snapshot {
        val style = appearance.styleRevision
        if (style != rendered) {
            rendered = style
            clear()
        }
        return Snapshot(appearance.revision.value, appearance.style())
    }

    // A style change or an app update between starting the render and finishing it makes the result
    // already stale, and storing it would keep the old icon alive past the switch.
    @Synchronized
    private fun cacheIfCurrent(revision: Int, store: () -> Unit) {
        if (revision == appearance.revision.value) store()
    }

    private class Snapshot(val revision: Int, val style: IconStyle)

    private class StyledIcon(val request: IconRequest, val bitmap: ImageBitmap)

    private fun sourceFor(
        component: ComponentRef,
        style: IconStyle,
        load: () -> Drawable?,
    ): IconSource? = if (style.tints(component)) {
        load()?.let { tinted(it, checkNotNull(style.tint)) }
    } else {

        val fromPack = if (style.mode == IconMode.ICONPACK) iconPacks.icon(component, densityDpi) else null
        val drawable = fromPack ?: load() ?: return null
        val geometry = when {
            drawable is AdaptiveIconDrawable -> IconGeometry.ADAPTIVE

            fromPack != null -> IconGeometry.FULL_BLEED
            else -> IconGeometry.LEGACY
        }
        IconSource(drawable, geometry)
    }

    private fun tinted(drawable: Drawable, tint: IconTint): IconSource {
        val adaptive = drawable as? AdaptiveIconDrawable
        val monochrome = adaptive?.monochrome
        val source = tintedSourceOf(
            hasMonochrome = monochrome != null,
            hasForeground = adaptive?.foreground != null,
        )
        return if (source == TintedSource.MONOCHROME) {
            IconSource(
                drawable = checkNotNull(monochrome).mutate().apply { setTint(tint.foreground) },

                geometry = IconGeometry.ADAPTIVE_LAYER,
                background = tint.background,
                edge = tint.edge,
            )
        } else {
            IconSource(
                drawable = generatedMonochrome(drawable, tint, sizePx, application.resources),

                geometry = IconGeometry.FULL_BLEED,
                background = tint.background,
                edge = tint.edge,
            )
        }
    }

    private fun render(source: IconSource, badge: ((Drawable) -> Drawable)?): ImageBitmap {
        val drawn = createBitmap(sizePx, sizePx)
        val canvas = Canvas(drawn)

        source.background?.let(canvas::drawColor)
        when (source.geometry) {
            IconGeometry.ADAPTIVE ->
                (source.drawable as AdaptiveIconDrawable).drawLayers(canvas, sizePx)

            IconGeometry.ADAPTIVE_LAYER ->
                source.drawable.drawInto(canvas, adaptiveIconBounds(sizePx, EXTRA_INSET_FRACTION))

            IconGeometry.LEGACY -> {
                canvas.drawColor(LEGACY_PLATE_COLOR)
                val bounds = legacyIconBounds(
                    sizePx = sizePx,
                    intrinsicWidth = source.drawable.intrinsicWidth,
                    intrinsicHeight = source.drawable.intrinsicHeight,
                )
                source.drawable.drawInto(canvas, bounds)
            }

            IconGeometry.FULL_BLEED -> source.drawable.drawInto(canvas, fullBleedIconBounds(sizePx))
        }

        val masked = createBitmap(sizePx, sizePx)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(drawn, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val maskCanvas = Canvas(masked)
        maskCanvas.drawRoundRect(
            RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()),
            radiusPx,
            radiusPx,
            paint,
        )
        if (Color.alpha(source.edge) != 0) {
            maskCanvas.drawEdge(source.edge, sizePx, radiusPx, edgeWidthPx)
        }
        drawn.recycle()
        if (badge == null) return masked.asImageBitmap()

        val badged = createBitmap(sizePx, sizePx)
        val badgedDrawable = badge(masked.toDrawable(application.resources))
        badgedDrawable.setBounds(0, 0, sizePx, sizePx)
        badgedDrawable.draw(Canvas(badged))
        masked.recycle()
        return badged.asImageBitmap()
    }

    private class IconSource(
        val drawable: Drawable,
        val geometry: IconGeometry,
        val background: Int? = null,
        val edge: Int = Color.TRANSPARENT,
    )

    private enum class IconGeometry {

        ADAPTIVE,

        ADAPTIVE_LAYER,

        LEGACY,

        FULL_BLEED,
    }

    private companion object {

        const val CACHE_SIZE = 64

        // A bento holds at most eight icons, so the variants never need the room the live style
        // does.
        const val VARIANT_CACHE_SIZE = 16

        val EXTRA_INSET_FRACTION: Float = AdaptiveIconDrawable.getExtraInsetFraction()

        const val LEGACY_PLATE_COLOR: Int = Color.WHITE

    }
}

private fun AdaptiveIconDrawable.drawLayers(canvas: Canvas, sizePx: Int) {
    setBounds(0, 0, sizePx, sizePx)
    background?.draw(canvas)
    foreground?.draw(canvas)
}

// The same hairline a glass pane carries, drawn inside the shape the way Compose's border is.
private fun Canvas.drawEdge(color: Int, sizePx: Int, radiusPx: Float, widthPx: Float) {
    val inset = widthPx / 2f
    drawRoundRect(
        RectF(inset, inset, sizePx - inset, sizePx - inset),
        radiusPx - inset,
        radiusPx - inset,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = widthPx
            this.color = color
        },
    )
}

private fun Drawable.drawInto(canvas: Canvas, bounds: IconBounds) {
    setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
    draw(canvas)
}

private fun generatedMonochrome(
    drawable: Drawable,
    tint: IconTint,
    sizePx: Int,
    resources: Resources,
): Drawable {
    val flat = createBitmap(sizePx, sizePx)
    val canvas = Canvas(flat)
    canvas.drawColor(Color.BLACK)
    if (drawable is AdaptiveIconDrawable) {
        drawable.drawLayers(canvas, sizePx)
    } else {
        drawable.drawInto(canvas, fullBleedIconBounds(sizePx))
    }

    val pixels = IntArray(sizePx * sizePx)
    flat.getPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    flat.recycle()
    val luminance = IntArray(pixels.size) { index ->
        val pixel = pixels[index]
        val red = pixel shr RED_SHIFT and CHANNEL_MASK
        val green = pixel shr GREEN_SHIFT and CHANNEL_MASK
        val blue = pixel and CHANNEL_MASK
        (red + green + blue) / RGB_CHANNELS
    }

    val alpha = monochromeAlpha(luminance, edgeSampleCount = sizePx * (sizePx / EDGE_ROW_RATIO))
    val rgb = tint.foreground and RGB_MASK
    for (index in pixels.indices) pixels[index] = (alpha[index] shl ALPHA_SHIFT) or rgb
    val mask = createBitmap(sizePx, sizePx)
    mask.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    return mask.toDrawable(resources)
}

private const val EDGE_ROW_RATIO = 16

private const val RGB_CHANNELS = 3
private const val CHANNEL_MASK = 0xFF
private const val RGB_MASK = 0x00FFFFFF
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
