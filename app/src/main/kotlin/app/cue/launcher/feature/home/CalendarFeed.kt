package app.cue.launcher.feature.home

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import app.cue.launcher.data.calendar.CalendarAccess
import app.cue.launcher.data.calendar.CalendarRead
import app.cue.launcher.data.calendar.CalendarSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Stable
fun interface CalendarFeed {

    fun events(): Flow<CalendarRead>

    companion object {

        val NONE: CalendarFeed = CalendarFeed { flowOf(CalendarRead(CalendarAccess.UNAVAILABLE)) }
    }
}

val LocalCalendarFeed: ProvidableCompositionLocal<CalendarFeed> = compositionLocalOf { CalendarFeed.NONE }

@Stable
fun interface CalendarSources {

    // null means the read failed (denied or a provider outage) — distinct from a genuine empty list.
    suspend fun read(): List<CalendarSource>?

    companion object {

        val NONE: CalendarSources = CalendarSources { null }
    }
}

val LocalCalendarSources: ProvidableCompositionLocal<CalendarSources> =
    compositionLocalOf { CalendarSources.NONE }
