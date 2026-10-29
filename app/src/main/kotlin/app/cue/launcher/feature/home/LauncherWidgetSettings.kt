package app.cue.launcher.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.calendar.selectedCalendarIds
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.settings.LauncherSettings
import app.cue.launcher.designsystem.GLYPH_CHEVRON
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.toTextStyle
import app.cue.launcher.feature.lock.LauncherAction
import app.cue.launcher.feature.lock.LocalLayoutLocked
import app.cue.launcher.feature.scenarios.builder.ActionPickerSheet
import app.cue.launcher.feature.scenarios.builder.actionSummary
import app.cue.launcher.feature.scenarios.JitRequirement
import app.cue.launcher.feature.scenarios.builder.rememberRequirementResolver
import app.cue.launcher.system.InstalledApp
import app.cue.launcher.system.calendarGranted
import app.cue.launcher.system.notifications.notificationListenerEnabled
import kotlinx.coroutines.launch

fun launcherWidgetHasSettings(widget: LauncherWidgetType): Boolean = when (widget) {
    LauncherWidgetType.SEARCHBAR, LauncherWidgetType.CALENDAR, LauncherWidgetType.MEDIA -> true
    LauncherWidgetType.CLOCK -> false
}

@Composable
internal fun rememberLauncherWidgetConfigure(
    widget: LauncherWidgetType?,
    overlay: HomeOverlayState,
): (() -> Unit)? {
    if (widget == null ||
        !launcherWidgetHasSettings(widget) ||
        !LauncherAction.CONFIGURE_WIDGET.availableWhen(LocalLayoutLocked.current)
    ) {
        return null
    }
    return remember(widget, overlay) {
        { overlay.open(HomeOverlayTarget.WidgetSettings(widget)) }
    }
}

@Composable
internal fun LauncherWidgetSettings(
    widget: LauncherWidgetType,
    access: WidgetSettingsAccess,
    onOpenCalendars: () -> Unit,
    onOpenPlayers: () -> Unit,
) {
    val stored by access.settings.settings.collectAsStateWithLifecycle(initialValue = LauncherSettings())
    val bridgePaired = stored.bridge.enabled && stored.bridge.pairing != null

    Column(verticalArrangement = Arrangement.spacedBy(Tokens.WidgetSettings.rowGap)) {
        when (widget) {
            LauncherWidgetType.SEARCHBAR -> TapActionSetting(
                access = access,
                action = stored.gestures.searchbarTap,
                fallback = stringResource(R.string.widget_settings_searchbar_default),
                bridgePaired = bridgePaired,
                onPick = access.settings::setSearchbarTapAction,
            )

            LauncherWidgetType.CALENDAR -> {
                TapActionSetting(
                    access = access,
                    action = stored.gestures.calendarTap,
                    fallback = stringResource(R.string.widget_settings_calendar_default),
                    bridgePaired = bridgePaired,
                    onPick = access.settings::setCalendarTapAction,
                )
                CalendarsSetting(
                    access = access,
                    stored = stored.calendarWidget.calendars,
                    onOpen = onOpenCalendars,
                )
            }

            LauncherWidgetType.MEDIA -> {
                MediaPlayerSetting(packageName = stored.mediaWidget.app, onOpen = onOpenPlayers)
                NotificationAccessSetting()
            }

            LauncherWidgetType.CLOCK -> Unit
        }
    }
}

@Composable
private fun TapActionSetting(
    access: WidgetSettingsAccess,
    action: TapAction?,
    fallback: String,
    bridgePaired: Boolean,
    onPick: suspend (TapAction?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }

    WidgetSettingRow(
        label = stringResource(R.string.widget_settings_tap_action),
        detail = if (action == null) fallback else actionSummary(action),
        glyph = GLYPH_TAP_ACTION,
        onClick = { picking = true },
    )
    if (action != null) {
        WidgetSettingRow(
            label = stringResource(R.string.widget_settings_clear),
            detail = null,
            glyph = GLYPH_CLEAR,
            trailingGlyph = null,
            onClick = { scope.launch { onPick(null) } },
        )
    }
    if (picking) {
        ActionPickerSheet(
            launcherApps = access.launcherApps,
            appActions = access.appActions,
            initial = action,
            onPick = { picked -> scope.launch { onPick(picked) } },
            onDismiss = { picking = false },
            ha = access.ha,
            bridgePaired = bridgePaired,
        )
    }
}

