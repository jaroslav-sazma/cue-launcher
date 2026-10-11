package app.cue.launcher.feature.home

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cue.launcher.R
import app.cue.launcher.data.calendar.CALENDAR_GRID_COLUMNS
import app.cue.launcher.data.calendar.CALENDAR_GRID_EMPTY
import app.cue.launcher.data.calendar.CALENDAR_GRID_ROWS
import app.cue.launcher.data.calendar.CALENDAR_TILE_EVENTS
import app.cue.launcher.data.calendar.CalendarAccess
import app.cue.launcher.data.calendar.CalendarEvent
import app.cue.launcher.data.calendar.CalendarRead
import app.cue.launcher.data.calendar.calendarEventLines
import app.cue.launcher.data.calendar.calendarFirstDayOfWeek
import app.cue.launcher.data.calendar.calendarMonthGrid
import app.cue.launcher.data.calendar.calendarMonthLabel
import app.cue.launcher.data.calendar.calendarWeekdayLabels
import app.cue.launcher.data.calendar.upcomingCalendarEvents
import app.cue.launcher.designsystem.GlassLevel
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.TileSurface
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.cueShadow
import app.cue.launcher.designsystem.specularSheen
import app.cue.launcher.designsystem.toTextStyle
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.time.ZonedDateTime

private typealias Cal = Tokens.CustomWidget.Calendar

@Composable
internal fun CalendarWidget(modifier: Modifier = Modifier) {
    val feed = LocalCalendarFeed.current
    // RESUMED rather than STARTED: the permission dialog the notice opens only pauses the launcher,
    // and a denied read has already completed, so the grant would otherwise go unseen.
    val read by remember(feed) { feed.events() }
        .collectAsStateWithLifecycle(initialValue = null, minActiveState = Lifecycle.State.RESUMED)
    val now = rememberMinuteTick()
    val events = remember(read, now) {
        upcomingCalendarEvents(
            events = read?.events.orEmpty(),
            nowMillis = now.toInstant().toEpochMilli(),
            zone = now.zone,
            limit = CALENDAR_TILE_EVENTS,
        )
    }
    val colors = LocalCueColors.current
    val fallback = colors.accent
    val shown = remember(events, fallback) {
        events.map { event -> CalendarDot(event, calendarDotColor(event.calendarColor, fallback)) }
    }
    val sheen = remember(colors) {
        specularSheen(
            colors = colors,
            opacityLight = Cal.SPECULAR_OPACITY_LIGHT,
            opacityDark = Cal.SPECULAR_OPACITY_DARK,
            extent = Cal.SPECULAR_EXTENT,
        )
    }
    // Clipped ahead of the caller's modifier: a click it carries draws its ripple there, and the
    // ripple has to keep to the corners of the glass.
    TileSurface(
        modifier = Modifier.clip(WIDGET_SHAPE).then(modifier).fillMaxSize(),
        level = GlassLevel.THICK,
        shape = WIDGET_SHAPE,
        contentPadding = 0.dp,
        shadow = null,
        sheen = sheen,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(
                start = Cal.paddingStart,
                top = Cal.paddingTop,
                end = Cal.paddingEnd,
                bottom = Cal.paddingBottom,
            ),
            horizontalArrangement = Arrangement.spacedBy(Cal.columnGap),
        ) {
            CalendarMonthGrid(now = now)
            CalendarDivider(Modifier.width(Cal.divider).fillMaxHeight().padding(vertical = Cal.dividerInset))
            CalendarEventColumn(
                shown = shown,
                zone = now.zone,
                today = now.toLocalDate(),
                notice = calendarNotice(read?.access).takeIf { shown.isEmpty() && read != null },
            )
        }
    }
}

private data class CalendarDot(val event: CalendarEvent, val color: Color)

internal fun calendarDotColor(calendarColor: Int, fallback: Color): Color =
    if (calendarColor == CALENDAR_COLOR_UNKNOWN) fallback else Color(calendarColor).copy(alpha = 1f)

private const val CALENDAR_COLOR_UNKNOWN = 0

