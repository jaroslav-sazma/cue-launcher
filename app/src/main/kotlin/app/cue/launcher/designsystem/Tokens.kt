package app.cue.launcher.designsystem

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Tokens {

    object Grid {
        const val COLUMNS = 4
        val columnWidth = 88.dp
        val gutter = 10.dp
        val contentInsetStart = 15.dp
        val contentInsetEnd = 15.dp

        object ZoneBleed {
            val start = 5.dp
            val end = 407.dp
            val padding = 9.dp

            val dock = start + padding
        }

        object RowHeight {
            val hero4x2 = 170.dp
            val tile2x2 = 168.dp
            val chip1x1 = 58.dp

            val static = 82.dp
        }

        val gapBentoStatic = 14.dp
    }

    object Home {

        const val WEIGHT_BENTO_GAP = 2

        const val WEIGHT_ROW_GAP = 1

        const val WEIGHT_DOCK_GAP = 3

        const val MIN_BENTO_SCALE = 0.75f
    }

    object Size {
        val icon = 58.dp
        val glyphInTile = 27.dp
        val glyphInIcon = 29.dp
        val searchbarWidth = 156.dp
        val searchbarHeight = 58.dp
        val drawerResultIcon = 44.dp
        val drawerRow = 56.dp
        val drawerInlineAction = 48.dp
        val drawerSearchField = 58.dp

        object PanelBar {
            val height = 107.dp
            val cornerRadius = 36.dp
        }
    }

    object Radius {
        val icon = 19.dp
        val tile2x2 = 28.dp
        val tile4x2 = 32.dp
        val widgetInner = 26.dp
        val zoneBento = 30.dp
        val zoneDock = 28.dp
        val overlayScrim = 30.dp
        val overlayPanel = 28.dp
        val searchbar = 29.dp
        val drawerResultIcon = 15.dp

        val drawerContactAvatar = Size.drawerResultIcon / 2
        val drawerInlineAction = 17.dp
        val drawerContentCorner = 36.dp
    }

    object Padding {
        val tile = 14.dp
        val chip = 6.dp
        val zone = 9.dp
        val widgetHolder = 6.dp
    }

    object Material {

        val thin = MaterialSpec(
            blurRadius = 20.dp,
            opacityLight = 0.04f,
            opacityDark = 0.03f,
            edgeLight = 0.40f,
            edgeDark = 0.10f,
        )

        val regular = MaterialSpec(
            blurRadius = 48.dp,
            opacityLight = 0.10f,
            opacityDark = 0.07f,
            edgeLight = 0.55f,
            edgeDark = 0.14f,
        )

        val thick = MaterialSpec(
            blurRadius = 64.dp,
            opacityLight = 0.16f,
            opacityDark = 0.11f,
            edgeLight = 0.68f,
            edgeDark = 0.18f,
        )

        const val TONE_LIGHT = 0xFFFFFFFF
        const val TONE_DARK = 0xFF1A1D1D

        object Opaque {
            const val TONE_LIGHT = 0xFFF8F5F0
            const val TONE_DARK = 0xFF1A1D1D
            const val LIGHTNESS_STEP_REGULAR = -0.05f
            const val LIGHTNESS_STEP_THICK = -0.10f
            const val EDGE_OPACITY = 0.06f
        }

        const val EDGE_COLOR = 0xFFFFFFFF
        val edgeWidth = 1.dp
    }

    object Elevation {
        val zone = ShadowSpec(offsetY = 4.dp, blurRadius = 18.dp, alpha = 0.14f)
        val tile = ShadowSpec(offsetY = 2.dp, blurRadius = 10.dp, alpha = 0.12f)
        val icon = ShadowSpec(offsetY = 1.dp, blurRadius = 6.dp, alpha = 0.20f)

        val drag = ShadowSpec(offsetY = 8.dp, blurRadius = 20.dp, alpha = 0.34f)

        val menu = ShadowSpec(offsetY = 12.dp, blurRadius = 40.dp, alpha = 0.30f)
    }

    object Type {
        val eyebrow = TypeSpec(size = 11.sp, weight = FontWeight.Medium, letterSpacing = 0.6.sp)
        val label = TypeSpec(size = 11.sp, weight = FontWeight.Normal)
        val tileTitleLarge = TypeSpec(size = 22.sp, weight = FontWeight.Medium, lineHeight = 25.3.sp)

        val tileTitle = TypeSpec(size = 19.sp, weight = FontWeight.Medium, lineHeight = 21.85f.sp)
        val tileBody = TypeSpec(size = 12.5f.sp, weight = FontWeight.Normal, lineHeight = 15.6f.sp)
        val badge = TypeSpec(size = 11.sp, weight = FontWeight.Bold, lineHeight = 14.sp)
        val stackPlus = TypeSpec(size = 9.sp, weight = FontWeight.Bold)
        val azIndex = TypeSpec(size = 9.5f.sp, weight = FontWeight.Medium)

        val labelGapBelowIcon = 6.dp

        val labelOnWallpaperShadow = ShadowSpec(offsetY = 0.5.dp, blurRadius = 1.dp, alpha = 1f)
        const val LABEL_LINES = 1
        val labelOverflow = TextOverflow.Ellipsis

        const val HIDE_LABELS_ABOVE_FONT_SCALE = 1.2f
    }

    object Template {

        val bodyGap = 2.dp

        // An action chip carries its label inside, under the glyph, so the chip keeps the size it
        // has everywhere else and the row of them costs the tile no extra height. The glyph is a
        // fixed size in the middle of it, so the inset is only what keeps the text off the edge.
        val labelInChipPadding = 4.dp

        // A size under the label the 1x1 chip carries under itself: the chip is only 50 dp wide
        // inside, and a caption that has to live there buys its width back from the type. It sits
        // straight under the glyph — the line box of the type is the whole gap there is.
        val labelInChip = TypeSpec(size = 9.sp, weight = FontWeight.Normal)

        val stackReserve = 26.dp

        const val TITLE_LINES = 2
        const val BODY_LINES = 2
    }

    object Progress {
        val height = 6.dp
        val radius = height / 2
        val gapAboveBar = 10.dp

        val markDiameter = height

        const val TRACK_OPACITY = 0.22f

        const val INDETERMINATE_OPACITY = 0.5f
    }

    object Reply {
        val height = 34.dp
        val radius = height / 2
        val gapAbovePill = 10.dp
        val glyph = 18.dp
        val gapAfterGlyph = 6.dp
    }

    object CustomWidget {

        object Clock {

            val halfGap = Grid.gutter

            val dial = 136.dp

            val dialInset = 11.dp

            const val DIAL_OPACITY_LIGHT = 0.78f
            const val DIAL_OPACITY_DARK = 0.08f

            val numeral = TypeSpec(
                size = 11.sp,
                weight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
                lineHeight = 11.sp,
            )

            // Distance of a numeral's centre from the centre of the dial, as a share of its diameter.
            const val NUMERAL_RADIUS_RATIO = 0.38f

            val tickWidth = 2.dp
            val tickLength = 7.dp
            val tickInset = 7.dp
            const val TICK_OPACITY_LIGHT = 0.38f
            const val TICK_OPACITY_DARK = 0.50f

            val hourHandWidth = 6.dp
            val hourHandLength = 40.dp
            val minuteHandWidth = 4.dp
            val minuteHandLength = 56.dp

            val cap = 10.dp
            val capEdge = 3.dp

            val weekday = TypeSpec(size = 16.sp, weight = FontWeight.Medium, lineHeight = 19.2f.sp)
            val weekdayGap = 2.dp
            val dayNumber = TypeSpec(
                size = 64.sp,
                weight = FontWeight.SemiBold,
                letterSpacing = (-1.5f).sp,
                lineHeight = 58.9f.sp,
            )

            // On the 0-100 scale Google's variable fonts give the 'ROND' axis; only a device font
            // that carries the axis at all reacts to it.
            const val DAY_NUMBER_ROUNDNESS = 100f
            val monthGap = 3.dp
            val month = TypeSpec(size = 15.sp, weight = FontWeight.Normal, lineHeight = 18.sp)

            val alarmGap = 10.dp
            val alarmIcon = 17.dp
            val alarmIconGap = 5.dp
            val alarm = TypeSpec(size = 13.sp, weight = FontWeight.Medium, lineHeight = 13.sp)
        }

        object Calendar {

            val paddingTop = 12.dp
            val paddingEnd = 16.dp
            val paddingBottom = 12.dp
            val paddingStart = Padding.tile

            val columnGap = 13.dp

            val monthColumnWidth = 146.dp
            val monthColumnTop = 2.dp

            val monthLabel = TypeSpec(
                size = 15.sp,
                weight = FontWeight.Medium,
                letterSpacing = 0.1.sp,
                lineHeight = 15.sp,
            )
            val monthLabelGap = 8.dp
            val monthLabelInset = 3.dp
            val weekday = TypeSpec(
                size = 9.5f.sp,
                weight = FontWeight.Medium,
                letterSpacing = 0.3.sp,
                lineHeight = 9.5f.sp,
            )
            val weekdayGap = 3.dp
            val dayNumber = TypeSpec(size = 10.5f.sp, weight = FontWeight.Medium, lineHeight = 10.5f.sp)
            val todayNumber = dayNumber.copy(weight = FontWeight.Bold)

            val today = 19.dp
            val todayShadowLight = ShadowSpec(offsetY = 1.dp, blurRadius = 4.dp, alpha = 0.14f)
            val todayShadowDark = todayShadowLight.copy(alpha = 0.30f)

            val divider = 1.dp

            val dividerInset = 4.dp

            const val DIVIDER_OPACITY_LIGHT = 0.10f
            const val DIVIDER_OPACITY_DARK = 0.14f

            val eventGap = 10.dp
            val eventDot = 8.dp
            val eventTitle = TypeSpec(
                size = 14.sp,
                weight = FontWeight.Medium,
                letterSpacing = 0.1.sp,
                lineHeight = 16.8f.sp,
            )
            val eventDetail = TypeSpec(
                size = 12.sp,
                weight = FontWeight.Normal,
                letterSpacing = 0.2.sp,
                lineHeight = 15.sp,
            )
            val eventStart = TypeSpec(size = 14.sp, weight = FontWeight.Medium, lineHeight = 16.8f.sp)
            val eventEnd = TypeSpec(size = 12.sp, weight = FontWeight.Normal, lineHeight = 15.sp)

            val lineGap = 1.dp

            val noticeInset = 2.dp
            val noticeGap = 4.dp
            val noticeTitle = TypeSpec(size = 16.sp, weight = FontWeight.Medium, lineHeight = 19.2f.sp)
            val noticeDetail = TypeSpec(size = 13.sp, weight = FontWeight.Normal, lineHeight = 16.9f.sp)

            const val NOTICE_LINES = 2

            const val SPECULAR_OPACITY_LIGHT = Searchbar.SPECULAR_OPACITY_LIGHT
            const val SPECULAR_OPACITY_DARK = Searchbar.SPECULAR_OPACITY_DARK
            const val SPECULAR_EXTENT = Searchbar.SPECULAR_EXTENT
        }

        object Media {

            val halfGap = Grid.gutter

            val metaInsetStart = 20.dp
            val metaInsetEnd = 4.dp

            val title = TypeSpec(
                size = 19.sp,
                weight = FontWeight.Medium,
                letterSpacing = 0.1.sp,
                lineHeight = 22.8f.sp,
            )
            const val TITLE_LINES = 2
            val artistGap = 4.dp
            val artist = TypeSpec(
                size = 14.sp,
                weight = FontWeight.Normal,
                letterSpacing = 0.2.sp,
                lineHeight = 18.2f.sp,
            )

            val controlGap = 6.dp
            val skip = 48.dp
            val skipGlyph = 30.dp
            val play = 64.dp
            val playGlyph = 34.dp

            val playRadiusPlaying = 22.dp
            val playRadiusPaused = play / 2

            val playShadowLight = ShadowSpec(offsetY = 2.dp, blurRadius = 8.dp, alpha = 0.14f)
            val playShadowDark = playShadowLight.copy(alpha = 0.30f)

            const val SKIP_PRESS_SCALE = 0.9f
            const val PLAY_PRESS_SCALE = 0.94f
            const val PRESS_OPACITY_LIGHT = 0.08f
            const val PRESS_OPACITY_DARK = 0.12f

            // How long a tapped play/pause shows its new state before trusting the player again: one
            // that ignores the command would otherwise leave the button showing the opposite.
            const val OPTIMISTIC_HOLD_MS = 1500L

            const val DISABLED_CONTENT_OPACITY = 0.38f
            const val DISABLED_CONTAINER_OPACITY = 0.12f

            const val SPECULAR_OPACITY_LIGHT = Searchbar.SPECULAR_OPACITY_LIGHT
            const val SPECULAR_OPACITY_DARK = Searchbar.SPECULAR_OPACITY_DARK
            const val SPECULAR_EXTENT = Searchbar.SPECULAR_EXTENT
        }
    }

    object Searchbar {
        val labelInset = 20.dp
        val gap = Drawer.actionGap
        val glyph = Drawer.glyph
        val label = TypeSpec(size = 15.sp, weight = FontWeight.Normal, letterSpacing = 0.15.sp)

        val disc = 42.dp

        // Concentric with the pill: the disc keeps the same inset above, below and at the end.
        val discInset = (Size.searchbarHeight - disc) / 2

        const val SPECULAR_OPACITY_LIGHT = 0.30f
        const val SPECULAR_OPACITY_DARK = 0.07f
        const val SPECULAR_EXTENT = 0.52f

        const val PRESS_SCALE = 0.965f
    }

    object WidgetSettings {

        val rowGap = 6.dp
        val rowRadius = 20.dp
        val rowPaddingHorizontal = 14.dp
        val rowPaddingVertical = 12.dp
        val rowMinHeight = 52.dp

        val label = TypeSpec(size = 15.sp, weight = FontWeight.Medium)
        val labelGap = 2.dp

        val glyph = 20.dp
        val glyphGap = 12.dp
    }

    object Folder {

        val fieldRadius = 7.dp
        val fieldGap = 4.dp
        val chipPadding = Padding.chip

        val glyphInField = 13.dp

        val panelWidth = 302.dp
        val panelPaddingTop = 16.dp
        val panelPaddingHorizontal = 20.dp
        val panelPaddingBottom = 20.dp

        val handleWidth = 32.dp
        val handleHeight = 4.dp
        val handleGap = 10.dp
        const val HANDLE_OPACITY = 0.30f

        val title = TypeSpec(size = 15.sp, weight = FontWeight.Medium)
        val titleGap = 14.dp

        const val COLUMNS = 4
        val gridRowGap = 14.dp
        val gridColumnGap = 10.dp

        val addGlyph = 24.dp

        val backgroundBlur = 32.dp

        const val ENTER_SCALE = 0.85f
    }

    object Indicator {

        const val ON_WALLPAPER_OPACITY = 0.85f

        object Stack {
            val pillWidth = 17.dp
            val dot = 5.dp
            val gap = 5.dp
            val activeDotWidth = 6.dp
            val activeDotHeight = 10.dp
            val edgeOffset = 12.dp

            const val TINT_OVER_WIDGET_LIGHT = 0.68f
            const val TINT_OVER_WIDGET_DARK = 0.58f

            val pillRadius = 8.dp
            val pillPadding = 6.dp
            val dotRadius = 3.dp
            val plusGap = 1.dp
            const val ACTIVE_OPACITY = 1f
            const val INACTIVE_OPACITY = 0.35f

            const val MAX_DOTS = 3

            val hintPaddingHorizontal = 10.dp
            val hintPaddingVertical = 4.dp
        }

        object Page {

            const val SCALE = 1.2f

            val activeWidth = 24.dp * SCALE
            val activeHeight = 5.dp * SCALE
            val inactive = 5.dp * SCALE
            val gap = 6.dp * SCALE

            val rowHeight = 18.dp * SCALE
            val radius = 3.dp * SCALE

            const val AUTO_HIDE_DELAY_MS = 1500L
            const val MORPH_GAIN = 2f
        }

        object ActionCue {
            val size = 5.dp * Page.SCALE
            val stroke = 1.4.dp * Page.SCALE
            val radius = 1.8.dp * Page.SCALE
        }
    }

    object Motion {

        const val HOME_EXIT_SCALE = 0.96f

        val wallpaperBlur = 20.dp

        const val WALLPAPER_BLUR_START = 0.65f

        const val BACK_PREVIEW_SCALE = 0.9f

        // How far a page has to be dragged before it commits. The pager's own default is half the
        // screen, and its velocity shortcut only opens above 400 dp/s — together that asks for
        // either a flick or a drag across most of the display, and a calm swipe is neither.
        const val PAGE_SNAP_THRESHOLD = 0.3f

        // How far a surface can travel past its end before it stops moving altogether. Glass does
        // not stretch, so scrolling past the end slides the pane instead of deforming it.
        val overscrollTravel = 56.dp

        // Share of the wallpaper's spare width spent by the swipe to the panel.
        const val WALLPAPER_PARALLAX_SWING = 0.2f

        // Home and the panel sit on two depth planes. A swipe pushes home back and pulls the panel
        // forward while the wallpaper counter-pans behind both, see WALLPAPER_PARALLAX_SWING.
        object Depth {

            const val HOME_SCALE = 0.86f
            const val HOME_ALPHA = 0.4f

            // Home goes out of focus as it leaves. This multiplies the blur under its glass zones,
            // not the widgets themselves, so it costs nothing and cannot upset an AppWidgetHostView.
            const val HOME_DEFOCUS = 2.6f

            // Depth reads as much from lag as from size: home sinks and holds back while the panel,
            // being the near plane, travels further than the pager alone would carry it.
            val homeSink = 26.dp

            const val PANEL_SCALE = 0.86f
            const val PANEL_ALPHA = 0.55f
            val panelLead = 64.dp

            // How much of the swipe is spent handing the cards over to one another: the last card
            // starts this far in, so the bento fills from the top instead of landing as one slab.
            const val CARD_STAGGER = 0.35f
            const val CARD_SCALE = 0.86f
            val cardRise = 28.dp

            // A card arrives from behind the backdrop: the page's own region lies right underneath
            // and is blurred far deeper, so a card that has not taken over its pane yet is read
            // through that. This is where in its arrival the card takes it over. Opacity and not
            // radius, because the radius is what costs blur passes — and at this depth the two are
            // indistinguishable.
            const val CARD_PANE_REVEAL = 0.75f

            // The light that rakes across a card as it settles: how wide the band is as a share of
            // the card, where it enters and leaves, and how bright it gets at the halfway point.
            const val RAKE_WIDTH = 0.55f
            const val RAKE_FROM = -0.4f
            const val RAKE_TO = 1.4f
            const val RAKE_PEAK = 0.34f
        }
    }

    object Drag {

        const val LIFT_SCALE = 1.06f
        const val LIFT_ROTATION = -2f

        // One travel for every directional swipe: shade, page action, stack paging, drawer.
        val swipeTravel = 48.dp

        val decorationSize = 68.dp

        val targetStroke = 2.dp

        val targetDash = 6.dp
        val targetGap = 4.dp

        // A slot only hinting at what it is for, so the slot a drag would really land in still reads
        // as the brighter one.
        const val HINT_OPACITY = 0.4f

        val liftDistance = 24.dp

        val pageEdgeZone = 24.dp

        const val PAGE_SWITCH_DELAY_MS = 500L

        val autoScrollEdge = 72.dp
        val autoScrollSpeed = 900.dp
    }

    object Drawer {

        val barPadding = 5.dp
        val barTopPadding = 8.dp

        val fieldPadding = 20.dp
        val fieldGap = 12.dp
        val glyph = 22.dp
        val clearGlyph = 20.dp

        val touchTarget = 48.dp

        val query = TypeSpec(size = 15.sp, weight = FontWeight.Normal)

        val listTopPadding = 8.dp
        val listBottomPadding = 18.dp

        val barOverlap = Radius.drawerContentCorner

        val rowPadding = 14.dp
        val rowGap = 14.dp
        val metaGap = 2.dp

        val sectionTopPadding = 12.dp
        val sectionBottomPadding = 6.dp

        val section = TypeSpec(size = 12.sp, weight = FontWeight.Medium, letterSpacing = 0.4.sp)
        val result = TypeSpec(size = 15.sp, weight = FontWeight.Medium)
        val resultMeta = TypeSpec(size = 11.5f.sp, weight = FontWeight.Normal)

        val initials = result

        val actionGap = 8.dp
        val actionGlyph = 22.dp

        val enterGlyph = 18.dp

        // The enter pill hugs the row's content with the same gap on all four sides, so the
        // horizontal one is whatever slack the row already leaves above and below the icon. Its
        // radius is concentric with the app icon inside it. These live here rather than in Radius
        // because Radius must not reach back into Drawer — Drawer.barOverlap already reads Radius,
        // and the cycle leaves whichever object initializes second holding a zero.
        val enterHintPadding = (Size.drawerRow - Size.drawerResultIcon) / 2
        val enterHintInset = rowPadding - enterHintPadding
        val enterHintRadius = Radius.drawerResultIcon + enterHintPadding

        // A thin glyph needs more air off the pill's edge than the solid icon tile does.
        val enterGlyphPadding = enterHintPadding

        object AzIndex {

            val width = 18.dp
            val verticalPadding = 10.dp

            val edgeOffset = 8.dp

            val listGutter = width + edgeOffset

            val gestureExclusionLimit = 200.dp
        }
    }

    object Panel {

        val surfaceCorner = Size.PanelBar.cornerRadius

        val widgetGap = 10.dp

        val contentBottomPadding = 10.dp

        // One grid row of a panel widget: the 4x2 hero split in half, so 2 rows are the hero height.
        val widgetRow = Grid.RowHeight.hero4x2 / 2

        object Resize {
            val pillWidth = 36.dp
            val pillHeight = Indicator.Page.activeHeight
            val pillRadius = Indicator.Page.radius

            val handleHeight = 26.dp

            // Slower than a reorder drag: here the scroll doubles as the growth of the widget.
            val autoScrollSpeed = 320.dp
        }

        object Bar {
            val horizontalPadding = 14.dp

            val bottomPadding = 34.dp
        }

        object Scrollbar {
            val width = 4.dp
            val radius = 2.dp

            val marginEnd = 2.dp
            val marginVertical = 8.dp

            val gutter = 14.dp

            const val TRACK_OPACITY = 0.14f
            const val THUMB_OPACITY = 0.50f

            val minThumbLength = 24.dp
        }
    }

    object WidgetPicker {

        val previewWidth = 132.dp
        val previewHeight = 88.dp

        val rowPadding = Drawer.rowPadding
        val rowGap = Drawer.rowGap
        val sheetPadding = Padding.tile
    }

    object Scenarios {
        val screenPadding = Padding.tile
        val sectionGap = Padding.tile
        val rowGap = Drawer.metaGap
        val rowPadding = Drawer.rowPadding

        val rowHeight = Size.drawerRow
        val rowItemGap = Drawer.rowGap

        val rowRadius = Radius.drawerResultIcon

        val badgeRadius = Badge.radius
        val badgePadding = Padding.chip

        val chipGap = Padding.chip

        val fabPadding = Padding.tile

        val listBottomPadding = Size.icon + Padding.tile

        const val DISABLED_ALPHA = 0.5f
    }

    object Builder {

        val rowRadius = Radius.drawerResultIcon
        val rowPadding = Padding.tile

        val backButtonWidth = 112.dp

        val cardBorder = 1.dp
        val selectedBorder = 2.dp

        val thumbWidth = 38.dp
        val thumbRadius = Padding.chip

        val sheetPadding = Padding.tile
        val sheetGap = Padding.tile

        val sheetFadeHeight = 64.dp

        val glyphCell = 72.dp
        val glyphCellIcon = Size.drawerResultIcon
        val glyphGridHeight = 240.dp

        val contentBottomPadding = Size.icon

        val pinSlotHeight = Size.drawerResultIcon
        val pinSlotGap = Padding.chip
        val pinSlotRadius = Radius.drawerResultIcon

        val suggestionKeyboardGap = Padding.chip
        val suggestionMinHeight = 112.dp

        const val BEHIND_ALPHA = 0.6f
    }

    object Settings {

        val screenPadding = 24.dp

        val surfacePadding = 16.dp

        val highlightPadding = 12.dp

        // The highlighted row sits 12 dp off the edge and carries 12 dp inside it, which puts its
        // text on the same 24 dp line as the section header above it.
        val rowMargin = 12.dp
        val rowContentPadding = 12.dp

        val highlightRadius = 20.dp

        val glyph = 24.dp
        val badgeGlyph = 14.dp

        val leadingBox = 40.dp
        val leadingRadius = 12.dp
        val leadingGlyph = 22.dp

        val iconPreview = 40.dp

        val swatch = 24.dp
        val swatchBorder = 1.dp

        // Space above a section label, which is what separates one section from the previous one.
        val sectionSpacing = 16.dp

        // Space between stacked blocks of text inside a section.
        val textSpacing = 6.dp

        val rowGap = 4.dp

        // Between cards stacked in a section, and between fields side by side inside one.
        val cardGap = 12.dp
        val fieldGap = 12.dp

        val choiceRowHeight = 56.dp
        val linkRowHeight = 52.dp

        val navBarReserve = 80.dp
    }

    object Badge {
        val minSize = 20.dp
        val radius = 10.dp
        val paddingHorizontal = 5.dp
        val overhang = 4.dp
        val dotSize = 10.dp
        val shadow = ShadowSpec(offsetY = 1.dp, blurRadius = 4.dp, alpha = 0.28f)
        val reach = overhang + shadow.blurRadius - shadow.offsetY
        const val OVERFLOW_THRESHOLD = 9
    }

    object Menu {

        val width = 272.dp
        val widthWithHeader = 292.dp
        val radius = 28.dp
        val padding = 6.dp

        val itemHeight = 52.dp
        val itemRadius = 20.dp
        val itemPaddingHorizontal = 18.dp
        val glyph = 22.dp
        val glyphGap = 14.dp
        val trailingGlyph = 20.dp
        const val TRAILING_OPACITY = 0.7f

        const val WRAP_LABELS_ABOVE_FONT_SCALE = 1.3f
        const val ITEM_LINES_WRAPPED = 2

        val headerIcon = Size.drawerResultIcon
        val headerGap = 12.dp
        val headerPaddingHorizontal = 12.dp
        val headerPaddingTop = 10.dp
        val headerPaddingBottom = 12.dp

        val ruleThickness = 1.dp
        val ruleInset = 12.dp
        val ruleGap = 6.dp
        const val RULE_OPACITY_LIGHT = 0.10f
        const val RULE_OPACITY_DARK = 0.12f

        const val PRESS_OPACITY_LIGHT = 0.05f
        const val PRESS_OPACITY_DARK = 0.08f

        const val DISABLED_OPACITY = Scenarios.DISABLED_ALPHA

        val sectionPaddingTop = 4.dp
        val sectionPaddingBottom = 6.dp

        val anchorGap = 16.dp
        val screenInset = 8.dp

        val backdropBlur = 32.dp

        val pressRipple = 64.dp
        const val RIPPLE_OPACITY_LIGHT = 0.34f
        const val RIPPLE_OPACITY_DARK = 0.16f
        const val RIPPLE_EDGE_OPACITY_LIGHT = 0.55f
        const val RIPPLE_EDGE_OPACITY_DARK = 0.18f

        const val ENTER_SCALE = 0.88f

        val header = TypeSpec(size = 16.sp, weight = FontWeight.Medium)
        val item = TypeSpec(size = 15.sp, weight = FontWeight.Normal)
    }
}

data class MaterialSpec(
    val blurRadius: Dp,
    val opacityLight: Float,
    val opacityDark: Float,
    val edgeLight: Float,
    val edgeDark: Float,
) {
    fun baseOpacity(isDark: Boolean): Float = if (isDark) opacityDark else opacityLight

    fun edgeOpacity(isDark: Boolean): Float = if (isDark) edgeDark else edgeLight
}

data class ShadowSpec(val offsetY: Dp, val blurRadius: Dp, val alpha: Float)

data class TypeSpec(
    val size: TextUnit,
    val weight: FontWeight,
    val letterSpacing: TextUnit = TextUnit.Unspecified,
    val lineHeight: TextUnit = TextUnit.Unspecified,
)