@Composable
private fun CalendarsSetting(
    access: WidgetSettingsAccess,
    stored: Set<Long>?,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val calendars = rememberCalendarSources(granted = calendarGranted(context))
    ForgetGoneCalendars(calendars, stored, access)

    val picked = when {
        stored == null -> null
        calendars == null -> stored
        else -> selectedCalendarIds(calendars, stored)
    }
    WidgetSettingRow(
        label = stringResource(R.string.widget_settings_calendars),
        detail = when (picked) {
            null -> stringResource(R.string.widget_settings_calendars_default)
            else -> pluralStringResource(
                R.plurals.widget_settings_calendars_count,
                picked.size,
                picked.size,
            )
        },
        glyph = GLYPH_CALENDARS,
        onClick = onOpen,
    )
}

@Composable
private fun MediaPlayerSetting(packageName: String, onOpen: () -> Unit) {
    val feed = LocalMediaFeed.current
    val players by produceState<List<InstalledApp>?>(initialValue = null, feed) { value = feed.players() }
    val missing = stringResource(R.string.widget_settings_media_player_missing)
    WidgetSettingRow(
        label = stringResource(R.string.widget_settings_media_player),
        detail = players?.let { loaded ->
            loaded.firstOrNull { it.component.packageName == packageName }?.label ?: missing
        },
        glyph = GLYPH_PLAYER,
        onClick = onOpen,
    )
}

// The widget itself asks for the access on a tap, but it is easy to read as a broken player; here
// the reason sits next to the button.
@Composable
private fun NotificationAccessSetting() {
    val context = LocalContext.current
    val permissions = rememberRequirementResolver { }
    val granted = remember(permissions.tick, context) { notificationListenerEnabled(context) }
    if (granted) return
    val requirement = JitRequirement.NotificationListener
    WidgetSettingRow(
        label = stringResource(requirement.action),
        detail = stringResource(requirement.rationale),
        glyph = GLYPH_ACCESS,
        trailingGlyph = null,
        onClick = { permissions.resolve(requirement) },
    )
}

@Composable
internal fun WidgetSettingRow(
    label: String,
    detail: String?,
    glyph: String,
    onClick: () -> Unit,
    glyphTint: Color? = null,
    trailingGlyph: String? = GLYPH_CHEVRON,
) {
    val colors = LocalCueColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = colors.textPrimary.copy(
        alpha = if (colors.isDark) Tokens.Menu.PRESS_OPACITY_DARK else Tokens.Menu.PRESS_OPACITY_LIGHT,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.WidgetSettings.rowMinHeight)
            .clip(RoundedCornerShape(Tokens.WidgetSettings.rowRadius))
            .background(if (pressed) press else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(
                horizontal = Tokens.WidgetSettings.rowPaddingHorizontal,
                vertical = Tokens.WidgetSettings.rowPaddingVertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(
            name = glyph,
            size = Tokens.WidgetSettings.glyph,
            tint = glyphTint ?: colors.textPrimary,
        )
        Spacer(modifier = Modifier.width(Tokens.WidgetSettings.glyphGap))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = colors.textPrimary,
                style = Tokens.WidgetSettings.label.toTextStyle(),
                maxLines = Tokens.Type.LABEL_LINES,
                overflow = Tokens.Type.labelOverflow,
            )
            if (detail != null) {
                Spacer(modifier = Modifier.height(Tokens.WidgetSettings.labelGap))
                Text(
                    text = detail,
                    color = colors.textSecondary,
                    style = Tokens.Type.tileBody.toTextStyle(),
                    maxLines = Tokens.Menu.ITEM_LINES_WRAPPED,
                    overflow = Tokens.Type.labelOverflow,
                )
            }
        }
        if (trailingGlyph != null) {
            Spacer(modifier = Modifier.width(Tokens.WidgetSettings.glyphGap))
            GlyphIcon(
                name = trailingGlyph,
                size = Tokens.WidgetSettings.glyph,
                tint = colors.textSecondary.copy(alpha = Tokens.Menu.TRAILING_OPACITY),
            )
        }
    }
}

private const val GLYPH_TAP_ACTION = "gesture-tap"
private const val GLYPH_CALENDARS = "calendar-month-outline"
private const val GLYPH_PLAYER = "music-note"
private const val GLYPH_ACCESS = "bell-badge-outline"
private const val GLYPH_CLEAR = "restore"
