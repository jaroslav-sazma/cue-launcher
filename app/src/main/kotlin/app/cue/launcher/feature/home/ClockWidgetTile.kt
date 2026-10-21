package app.cue.launcher.feature.home

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cue.launcher.R
import app.cue.launcher.data.clock.CLOCK_DEGREES_PER_HOUR
import app.cue.launcher.data.clock.CLOCK_DIAL_NUMERALS
import app.cue.launcher.data.clock.CLOCK_TICK_HOURS
import app.cue.launcher.data.clock.ClockAlarmDay
import app.cue.launcher.data.clock.clockAlarmDay
import app.cue.launcher.data.clock.clockDateParts
import app.cue.launcher.data.clock.clockHourAngle
import app.cue.launcher.data.clock.clockMinuteAngle
import app.cue.launcher.data.clock.clockShortWeekdayText
import app.cue.launcher.data.clock.clockTimeText
import app.cue.launcher.designsystem.GlassLevel
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.TileSurface
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.toTextStyle
import app.cue.launcher.system.rememberNextAlarmMillis
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private typealias Clock = Tokens.CustomWidget.Clock

// Where the numerals sit around the dial, clockwise from XII.
private val NUMERAL_DIRECTIONS = listOf(0f to -1f, 1f to 0f, 0f to 1f, -1f to 0f)

private const val ALARM_GLYPH = "alarm"

