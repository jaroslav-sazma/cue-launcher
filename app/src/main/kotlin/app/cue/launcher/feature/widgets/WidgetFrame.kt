package app.cue.launcher.feature.widgets

import android.appwidget.AppWidgetHostView
import android.graphics.Rect
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.cue.launcher.designsystem.GlassLevel
import app.cue.launcher.designsystem.TileSurface
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.system.widget.WidgetBox
import app.cue.launcher.system.widget.WidgetSizeSpec
import app.cue.launcher.system.widget.bentoWidgetBox
import app.cue.launcher.system.widget.panelWidgetBox

@Composable
fun WidgetFrame(
    appWidgetId: Int,
    widgets: WidgetHosting,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    HostedWidgetFrame(
        appWidgetId = appWidgetId,
        widgets = widgets,
        radius = Tokens.Radius.tile4x2,
        modifier = modifier.fillMaxWidth().height(height),
        fillSlot = true,
        box = ::panelWidgetBox,
    )
}

@Composable
fun BentoWidgetFrame(
    appWidgetId: Int,
    widgets: WidgetHosting,
    radius: Dp,
    modifier: Modifier = Modifier,
) {
    HostedWidgetFrame(
        appWidgetId = appWidgetId,
        widgets = widgets,
        radius = radius,
        modifier = modifier,
        fillSlot = true,
        box = ::bentoWidgetBox,
    )
}

@Composable
fun StaticWidgetFrame(
    appWidgetId: Int,
    widgets: WidgetHosting,
    modifier: Modifier = Modifier,
) {
    HostedWidgetFrame(
        appWidgetId = appWidgetId,
        widgets = widgets,
        radius = null,
        modifier = modifier,
        fillSlot = true,
        box = ::bentoWidgetBox,
    )
}

@Composable
private fun HostedWidgetFrame(
    appWidgetId: Int,
    widgets: WidgetHosting,
    radius: Dp?,
    modifier: Modifier,
    fillSlot: Boolean,
    box: (WidgetSizeSpec, Int, Int) -> WidgetBox,
) {
    val context = LocalContext.current
    val hosted = remember(appWidgetId, context, widgets) {
        widgets.hostedView(context, appWidgetId)
    } ?: return
    val density = LocalDensity.current

    // onVisibilityChanged compares its callback by identity, and WidgetHosting is not a stable type,
    // so an inline lambda would be a new one on every recomposition and re-run the whole node.
    val onAppear = remember(widgets, appWidgetId) { { widgets.refresh(appWidgetId) } }

    WidgetHolder(radius = radius, modifier = modifier) {
        BoxWithConstraints(
            modifier = if (fillSlot) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val measured = box(hosted.size, constraints.maxWidth, constraints.maxHeight)
            val widthDp = with(density) { measured.widthPx.toDp() }
            val heightDp = with(density) { measured.heightPx.toDp() }
            Box(
                modifier = Modifier
                    .requiredSize(width = widthDp, height = heightDp)
                    .then(
                        if (radius == null) {
                            Modifier
                        } else {
                            Modifier.clip(RoundedCornerShape(widgetInnerRadius(radius)))
                        },
                    )

                    .widgetTouchArbiter(hosted.view),
            ) {
                AndroidView(
                    factory = { hosted.view.parked() },
                    update = { view -> view.reportSize(widthDp.value, heightDp.value) },
                    modifier = Modifier
                        .fillMaxSize()
                        .parkedWhileOffScreen(hosted.view, onAppear),
                )
            }
        }
    }
}

@Composable
private fun WidgetHolder(
    radius: Dp?,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (radius == null) {
        Box(modifier = modifier, content = content)
    } else {
        TileSurface(
            modifier = modifier,
            level = GlassLevel.REGULAR,
            radius = radius,
            contentPadding = Tokens.Padding.widgetHolder,
            content = content,
        )
    }
}

fun widgetInnerRadius(frameRadius: Dp): Dp =
    (frameRadius - Tokens.Padding.widgetHolder).coerceAtLeast(0.dp)

// Host views outlive the frame that shows them, so one can still be attached to the holder of a
// frame that has only just been disposed. It arrives parked, because the callback below only ever
// fires on a change and a view that starts off screen would otherwise never be told to stop.
private fun AppWidgetHostView.parked(): AppWidgetHostView = apply {
    (parent as? ViewGroup)?.removeView(this)
    visibility = View.INVISIBLE
}

// Both launcher pages stay composed so that a swipe never has to build one, which leaves a whole
// page of host views attached to a window that shows none of them. INVISIBLE is what stops a view
// drawing — View.invalidate() is dropped on anything but VISIBLE — so a widget that ticks or
// animates cannot drive frames for a page nobody is looking at. Layout and contents are untouched,
// so waking costs nothing and the view comes back on the last state it was given. The same signal
// is the one the platform wants for the widget's engagement metrics, and the moment to ask the
// provider for fresh content: a widget page is looked at for seconds at a time, and what it last
// pushed can be hours old. The swipe is what fires this, so the content lands while it animates.
private fun Modifier.parkedWhileOffScreen(
    view: AppWidgetHostView,
    onAppear: () -> Unit,
): Modifier = onVisibilityChanged(minFractionVisible = 0f) { onScreen ->
    view.visibility = if (onScreen) View.VISIBLE else View.INVISIBLE
    if (onScreen) {
        view.startVisibilityTracking()
        onAppear()
    } else {
        view.stopVisibilityTracking()
    }
}

private fun AppWidgetHostView.reportSize(widthDp: Float, heightDp: Float) {
    if (widthDp <= 0f || heightDp <= 0f) return
    val padding = AppWidgetHostView.getDefaultPaddingForWidget(context, appWidgetInfo?.provider, Rect())
    val density = resources.displayMetrics.density
    val extraWidthDp = (padding.left + padding.right) / density
    val extraHeightDp = (padding.top + padding.bottom) / density
    updateAppWidgetSize(
        Bundle(),
        listOf(SizeF(widthDp + extraWidthDp, heightDp + extraHeightDp)),
    )
}
