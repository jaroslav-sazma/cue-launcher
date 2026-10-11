package app.cue.launcher.data.calendar

data class CalendarSource(
    val id: Long,
    val name: String,
    val account: String?,
    val color: Int,

    val visible: Boolean,
)

fun selectedCalendarIds(sources: List<CalendarSource>, stored: Set<Long>?): Set<Long> =
    stored?.let { ids -> sources.map(CalendarSource::id).filterTo(mutableSetOf(), ids::contains) }
        ?: sources.filter(CalendarSource::visible).map(CalendarSource::id).toSet()

fun toggledCalendarIds(selected: Set<Long>, id: Long): Set<Long> =
    if (id in selected) selected - id else selected + id

// null means don't write: calendars is unknown (permission denied / provider outage) or there was
// nothing stored to prune. A non-null (possibly empty) result is the new choice to persist — an
// empty result means every stored calendar is gone and the caller should fall back to the default.
fun prunedCalendarSelection(calendars: List<CalendarSource>?, stored: Set<Long>?): Set<Long>? {
    if (calendars == null || stored.isNullOrEmpty()) return null
    val kept = selectedCalendarIds(calendars, stored)
    return kept.takeIf { it != stored }
}