@Composable
internal fun ClockWidget(modifier: Modifier = Modifier) {
    val now = rememberMinuteTick()
    val alarm = rememberNextAlarm(now.zone)
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(Clock.halfGap),
    ) {
        ClockDialTile(now = now, modifier = Modifier.weight(1f).fillMaxHeight())
        ClockDateColumn(now = now, alarm = alarm, modifier = Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun rememberNextAlarm(zone: ZoneId): ZonedDateTime? {
    val millis = rememberNextAlarmMillis()
    return remember(millis, zone) {
        millis?.let { ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
    }
}

@Composable
private fun ClockDialTile(now: ZonedDateTime, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val spoken = clockTimeText(now.toLocalTime(), DateFormat.is24HourFormat(context), locale)
    TileSurface(
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
        level = GlassLevel.THICK,
        radius = Tokens.Radius.widgetInner,
        contentPadding = Clock.dialInset,
        shadow = null,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ClockDial(time = now.toLocalTime(), diameter = minOf(Clock.dial, maxWidth, maxHeight))
        }
    }
}

@Composable
private fun ClockDial(time: LocalTime, diameter: Dp, modifier: Modifier = Modifier) {
    val colors = LocalCueColors.current
    val scale = diameter / Clock.dial
    TileSurface(
        modifier = modifier.size(diameter),
        level = GlassLevel.THICK,
        contentPadding = 0.dp,
        shape = CircleShape,
        surface = colors.clockDial,
    ) {
        ClockNumerals(radius = diameter * Clock.NUMERAL_RADIUS_RATIO, scale = scale)
        ClockHands(time = time, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun BoxScope.ClockNumerals(radius: Dp, scale: Float) {
    val colors = LocalCueColors.current
    val style = Clock.numeral
        .copy(size = Clock.numeral.size * scale, lineHeight = Clock.numeral.lineHeight * scale)
        .toTextStyle()
    CLOCK_DIAL_NUMERALS.forEachIndexed { index, numeral ->
        val (dx, dy) = NUMERAL_DIRECTIONS[index]
        Text(
            text = numeral,
            modifier = Modifier.align(Alignment.Center).offset(x = radius * dx, y = radius * dy),
            color = colors.textPrimary,
            style = style,
        )
    }
}

@Composable
private fun ClockHands(time: LocalTime, modifier: Modifier = Modifier) {
    val colors = LocalCueColors.current
    val tick = colors.clockTick
    val ink = colors.textPrimary
    val accent = colors.accent
    Canvas(modifier = modifier) {
        val scale = size.minDimension / Clock.dial.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        val tickWidth = Clock.tickWidth.toPx() * scale
        CLOCK_TICK_HOURS.forEach { hour ->
            rotate(degrees = hour * CLOCK_DEGREES_PER_HOUR, pivot = center) {
                drawRoundRect(
                    color = tick,
                    topLeft = Offset(center.x - tickWidth / 2f, Clock.tickInset.toPx() * scale),
                    size = Size(tickWidth, Clock.tickLength.toPx() * scale),
                    cornerRadius = CornerRadius(tickWidth / 2f),
                )
            }
        }
        drawHand(clockHourAngle(time), Clock.hourHandLength, Clock.hourHandWidth, ink, scale)
        drawHand(clockMinuteAngle(time), Clock.minuteHandLength, Clock.minuteHandWidth, accent, scale)
        val capRadius = Clock.cap.toPx() * scale / 2f
        drawCircle(color = ink, radius = capRadius, center = center)
        drawCircle(color = accent, radius = capRadius - Clock.capEdge.toPx() * scale, center = center)
    }
}

// The hand reaches past the centre by half its width, so its rounded tail stays a half circle.
private fun DrawScope.drawHand(angle: Float, length: Dp, width: Dp, color: Color, scale: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val thickness = width.toPx() * scale
    val reach = length.toPx() * scale
    rotate(degrees = angle, pivot = center) {
        drawRoundRect(
            color = color,
            topLeft = Offset(center.x - thickness / 2f, center.y - reach),
            size = Size(thickness, reach + thickness / 2f),
            cornerRadius = CornerRadius(thickness / 2f),
        )
    }
}

@Composable
private fun ClockDateColumn(now: ZonedDateTime, alarm: ZonedDateTime?, modifier: Modifier = Modifier) {
    val colors = LocalCueColors.current
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(now.toLocalDate(), locale) { clockDateParts(now.toLocalDate(), locale) }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = date.weekday,
            color = colors.accent,
            style = Clock.weekday.toTextStyle(),
            textAlign = TextAlign.Center,
            maxLines = Tokens.Type.LABEL_LINES,
            overflow = Tokens.Type.labelOverflow,
        )
        Text(
            text = date.day,
            modifier = Modifier.padding(top = Clock.weekdayGap),
            color = colors.textPrimary,
            style = Clock.dayNumber.toTextStyle(),
            maxLines = Tokens.Type.LABEL_LINES,
        )
        Text(
            text = date.month,
            modifier = Modifier.padding(top = Clock.monthGap),
            color = colors.textSecondary,
            style = Clock.month.toTextStyle(),
            textAlign = TextAlign.Center,
            maxLines = Tokens.Type.LABEL_LINES,
            overflow = Tokens.Type.labelOverflow,
        )
        alarm?.let {
            ClockAlarmRow(alarm = it, now = now, modifier = Modifier.padding(top = Clock.alarmGap))
        }
    }
}

@Composable
private fun ClockAlarmRow(alarm: ZonedDateTime, now: ZonedDateTime, modifier: Modifier = Modifier) {
    val colors = LocalCueColors.current
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val time = clockTimeText(alarm.toLocalTime(), DateFormat.is24HourFormat(context), locale)
    val label = when (clockAlarmDay(now.toLocalDate(), alarm.toLocalDate())) {
        ClockAlarmDay.TODAY -> stringResource(R.string.clock_widget_alarm_today, time)
        ClockAlarmDay.TOMORROW -> stringResource(R.string.clock_widget_alarm_tomorrow, time)
        ClockAlarmDay.LATER ->
            stringResource(R.string.clock_widget_alarm_day, clockShortWeekdayText(alarm.toLocalDate(), locale), time)
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Clock.alarmIconGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(
            name = ALARM_GLYPH,
            size = Clock.alarmIcon,
            tint = colors.accent,
            contentDescription = stringResource(R.string.clock_widget_next_alarm),
        )
        Text(
            text = label,
            color = colors.textPrimary,
            style = Clock.alarm.toTextStyle(),
            maxLines = Tokens.Type.LABEL_LINES,
            overflow = Tokens.Type.labelOverflow,
        )
    }
}
