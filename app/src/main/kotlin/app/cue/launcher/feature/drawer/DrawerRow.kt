package app.cue.launcher.feature.drawer

import android.graphics.Rect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntRect
import app.cue.launcher.R
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.designsystem.AppIcon
import app.cue.launcher.designsystem.BadgedIcon
import app.cue.launcher.designsystem.CueMenu
import app.cue.launcher.designsystem.CueMenuHeader
import app.cue.launcher.designsystem.CueMenuItem
import app.cue.launcher.designsystem.CueMenuRule
import app.cue.launcher.designsystem.GlassLevel
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.MenuAnchor
import app.cue.launcher.designsystem.MenuDecoration
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.toTextStyle
import app.cue.launcher.designsystem.menuAnchorBounds
import app.cue.launcher.feature.dragdrop.DragPayload
import app.cue.launcher.feature.dragdrop.shortcutDragSource
import app.cue.launcher.feature.icons.IconOverrideMenuItem
import app.cue.launcher.feature.icons.notificationCountSemantics
import app.cue.launcher.feature.icons.rememberAppIcon
import app.cue.launcher.feature.icons.rememberNotificationCount
import app.cue.launcher.feature.lock.LauncherAction
import app.cue.launcher.feature.lock.LocalLayoutLocked
import app.cue.launcher.system.InstalledApp
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.system.toSourceBounds

@Composable
internal fun AppRow(
    app: InstalledApp,
    meta: String?,
    launcherApps: LauncherAppsRepository,
    drag: DrawerDrag,
    onLaunch: (ComponentRef, Rect?) -> Unit,
    modifier: Modifier = Modifier,
    enterTarget: Boolean = false,
) {
    val component = app.component
    val icon = rememberAppIcon(component, launcherApps)
    var iconBounds by remember(component) { mutableStateOf<Rect?>(null) }
    var anchorBounds by remember(component) { mutableStateOf(IntRect.Zero) }
    val addToHome = stringResource(R.string.drawer_add_to_home)
    val canAddToHome = LauncherAction.ADD_TO_HOME.availableWhen(LocalLayoutLocked.current)
    val notificationCount = rememberNotificationCount(component)

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Tokens.Size.drawerRow)
                .enterTargetSurface(enterTarget)
                .clickable(
                    onClickLabel = stringResource(R.string.drawer_result_launch),
                    onClick = { onLaunch(component, iconBounds) },
                )
                .shortcutDragSource(
                    payload = DragPayload.NewShortcut(component),
                    icon = icon,
                    label = app.label,

                    onLockedLongPress = { drag.menuFor = component },
                )
                .addToHomeAction(addToHome, enabled = canAddToHome) { drag.onAddToHome(component) }
                .notificationCountSemantics(notificationCount)
                .padding(horizontal = Tokens.Drawer.rowPadding),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Drawer.rowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BadgedIcon(count = notificationCount) {
                AppIcon(
                    icon = icon,
                    contentDescription = null,
                    modifier = Modifier.onGloballyPositioned {
                        iconBounds = it.boundsInWindow().toSourceBounds()
                        anchorBounds = it.menuAnchorBounds()
                    },
                    size = Tokens.Size.drawerResultIcon,
                )
            }
            DrawerRowText(label = app.label, meta = meta, modifier = Modifier.weight(1f))
            if (enterTarget) EnterHint()
        }
        AppRowMenu(
            drag = drag,
            component = component,
            label = addToHome,
            appLabel = app.label,
            icon = icon,
            bounds = anchorBounds,
            launcherApps = launcherApps,
        )
    }
}

// Drawn behind rather than as a background: the pill is narrower than the row, but the whole row
// stays tappable and its content keeps the same position as every other row's.
@Composable
internal fun Modifier.enterTargetSurface(enterTarget: Boolean): Modifier {
    if (!enterTarget) return this
    val highlight = LocalCueColors.current.glass(GlassLevel.REGULAR)
    val inset = Tokens.Drawer.enterHintInset
    val radius = Tokens.Drawer.enterHintRadius
    return drawBehind {
        val insetPx = inset.toPx()
        drawRoundRect(
            color = highlight,
            topLeft = Offset(insetPx, 0f),
            size = size.copy(width = size.width - insetPx * 2),
            cornerRadius = CornerRadius(radius.toPx()),
        )
    }
}

