package app.cue.launcher.data.calendar

data class CalendarRead(
    val access: CalendarAccess,
    val events: List<CalendarEvent> = emptyList(),
) {

    companion object {
        val DENIED = CalendarRead(CalendarAccess.DENIED)
    }
}

enum class CalendarAccess {
    GRANTED,
    DENIED,
    UNAVAILABLE,
}
