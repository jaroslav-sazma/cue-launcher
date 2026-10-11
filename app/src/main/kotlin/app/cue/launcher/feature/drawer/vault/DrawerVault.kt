package app.cue.launcher.feature.drawer.vault

import android.graphics.Rect
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import app.cue.launcher.R
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.toTextStyle
import app.cue.launcher.feature.drawer.AppRow
import app.cue.launcher.feature.drawer.DrawerDrag
import app.cue.launcher.feature.drawer.DrawerSectionHeader
import app.cue.launcher.feature.drawer.listKey
import app.cue.launcher.system.InstalledApp
import app.cue.launcher.system.LauncherAppsRepository
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Rect as ComposeRect

@Composable
fun DrawerVault(
    vault: Vault,
    launcherApps: LauncherAppsRepository,
    drag: DrawerDrag,
    onLaunch: (ComponentRef, Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Tokens.Drawer.barPadding,

                end = Tokens.Drawer.barPadding + Tokens.Drawer.AzIndex.listGutter,
                top = Tokens.Drawer.listTopPadding,

                bottom = Tokens.Drawer.barOverlap,
            ),
        ) {
            vault.sections.forEach { section ->
                item(key = "letter:${section.letter}", contentType = "header") {
                    DrawerSectionHeader(text = section.letter)
                }
                items(items = section.apps, key = InstalledApp::listKey, contentType = { "app" }) { app ->
                    AppRow(
                        app = app,

                        meta = null,
                        launcherApps = launcherApps,
                        drag = drag,
                        onLaunch = onLaunch,
                    )
                }
            }
        }
        AzIndexRail(
            letters = vault.letters,
            modifier = Modifier.align(Alignment.CenterEnd),
            onJump = { section ->

                scope.launch { listState.scrollToItem(vault.headerItemIndex(section)) }
            },
        )
    }
}

@Composable
private fun AzIndexRail(
    letters: List<String>,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (letters.isEmpty()) return
    val colors = LocalCueColors.current
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    var bounds by remember { mutableStateOf(ComposeRect.Zero) }
    val exclusionLimitPx = with(LocalDensity.current) {
        Tokens.Drawer.AzIndex.gestureExclusionLimit.toPx()
    }

    DisposableEffect(view, bounds, exclusionLimitPx) {
        view.systemGestureExclusionRects = listOf(exclusionRect(bounds, exclusionLimitPx))

        onDispose { view.systemGestureExclusionRects = emptyList() }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(Tokens.Drawer.AzIndex.listGutter)
            .padding(vertical = Tokens.Drawer.AzIndex.verticalPadding)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .azScrub(letters.size, haptics, onJump),
    ) {
        Column(

            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(Tokens.Drawer.AzIndex.width),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEachIndexed { section, letter ->
                val jumpLabel = stringResource(R.string.drawer_az_jump, letter)
                Text(
                    text = letter,

                    modifier = Modifier.semantics {
                        contentDescription = jumpLabel
                        onClick(label = jumpLabel) {
                            onJump(section)
                            true
                        }
                    },
                    color = colors.textSecondary,
                    style = Tokens.Type.azIndex.toTextStyle(),
                )
            }
        }
    }
}

private fun Modifier.azScrub(
    count: Int,
    haptics: HapticFeedback,
    onJump: (Int) -> Unit,
): Modifier = pointerInput(count, onJump) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var current = NO_LETTER
        var pointer: PointerInputChange? = down
        while (pointer != null && pointer.pressed) {
            val next = azLetterAt(pointer.position.y, size.height.toFloat(), count)
            if (next != NO_LETTER && next != current) {
                current = next
                onJump(next)
                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
            pointer.consume()
            pointer = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
        }
    }
}

private fun exclusionRect(bounds: ComposeRect, limitPx: Float): Rect {
    val span = azExclusionSpan(bounds.height, limitPx)
    return Rect(
        bounds.left.roundToInt(),
        (bounds.top + span.start).roundToInt(),
        bounds.right.roundToInt(),
        (bounds.top + span.endInclusive).roundToInt(),
    )
}