@Composable
private fun CalendarMonthGrid(
    now: ZonedDateTime,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCueColors.current
    val locale = LocalConfiguration.current.locales[0]
    val today = now.toLocalDate()
    val month = YearMonth.from(today)
    val firstDayOfWeek = remember(locale) { calendarFirstDayOfWeek(locale) }
    val cells = remember(month, firstDayOfWeek) { calendarMonthGrid(month, firstDayOfWeek) }
    val weekdays = remember(firstDayOfWeek, locale) { calendarWeekdayLabels(firstDayOfWeek, locale) }
    Column(modifier = modifier.width(Cal.monthColumnWidth).fillMaxHeight().padding(top = Cal.monthColumnTop)) {
        Text(
            text = calendarMonthLabel(month, locale),
            modifier = Modifier.padding(horizontal = Cal.monthLabelInset),
            color = colors.accent,
            style = Cal.monthLabel.toTextStyle(),
            lineHeight = Cal.monthLabel.lineHeight,
            maxLines = Tokens.Type.LABEL_LINES,
            overflow = Tokens.Type.labelOverflow,
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = Cal.monthLabelGap)) {
            weekdays.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    color = colors.textSecondary,
                    style = Cal.weekday.toTextStyle(),
                    textAlign = TextAlign.Center,
                    maxLines = Tokens.Type.LABEL_LINES,
                )
            }
        }
        Column(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = Cal.weekdayGap)) {
            for (row in 0 until CALENDAR_GRID_ROWS) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    for (column in 0 until CALENDAR_GRID_COLUMNS) {
                        val day = cells[row * CALENDAR_GRID_COLUMNS + column]
                        CalendarDayCell(
                            day = day,
                            isToday = day == today.dayOfMonth,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(day: Int, isToday: Boolean, modifier: Modifier = Modifier) {
    if (day == CALENDAR_GRID_EMPTY) {
        Box(modifier = modifier)
        return
    }
    val colors = LocalCueColors.current
    val shadow = (if (colors.isDark) Cal.todayShadowDark else Cal.todayShadowLight)
        .takeIf { isToday && !colors.reducedTransparency }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(Cal.today)
                .then(if (shadow == null) Modifier else Modifier.cueShadow(CircleShape, shadow))
                .then(if (isToday) Modifier.background(colors.accent, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.toString(),
                color = if (isToday) colors.onAccent() else colors.textPrimary,
                style = (if (isToday) Cal.todayNumber else Cal.dayNumber).toTextStyle(),
            )
        }
    }
}

private val WIDGET_SHAPE = RoundedCornerShape(Tokens.Radius.widgetInner)

@Composable
private fun CalendarDivider(modifier: Modifier) {
    val colors = LocalCueColors.current
    val alpha = if (colors.isDark) Cal.DIVIDER_OPACITY_DARK else Cal.DIVIDER_OPACITY_LIGHT
    Box(modifier = modifier.background(colors.textPrimary.copy(alpha = alpha)))
}

@Composable
private fun RowScope.CalendarEventColumn(
    shown: List<CalendarDot>,
    zone: ZoneId,
    today: LocalDate,
    notice: CalendarNoticeText?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.weight(1f).fillMaxHeight(),
        verticalArrangement = Arrangement.Center,
    ) {
        shown.forEachIndexed { index, dot ->
            if (index > 0) CalendarDivider(Modifier.fillMaxWidth().height(Cal.divider))
            CalendarEventRow(
                event = dot.event,
                dot = dot.color,
                zone = zone,
                today = today,
                modifier = Modifier.weight(1f),
            )
        }
        notice?.let { CalendarNotice(it) }
    }
}

@Composable
private fun CalendarEventRow(
    event: CalendarEvent,
    dot: Color,
    zone: ZoneId,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCueColors.current
    val locale = LocalConfiguration.current.locales[0]
    val dayFormat = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dM"), locale)
    }
    val lines = remember(event, zone, today, dayFormat) { calendarEventLines(event, zone, today, dayFormat) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Cal.eventGap),
    ) {
        Box(modifier = Modifier.size(Cal.eventDot).background(dot, CircleShape))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title ?: stringResource(R.string.calendar_widget_untitled),
                color = colors.textPrimary,
                style = Cal.eventTitle.toTextStyle(),
                maxLines = Tokens.Type.LABEL_LINES,
                overflow = Tokens.Type.labelOverflow,
            )
            lines.detail?.let { detail ->
                Text(
                    text = detail,
                    modifier = Modifier.padding(top = Cal.lineGap),
                    color = colors.textSecondary,
                    style = Cal.eventDetail.toTextStyle(),
                    maxLines = Tokens.Type.LABEL_LINES,
                    overflow = Tokens.Type.labelOverflow,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = lines.start ?: stringResource(R.string.calendar_widget_all_day),
                color = if (lines.start == null) colors.textSecondary else colors.textPrimary,
                style = Cal.eventStart.toTextStyle(),
                maxLines = Tokens.Type.LABEL_LINES,
            )
            lines.end?.let { end ->
                Text(
                    text = end,
                    modifier = Modifier.padding(top = Cal.lineGap),
                    color = colors.textSecondary,
                    style = Cal.eventEnd.toTextStyle(),
                    maxLines = Tokens.Type.LABEL_LINES,
                )
            }
        }
    }
}

@Composable
private fun CalendarNotice(notice: CalendarNoticeText, modifier: Modifier = Modifier) {
    val colors = LocalCueColors.current
    Column(modifier = modifier.padding(start = Cal.noticeInset)) {
        Text(
            text = stringResource(notice.title),
            color = colors.textPrimary,
            style = Cal.noticeTitle.toTextStyle(),
            maxLines = Cal.NOTICE_LINES,
            overflow = Tokens.Type.labelOverflow,
        )
        notice.detail?.let { detail ->
            Spacer(modifier = Modifier.height(Cal.noticeGap))
            Text(
                text = stringResource(detail),
                color = colors.textSecondary,
                style = Cal.noticeDetail.toTextStyle(),
                maxLines = Cal.NOTICE_LINES,
                overflow = Tokens.Type.labelOverflow,
            )
        }
    }
}

private data class CalendarNoticeText(@StringRes val title: Int, @StringRes val detail: Int?)

private fun calendarNotice(access: CalendarAccess?): CalendarNoticeText = when (access) {
    CalendarAccess.GRANTED, null -> CalendarNoticeText(
        title = R.string.calendar_widget_empty,

        detail = R.string.calendar_widget_empty_detail,
    )

    CalendarAccess.DENIED -> CalendarNoticeText(
        title = R.string.calendar_widget_denied,
        detail = R.string.calendar_widget_denied_action,
    )
    CalendarAccess.UNAVAILABLE -> CalendarNoticeText(R.string.calendar_widget_unavailable, detail = null)
}
