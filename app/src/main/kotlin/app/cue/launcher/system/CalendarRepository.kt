package app.cue.launcher.system

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import app.cue.launcher.data.calendar.CalendarAccess
import app.cue.launcher.data.calendar.CalendarEvent
import app.cue.launcher.data.calendar.CalendarRead
import app.cue.launcher.data.calendar.CalendarSource
import app.cue.launcher.engine.WallClock
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@SingleIn(AppScope::class)
@Inject
class CalendarRepository(
    private val application: Application,
    private val clock: WallClock,
) {

    // The last read per request, for what is shown only: a widget follows the calendar only while it
    // is on screen, so each return starts a new subscription, which opens with what was last seen
    // instead of nothing until the fresh read replaces it. A condition never gets it — a scenario
    // must not be judged on an event that may have been moved or deleted since.
    private val lastShown = ConcurrentHashMap<Pair<Long, Set<Long>?>, CalendarRead>()

    fun observeEvents(
        horizonMillis: Long = CALENDAR_HORIZON_MILLIS,
        calendarIds: Set<Long>? = null,
        replayLastShown: Boolean = false,
    ): Flow<CalendarRead> = flow {
        val request = horizonMillis to calendarIds
        if (!calendarGranted(application)) {
            lastShown.remove(request)
            emit(CalendarRead.DENIED)
            return@flow
        }
        if (replayLastShown) lastShown[request]?.let { emit(it) }
        emitAll(
            calendarChanges().map {
                readAhead(horizonMillis, calendarIds).also { read -> if (replayLastShown) lastShown[request] = read }
            },
        )
    }.flowOn(Dispatchers.IO)

    suspend fun readAhead(
        horizonMillis: Long = CALENDAR_HORIZON_MILLIS,
        calendarIds: Set<Long>? = null,
    ): CalendarRead {
        val now = clock.nowMillis()
        return read(fromMillis = now, toMillis = now + horizonMillis, calendarIds = calendarIds)
    }

    suspend fun read(
        fromMillis: Long,
        toMillis: Long,
        calendarIds: Set<Long>? = null,
    ): CalendarRead = withContext(Dispatchers.IO) { query(fromMillis, toMillis, calendarIds) }

    // null means the read failed (denied or a provider outage) — distinct from a genuine empty list.
    suspend fun calendars(): List<CalendarSource>? = withContext(Dispatchers.IO) {
        if (!calendarGranted(application)) return@withContext null
        val cursor = try {
            application.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                CALENDAR_PROJECTION,
                null,
                null,
                CALENDAR_ORDER_BY,
            )
        } catch (_: SecurityException) {
            null
        } ?: return@withContext null
        cursor.use { it.readCalendars() }
    }

    private fun query(fromMillis: Long, toMillis: Long, calendarIds: Set<Long>?): CalendarRead {
        val uri: Uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(fromMillis.toString())
            .appendPath(toMillis.toString())
            .build()
        val cursor = try {
            application.contentResolver.query(
                uri,
                PROJECTION,
                calendarSelection(calendarIds),
                null,
                ORDER_BY,
            )
        } catch (_: SecurityException) {

            return CalendarRead.DENIED
        }

        return cursor?.use { CalendarRead(CalendarAccess.GRANTED, it.readEvents()) }
            ?: CalendarRead(CalendarAccess.UNAVAILABLE)
    }

    private fun Cursor.readEvents(): List<CalendarEvent> = buildList {
        while (moveToNext()) {
            add(
                CalendarEvent(
                    eventId = getLong(COLUMN_EVENT_ID),
                    calendarId = getLong(COLUMN_CALENDAR_ID),
                    calendarColor = getInt(COLUMN_CALENDAR_COLOR),
                    title = text(COLUMN_TITLE),
                    location = text(COLUMN_LOCATION),
                    startMillis = getLong(COLUMN_BEGIN),
                    endMillis = getLong(COLUMN_END),
                    allDay = getInt(COLUMN_ALL_DAY) != 0,
                ),
            )
        }
    }

    private fun Cursor.readCalendars(): List<CalendarSource> = buildList {
        while (moveToNext()) {
            val name = text(CALENDAR_COLUMN_NAME) ?: text(CALENDAR_COLUMN_ACCOUNT) ?: continue
            add(
                CalendarSource(
                    id = getLong(CALENDAR_COLUMN_ID),
                    name = name,
                    account = text(CALENDAR_COLUMN_ACCOUNT)?.takeIf { it != name },
                    color = getInt(CALENDAR_COLUMN_COLOR),
                    visible = getInt(CALENDAR_COLUMN_VISIBLE) != 0,
                ),
            )
        }
    }

    private fun calendarChanges(): Flow<Unit> =
        application.contentResolver.contentChanges(CalendarContract.CONTENT_URI)

    private companion object {

        val PROJECTION = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_COLOR,
        )

        const val COLUMN_EVENT_ID = 0
        const val COLUMN_CALENDAR_ID = 1
        const val COLUMN_TITLE = 2
        const val COLUMN_LOCATION = 3
        const val COLUMN_BEGIN = 4
        const val COLUMN_END = 5
        const val COLUMN_ALL_DAY = 6
        const val COLUMN_CALENDAR_COLOR = 7

        val CALENDAR_PROJECTION = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.VISIBLE,
        )

        const val CALENDAR_COLUMN_ID = 0
        const val CALENDAR_COLUMN_NAME = 1
        const val CALENDAR_COLUMN_ACCOUNT = 2
        const val CALENDAR_COLUMN_COLOR = 3
        const val CALENDAR_COLUMN_VISIBLE = 4

        val CALENDAR_ORDER_BY =
            "${CalendarContract.Calendars.ACCOUNT_NAME} ASC, " +
                "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"

        val ORDER_BY = "${CalendarContract.Instances.BEGIN} ASC"
    }
}

// Without a stored choice the widget follows the calendar app: whatever is visible there. A stored
// choice wins over that flag, so a calendar hidden in the calendar app can still feed the widget.
fun calendarSelection(calendarIds: Set<Long>?): String = when (calendarIds) {
    null -> "${CalendarContract.Instances.VISIBLE} = 1 AND $CALENDAR_EVENT_SELECTION"
    else -> "${CalendarContract.Instances.CALENDAR_ID} IN (${calendarIdList(calendarIds)}) " +
        "AND $CALENDAR_EVENT_SELECTION"
}

private fun calendarIdList(calendarIds: Set<Long>): String =
    calendarIds.sorted().joinToString(separator = ",").ifEmpty { NO_CALENDAR_ID.toString() }

private const val NO_CALENDAR_ID = -1L

private val CALENDAR_EVENT_SELECTION = buildString {
    append("(${CalendarContract.Instances.STATUS} IS NULL")
    append(" OR ${CalendarContract.Instances.STATUS} != ${CalendarContract.Events.STATUS_CANCELED})")
    append(" AND (${CalendarContract.Instances.SELF_ATTENDEE_STATUS} IS NULL")
    append(
        " OR ${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != " +
            "${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED})",
    )
}

const val CALENDAR_HORIZON_MILLIS: Long = 30L * 24 * 60 * 60 * 1000

const val READ_CALENDAR_PERMISSION: String = Manifest.permission.READ_CALENDAR

fun calendarGranted(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
