package app.cue.launcher.feature.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.designsystem.Tokens
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.ZonedDateTime

@Composable
fun LauncherWidgetContent(
    widget: LauncherWidgetType,
    modifier: Modifier = Modifier,
) {
    when (widget) {
        LauncherWidgetType.CALENDAR -> CalendarWidget(modifier)
        LauncherWidgetType.CLOCK -> ClockWidget(modifier)
        LauncherWidgetType.MEDIA -> MediaWidget(modifier)

        LauncherWidgetType.SEARCHBAR -> Unit
    }
}

// ACTION_TIME_TICK cannot be declared in a manifest receiver — it only ever reaches a receiver
// registered in code, and only while the process is alive, which is exactly what a minute-accurate
// clock needs: no drift after doze, and an immediate read on every ON_START so it never shows stale.
private fun minuteTicks(context: Context): Flow<ZonedDateTime> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(received: Context?, intent: Intent?) {
            trySend(ZonedDateTime.now())
        }
    }
    context.registerReceiver(receiver, MINUTE_TICK_CHANGES, Context.RECEIVER_NOT_EXPORTED)
    send(ZonedDateTime.now())
    awaitClose { context.unregisterReceiver(receiver) }
}

private val MINUTE_TICK_CHANGES = IntentFilter().apply {
    addAction(Intent.ACTION_TIME_TICK)
    addAction(Intent.ACTION_TIME_CHANGED)
    addAction(Intent.ACTION_TIMEZONE_CHANGED)
}

@Composable
internal fun rememberMinuteTick(): ZonedDateTime {
    val context = LocalContext.current
    val now by remember(context) { minuteTicks(context) }
        .collectAsStateWithLifecycle(initialValue = ZonedDateTime.now())
    return now
}

@Composable
internal fun LauncherWidgetCellContent(
    widget: LauncherWidgetType,
    blurRegionKey: String?,
    label: String,
    showLabel: Boolean,
    drag: Modifier,
    actions: StaticSlotActions,
) {
    when (widget) {
        // The cell centres icon and label together, so a widget shorter than the row keeps the
        // label's room to stay on the same band as the icons beside it.
        LauncherWidgetType.SEARCHBAR -> {
            SearchbarTile(
                onClick = actions.openSearch,
                modifier = Modifier.width(Tokens.Size.searchbarWidth).then(drag),
                blurRegionKey = blurRegionKey,
            )
            if (showLabel) StaticLabelReserve()
        }

        LauncherWidgetType.CALENDAR -> LauncherWidgetContent(
            widget = LauncherWidgetType.CALENDAR,
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClickLabel = label) { actions.openCalendar() }
                .then(drag),
        )

        LauncherWidgetType.CLOCK -> LauncherWidgetContent(
            widget = LauncherWidgetType.CLOCK,
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClickLabel = stringResource(R.string.launcher_widget_clock_open)) {
                    actions.openAlarms()
                }
                .then(drag),
        )

        LauncherWidgetType.MEDIA -> LauncherWidgetContent(
            widget = LauncherWidgetType.MEDIA,
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClickLabel = stringResource(R.string.launcher_widget_media_open)) {
                    actions.openMedia()
                }
                .then(drag),
        )
    }
}

@StringRes
fun launcherWidgetLabelRes(widget: LauncherWidgetType): Int = when (widget) {
    LauncherWidgetType.CALENDAR -> R.string.launcher_widget_calendar
    LauncherWidgetType.CLOCK -> R.string.launcher_widget_clock
    LauncherWidgetType.MEDIA -> R.string.launcher_widget_media
    LauncherWidgetType.SEARCHBAR -> R.string.launcher_widget_searchbar
}

@Composable
internal fun rememberForegroundTick(): Int {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return tick
}