@Composable
internal fun EnterHint() {
    val colors = LocalCueColors.current
    GlyphIcon(
        name = GLYPH_ENTER,
        modifier = Modifier.padding(end = Tokens.Drawer.enterGlyphPadding),
        size = Tokens.Drawer.enterGlyph,
        tint = colors.accent,
        contentDescription = stringResource(R.string.drawer_enter_hint),
    )
}

private fun Modifier.addToHomeAction(label: String, enabled: Boolean, onAdd: () -> Unit): Modifier =
    semantics {
        customActions = if (!enabled) {
            emptyList()
        } else {
            listOf(CustomAccessibilityAction(label) { onAdd(); true })
        }
    }

@Composable
internal fun DrawerExtraRow(
    glyph: String,
    label: String,
    query: String,
    onClick: (Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCueColors.current
    var glyphBounds by remember { mutableStateOf<Rect?>(null) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Tokens.Size.drawerRow)
            .clickable(onClickLabel = label, onClick = { onClick(glyphBounds) })
            .padding(horizontal = Tokens.Drawer.rowPadding),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Drawer.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Tokens.Size.drawerResultIcon)
                .onGloballyPositioned { glyphBounds = it.boundsInWindow().toSourceBounds() },
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(name = glyph, size = Tokens.Drawer.glyph, tint = colors.accent)
        }
        DrawerRowText(label = label, meta = query)
    }
}

@Composable
internal fun DrawerRowText(label: String, meta: String?, modifier: Modifier = Modifier) {
    val colors = LocalCueColors.current
    Column(modifier = modifier) {
        Text(
            text = label,
            color = colors.textPrimary,
            style = Tokens.Drawer.result.toTextStyle(),
            maxLines = Tokens.Type.LABEL_LINES,
            overflow = Tokens.Type.labelOverflow,
        )
        if (meta != null) {
            Text(
                text = meta,
                modifier = Modifier.padding(top = Tokens.Drawer.metaGap),
                color = colors.textSecondary,
                style = Tokens.Drawer.resultMeta.toTextStyle(),
                maxLines = Tokens.Type.LABEL_LINES,
                overflow = Tokens.Type.labelOverflow,
            )
        }
    }
}

@Composable
private fun AppRowMenu(
    drag: DrawerDrag,
    component: ComponentRef,
    label: String,
    appLabel: String,
    icon: ImageBitmap?,
    bounds: IntRect,
    launcherApps: LauncherAppsRepository,
) {
    CueMenu(
        expanded = drag.menuFor == component,
        onDismissRequest = { drag.menuFor = null },
        anchor = MenuAnchor(
            bounds = bounds,
            width = Tokens.Menu.widthWithHeader,

            decoration = MenuDecoration.Lift {
                AppIcon(icon = icon, contentDescription = null, size = Tokens.Size.drawerResultIcon)
            },
        ),
    ) {
        CueMenuHeader(icon = icon, title = appLabel)
        CueMenuRule()
        if (LauncherAction.ADD_TO_HOME.availableWhen(LocalLayoutLocked.current)) {
            CueMenuItem(
                label = label,
                glyph = GLYPH_ADD_TO_HOME,
                onClick = {
                    drag.menuFor = null
                    drag.onAddToHome(component)
                },
            )
        }
        IconOverrideMenuItem(
            component = component,
            launcherApps = launcherApps,
            onDone = { drag.menuFor = null },
        )
        CueMenuItem(
            label = stringResource(R.string.drawer_hide_app),
            glyph = GLYPH_HIDE_APP,
            onClick = {
                drag.menuFor = null
                drag.onHide(component)
            },
        )
    }
}

private const val GLYPH_ADD_TO_HOME = "home-plus-outline"
private const val GLYPH_HIDE_APP = "eye-off-outline"
private const val GLYPH_ENTER = "keyboard-return"

internal val InstalledApp.listKey: String
    get() = with(component) { "$packageName/$className/$userSerial" }

@Composable
internal fun DrawerSectionHeader(text: String, modifier: Modifier = Modifier) {
    val colors = LocalCueColors.current
    Text(
        text = text,
        modifier = modifier.padding(
            start = Tokens.Drawer.rowPadding,
            end = Tokens.Drawer.rowPadding,
            top = Tokens.Drawer.sectionTopPadding,
            bottom = Tokens.Drawer.sectionBottomPadding,
        ),
        color = colors.accent,
        style = Tokens.Drawer.section.toTextStyle(),
    )
}
