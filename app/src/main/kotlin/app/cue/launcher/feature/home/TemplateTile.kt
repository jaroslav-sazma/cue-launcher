package app.cue.launcher.feature.home

import android.graphics.Rect
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import app.cue.launcher.R
import app.cue.launcher.data.scenario.SemanticStyle
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TriggerPayload
import app.cue.launcher.designsystem.AppIcon
import app.cue.launcher.designsystem.GlassLevel
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.IconTint
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.LocalTintedIcons
import app.cue.launcher.designsystem.TileSurface
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.toTextStyle
import app.cue.launcher.designsystem.labelsVisibleAt
import app.cue.launcher.engine.haIconEntity
import app.cue.launcher.engine.progressTrack
import app.cue.launcher.feature.icons.rememberTintedPackageIcon
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.system.toSourceBounds

@Composable
fun TemplateTileContent(
    template: TileContent.Template,
    launcherApps: LauncherAppsRepository,
    actions: TileActions,
    modifier: Modifier = Modifier,
    payload: TriggerPayload = TriggerPayload.EMPTY,
    accented: Set<String> = emptySet(),
    onReply: (() -> Unit)? = null,
) {
    val colors = LocalCueColors.current
    val accent = colors.ink(payload.semantic, colors.accent)
    val track = remember(payload.progress) { progressTrack(payload.progress) }

    val progressBar: @Composable (Modifier) -> Unit = { barModifier ->
        track?.let { TileProgressBar(track = it, accent = accent, modifier = barModifier) }
    }

    val labelled = template.items.any { it.label != null } && iconLabelsFit()
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        when (template.layout) {

            TemplateLayout.FOUR_ICONS -> {
                val rows = template.items.chunked(ICONS_PER_ROW)
                rows.take(1).forEach { row ->
                    TemplateActionRow(row, launcherApps, actions, payload.semantic, accented, labelled)
                }
                progressBar(Modifier)
                rows.drop(1).forEach { row ->
                    TemplateActionRow(row, launcherApps, actions, payload.semantic, accented, labelled)
                }
            }

            TemplateLayout.TEXT_ONLY -> {
                TemplateTexts(template, progressBar)
                onReply?.let { reply ->
                    TileReplyPill(onClick = reply, modifier = Modifier.padding(top = Tokens.Reply.gapAbovePill))
                }
            }

            TemplateLayout.TEXT_TWO_ICONS -> {
                // The action row is measured first, so a long text loses its last line instead of
                // squeezing the chips and their labels.
                TemplateTexts(template, progressBar, modifier = Modifier.weight(1f, fill = false))
                TemplateActionRow(template.items, launcherApps, actions, payload.semantic, accented, labelled)
            }

            TemplateLayout.ICON_TEXT -> TemplateTexts(template, progressBar)
        }
    }
}

@Composable
private fun TemplateTexts(
    template: TileContent.Template,
    progressBar: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCueColors.current
    Column(modifier = modifier) {
        template.text?.let { title ->
            Text(
                text = title,
                color = colors.textPrimary,
                style = Tokens.Type.tileTitle.toTextStyle(),
                maxLines = Tokens.Template.TITLE_LINES,
                overflow = Tokens.Type.labelOverflow,
            )
        }
        template.text2?.let { body ->
            Text(
                text = body,
                color = colors.textSecondary,
                style = Tokens.Type.tileBody.toTextStyle(),
                maxLines = Tokens.Template.BODY_LINES,
                overflow = Tokens.Type.labelOverflow,

                modifier = Modifier.padding(
                    top = Tokens.Template.bodyGap,
                    end = Tokens.Template.stackReserve,
                ),
            )
        }
        progressBar(Modifier.padding(top = Tokens.Progress.gapAboveBar))
    }
}

@Composable
private fun TemplateActionRow(
    items: List<TemplateItem>,
    launcherApps: LauncherAppsRepository,
    actions: TileActions,
    semantic: SemanticStyle,
    accented: Set<String>,
    labelled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        items.forEach { item ->
            Box(
                modifier = Modifier.width(actionColumnWidth()),
                contentAlignment = Alignment.Center,
            ) {
                TemplateItemIcon(
                    item = item,
                    launcherApps = launcherApps,
                    actions = actions,
                    level = GlassLevel.THICK,

                    // An item without a label keeps nothing back for one: its glyph has the chip to
                    // itself and sits in the middle of it.
                    caption = item.label?.takeIf { labelled },
                    glyphTint = templateIconTint(item, semantic, accented),
                )
            }
        }
    }
}

