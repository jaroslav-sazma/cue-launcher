package app.cue.launcher.feature.widgets

import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import app.cue.launcher.system.widget.WidgetPreview

@Composable
fun WidgetPreviewBox(
    preview: WidgetPreview?,
    widthPx: Int,
    heightPx: Int,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        if (preview == null) return@BoxWithConstraints
        val scale = previewScale(
            contentWidth = widthPx.toFloat(),
            contentHeight = heightPx.toFloat(),
            boxWidth = constraints.maxWidth.toFloat(),
            boxHeight = constraints.maxHeight.toFloat(),
        )
        val density = LocalDensity.current
        AndroidView(
            factory = ::FrameLayout,
            update = { container ->
                container.removeAllViews()
                previewView(container, preview)?.let(container::addView)
            },
            modifier = Modifier
                .requiredSize(
                    width = with(density) { widthPx.toDp() },
                    height = with(density) { heightPx.toDp() },
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

fun previewScale(
    contentWidth: Float,
    contentHeight: Float,
    boxWidth: Float,
    boxHeight: Float,
): Float {
    val invalid = contentWidth <= 0f || contentHeight <= 0f || boxWidth <= 0f || boxHeight <= 0f
    if (invalid) return 1f
    return minOf(1f, boxWidth / contentWidth, boxHeight / contentHeight)
}

private fun previewView(container: FrameLayout, preview: WidgetPreview): View? {
    val views = preview.remoteViews?.let { remoteViews ->
        runCatching { remoteViews.apply(container.context, container) }
            .onFailure { failure -> Log.w(TAG, "The widget preview cannot be rendered", failure) }
            .getOrNull()
    }
    return views ?: preview.image?.let { drawable ->
        ImageView(container.context).apply {
            setImageDrawable(drawable)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
    }
}

private const val TAG = "WidgetHost"
