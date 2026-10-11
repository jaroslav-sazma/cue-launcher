package app.cue.launcher.feature.dragdrop

import android.content.ClipData
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropSourceModifierNode
import androidx.compose.ui.draganddrop.DragAndDropStartTransferScope
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.cue.launcher.designsystem.GlassLevel
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.lock.LocalLayoutLocked
import kotlin.math.roundToInt

@Composable
fun Modifier.shortcutDragSource(
    payload: DragPayload,
    icon: ImageBitmap?,
    label: String,

    onLockedLongPress: (() -> Unit)? = null,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val colors = LocalCueColors.current
    val lockedPress by rememberUpdatedState(onLockedLongPress)
    if (LocalLayoutLocked.current) {

        return when (onLockedLongPress) {
            null -> this
            else -> this.pointerInput(haptics) {
                detectLockedLongPress {
                    val press = lockedPress ?: return@detectLockedLongPress
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    press()
                }
            }
        }
    }
    return this then ShortcutDragSourceElement(
        payload = payload,
        icon = icon,
        label = label,
        haptics = haptics,
        iconSizePx = with(density) { Tokens.Size.icon.toPx() },
        decorationPx = with(density) { Tokens.Drag.decorationSize.toPx() },
        radiusPx = with(density) { Tokens.Radius.icon.toPx() },
        placeholder = colors.glass(GlassLevel.THICK),
    )
}

@Suppress("LongParameterList")
private data class ShortcutDragSourceElement(
    val payload: DragPayload,
    val icon: ImageBitmap?,
    val label: String,
    val haptics: HapticFeedback,
    val iconSizePx: Float,
    val decorationPx: Float,
    val radiusPx: Float,
    val placeholder: Color,
) : ModifierNodeElement<ShortcutDragSourceNode>() {

    override fun create() = ShortcutDragSourceNode(
        payload = payload,
        icon = icon,
        label = label,
        haptics = haptics,
        iconSizePx = iconSizePx,
        decorationPx = decorationPx,
        radiusPx = radiusPx,
        placeholder = placeholder,
    )

    override fun update(node: ShortcutDragSourceNode) {
        node.payload = payload
        node.icon = icon
        node.label = label
        node.haptics = haptics
        node.iconSizePx = iconSizePx
        node.decorationPx = decorationPx
        node.radiusPx = radiusPx
        node.placeholder = placeholder
    }
}

@Suppress("LongParameterList")
private class ShortcutDragSourceNode(
    var payload: DragPayload,
    var icon: ImageBitmap?,
    var label: String,
    var haptics: HapticFeedback,
    var iconSizePx: Float,
    var decorationPx: Float,
    var radiusPx: Float,
    var placeholder: Color,
) : DelegatingNode(), LayoutAwareModifierNode {

    private val source = delegate(
        DragAndDropSourceModifierNode { offset -> startTransfer(offset) },
    )

    init {
        delegate(
            SuspendingPointerInputModifierNode {
                detectLift { position -> source.requestDragAndDropTransfer(position) }
            },
        )
    }

    override fun onPlaced(coordinates: LayoutCoordinates) = source.onPlaced(coordinates)

    override fun onRemeasured(size: IntSize) = source.onRemeasured(size)

    private fun DragAndDropStartTransferScope.startTransfer(@Suppress("UNUSED_PARAMETER") offset: Offset) {
        val bitmap = icon
        val tone = placeholder
        val size = iconSizePx
        val radius = radiusPx
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val encoded = encodeDragPayload(payload)
        startDragAndDropTransfer(
            transferData = DragAndDropTransferData(
                clipData = ClipData(label, arrayOf(SHORTCUT_MIME_TYPE), ClipData.Item(encoded)),

                localState = encoded,
            ),
            decorationSize = Size(decorationPx, decorationPx),
            drawDragDecoration = {
                if (bitmap != null) drawLiftedIcon(bitmap, size) else drawLiftedTile(tone, size, radius)
            },
        )
    }
}

private suspend fun PointerInputScope.detectLift(onLift: (Offset) -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (awaitLongPress(down)) onLift(down.position)
    }
}

private suspend fun PointerInputScope.detectLockedLongPress(onLongPress: () -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!awaitLongPress(down)) return@awaitEachGesture
        onLongPress()
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

private fun DrawScope.drawLiftedTile(tone: Color, iconSizePx: Float, radiusPx: Float) {
    val lifted = iconSizePx * Tokens.Drag.LIFT_SCALE
    rotate(degrees = Tokens.Drag.LIFT_ROTATION) {
        drawRoundRect(
            color = tone,
            topLeft = Offset((size.width - lifted) / 2f, (size.height - lifted) / 2f),
            size = Size(lifted, lifted),
            cornerRadius = CornerRadius(radiusPx),
        )
    }
}

private fun DrawScope.drawLiftedIcon(icon: ImageBitmap, iconSizePx: Float) {
    val lifted = iconSizePx * Tokens.Drag.LIFT_SCALE
    rotate(degrees = Tokens.Drag.LIFT_ROTATION) {
        drawImage(
            image = icon,
            dstOffset = IntOffset(
                x = ((size.width - lifted) / 2f).roundToInt(),
                y = ((size.height - lifted) / 2f).roundToInt(),
            ),
            dstSize = IntSize(lifted.roundToInt(), lifted.roundToInt()),
        )
    }
}