// A chip stands on the bento's own column, the one the 1x1 chip below the tile stands on, so the
// icons of a bento read as a single grid however they are nested. The tile's padding eats into that
// column, and on a narrow display what is left can come out under the chip — there the row falls
// back to spanning the tile.
@Composable
private fun actionColumnWidth(): Dp {
    val windowWidth = LocalWindowInfo.current.containerSize.width
    val density = LocalDensity.current
    return remember(windowWidth, density) {
        with(density) { bentoColumnWidth(windowWidth.toDp()) - Tokens.Padding.tile * 2 }
            .coerceAtLeast(Tokens.Size.icon)
    }
}

// The caption takes the room the glyph leaves inside the chip, which the chip has at the size the
// tokens give it but not on a bento shrunk onto a short screen — nor at a font scale where a label
// stops fitting anywhere on the home screen.
@Composable
private fun iconLabelsFit(): Boolean = labelsVisibleAt(LocalDensity.current.fontScale) &&
    LocalHomeBands.current.mediumHeight >= Tokens.Grid.RowHeight.tile2x2

// Each icon answers for itself: the accent marks the one Home Assistant entity behind it that is
// on, and everything else keeps the colour the tile's notification asked for.
@Composable
internal fun templateIconTint(
    item: TemplateItem,
    semantic: SemanticStyle,
    accented: Set<String>,
): Color = with(LocalCueColors.current) {
    if (haIconEntity(item)?.let { it in accented } == true) accent else ink(semantic, textPrimary)
}

@Composable
fun TemplateItemIcon(
    item: TemplateItem,
    launcherApps: LauncherAppsRepository,
    actions: TileActions,
    level: GlassLevel,
    modifier: Modifier = Modifier,

    // The text under the glyph, inside the chip. Without one the glyph is centred in the chip.
    caption: String? = null,
    glyphTint: Color = LocalCueColors.current.textPrimary,
    contentDescription: String? = item.label,

    onLongClick: (() -> Unit)? = null,
) {
    val iconPackage = remember(item) { templateIconPackage(item) }
    val icon = rememberTintedPackageIcon(iconPackage, launcherApps, bentoIconTint(glyphTint))
    var bounds by remember(item) { mutableStateOf<Rect?>(null) }
    val clickable = modifier
        .size(Tokens.Size.icon)
        .clip(RoundedCornerShape(Tokens.Radius.icon))
        .combinedClickable(
            onClick = { actions.perform(item.actions, bounds) },
            onLongClick = onLongClick,
            onLongClickLabel = stringResource(R.string.tile_menu).takeIf { onLongClick != null },
        )
        .semantics { role = Role.Button }
        .onGloballyPositioned { bounds = it.boundsInWindow().toSourceBounds() }

    TileSurface(
        modifier = clickable,
        level = level,
        radius = Tokens.Radius.icon,
        contentPadding = if (caption == null) Tokens.Padding.chip else Tokens.Template.labelInChipPadding,
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TemplateGlyph(item, icon, glyphTint, contentDescription)
            caption?.let {
                Text(
                    text = it,
                    color = glyphTint,
                    style = Tokens.Template.labelInChip.toTextStyle(),
                    maxLines = Tokens.Type.LABEL_LINES,
                    overflow = Tokens.Type.labelOverflow,
                )
            }
        }
    }
}

// One tile design regardless of what the action leads to: an app icon only replaces the glyph inside
// the chip, it never replaces the chip itself, and it is tinted down to the glyph's own colour so a
// bento stays one palette whatever the appearance setting says.
@Composable
private fun TemplateGlyph(
    item: TemplateItem,
    icon: ImageBitmap?,
    glyphTint: Color,
    contentDescription: String?,
) {
    if (icon != null) {
        CompositionLocalProvider(LocalTintedIcons provides true) {
            AppIcon(icon = icon, contentDescription = contentDescription, size = Tokens.Size.glyphInTile)
        }
        return
    }
    GlyphIcon(
        name = templateGlyphName(item),
        size = Tokens.Size.glyphInTile,
        tint = glyphTint,
        contentDescription = contentDescription,
    )
}

// The chip is the surface, so the icon brings no plate of its own — only the shape, in the colour
// the glyph beside it would have had.
@Composable
internal fun bentoIconTint(foreground: Color): IconTint =
    remember(foreground) { IconTint(background = Color.Transparent.toArgb(), foreground = foreground.toArgb()) }

private const val ICONS_PER_ROW = 2
